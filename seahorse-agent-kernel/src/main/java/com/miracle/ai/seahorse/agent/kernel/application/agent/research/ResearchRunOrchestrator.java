/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchTaskProfile;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTaskQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 研究任务编排器。
 *
 * <p>使用固定步骤序列（bounded orchestrator）编排研究任务，
 * 每个步骤通过注入的 ResearchStepHandler 执行，并通过事件协议通知前端。
 * 不引入通用工作流引擎，保持 KISS 原则。
 */
public class ResearchRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunOrchestrator.class);

    private final DurableTaskQueuePort taskQueue;
    private final AgentRunEventBufferPort eventBuffer;
    private final EnumMap<ResearchStepType, ResearchStepHandler> handlers;
    private final ConcurrentMap<String, ResearchStepContext> contexts = new ConcurrentHashMap<>();
    private final String workerId;

    public ResearchRunOrchestrator(DurableTaskQueuePort taskQueue,
                                   AgentRunEventBufferPort eventBuffer,
                                   List<ResearchStepHandler> stepHandlers) {
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        this.eventBuffer = Objects.requireNonNull(eventBuffer, "eventBuffer must not be null");
        this.handlers = new EnumMap<>(ResearchStepType.class);
        for (ResearchStepHandler handler : Objects.requireNonNullElseGet(stepHandlers, List::<ResearchStepHandler>of)) {
            handlers.put(handler.stepType(), handler);
        }
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String startResearch(String runId, ResearchTaskProfile profile, String query) {
        Objects.requireNonNull(runId, "runId must not be null");
        contexts.put(runId, new ResearchStepContext(runId, query));
        DurableTask task = new DurableTask(
                UUID.randomUUID().toString(),
                runId,
                ResearchStepType.PLAN.name(),
                0,
                Instant.now(),
                null,
                "{\"query\":\"" + escapeJson(query) + "\",\"profileId\":\"" + profile.profileId() + "\"}");
        taskQueue.enqueue(task);
        emitEvent(runId, StreamEventType.RUN_STARTED, Map.of("runId", runId, "title", "Research started"));
        return runId;
    }

    public boolean pollAndExecute() {
        return taskQueue.claimNext(workerId).map(this::executeTask).orElse(false);
    }

    private boolean executeTask(DurableTask task) {
        ResearchStepType stepType;
        try {
            stepType = ResearchStepType.valueOf(task.stepType());
        } catch (IllegalArgumentException e) {
            taskQueue.fail(task.taskId(), "Unknown step type: " + task.stepType());
            return false;
        }

        try {
            emitEvent(task.runId(), StreamEventType.STEP_STARTED,
                    Map.of("stepId", task.taskId(), "title", stepType.name(), "status", "RUNNING"));

            ResearchStepContext context = contexts.computeIfAbsent(task.runId(),
                    id -> new ResearchStepContext(id, ""));
            ResearchStepHandler handler = handlers.get(stepType);
            if (handler != null) {
                handler.execute(task, context);
            } else {
                log.debug("No handler registered for step {} (run={})", stepType, task.runId());
            }

            emitEvent(task.runId(), StreamEventType.STEP_FINISHED,
                    Map.of("stepId", task.taskId(), "title", stepType.name(), "status", "SUCCEEDED"));
            taskQueue.ack(task.taskId());

            enqueueNextStep(task.runId(), stepType);
            return true;
        } catch (RetryableResearchException e) {
            int nextAttempt = task.attemptCount() + 1;
            Instant retryAt = Instant.now().plusSeconds(30L * nextAttempt);
            taskQueue.retry(task.taskId(), retryAt, e.getMessage());
            log.info("Research step {} retry scheduled for run={}, attempt={}", stepType, task.runId(), nextAttempt);
            return true;
        } catch (Exception e) {
            taskQueue.fail(task.taskId(), e.getMessage());
            emitEvent(task.runId(), StreamEventType.RECOVERABLE_ERROR,
                    Map.of("stepId", task.taskId(), "title", stepType.name(),
                            "message", Objects.requireNonNullElse(e.getMessage(), "Unknown error")));
            log.warn("Research step {} failed for run={}", stepType, task.runId(), e);
            return true;
        }
    }

    private void enqueueNextStep(String runId, ResearchStepType current) {
        ResearchStepType next = current.next();
        if (next != null) {
            DurableTask nextTask = new DurableTask(
                    UUID.randomUUID().toString(),
                    runId,
                    next.name(),
                    0,
                    Instant.now(),
                    null,
                    null);
            taskQueue.enqueue(nextTask);
        } else {
            emitEvent(runId, StreamEventType.STEP_FINISHED,
                    Map.of("stepId", "run-complete-" + runId, "title", "Research completed", "status", "SUCCEEDED"));
            contexts.remove(runId);
        }
    }

    private void emitEvent(String runId, StreamEventType type, Object payload) {
        try {
            StreamEventEnvelope envelope = StreamEventEnvelope.of(
                    System.nanoTime(), type, runId, payload);
            eventBuffer.append(runId, envelope);
        } catch (Exception e) {
            log.debug("Failed to emit event for run={}", runId, e);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
