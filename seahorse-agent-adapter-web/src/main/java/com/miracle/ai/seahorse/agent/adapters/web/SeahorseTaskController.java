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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Task Facade REST Controller。
 * <p>
 * 提供统一任务入口：创建任务、查询任务、取消任务、事件流（SSE）、产物聚合。
 */
@RestController
@RequestMapping("/tasks")
public class SeahorseTaskController {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseTaskController.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final ObjectProvider<TaskInboundPort> taskPortProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseTaskController(ObjectProvider<TaskInboundPort> taskPortProvider,
                                  CurrentUserPort currentUserPort) {
        this.taskPortProvider = taskPortProvider;
        this.currentUserPort = currentUserPort;
    }

    /**
     * 创建任务。
     */
    @PostMapping
    public ApiResponse<TaskResponse> createTask(@RequestBody CreateTaskRequest request) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            var currentUser = currentUserPort.requireCurrentUser();
            var command = new CreateTaskCommand(
                    request.toTaskType(),
                    String.valueOf(currentUser.userId()),
                    request.question(),
                    request.conversationId(),
                    request.agentId(),
                    request.title(),
                    request.knowledgeBaseId(),
                    request.attachmentIds(),
                    request.mode(),
                    currentUser
            );
            Task task = port.createTask(command);
            return TaskResponse.from(task);
        });
    }

    /**
     * 获取任务详情。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable String taskId) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            Task task = port.getTask(taskId);
            return TaskResponse.from(task);
        });
    }

    /**
     * 列出用户任务。
     */
    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            var currentUser = currentUserPort.requireCurrentUser();
            List<Task> tasks = port.listUserTasks(String.valueOf(currentUser.userId()), limit);
            return tasks.stream().map(TaskResponse::from).toList();
        });
    }

    /**
     * 取消任务。
     */
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<TaskResponse> cancelTask(@PathVariable String taskId) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            Task task = port.cancelTask(taskId);
            return TaskResponse.from(task);
        });
    }

    /**
     * 任务事件流（SSE）。订阅时先回放历史事件，再实时推送新事件。
     */
    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter events(@PathVariable String taskId) {
        TaskInboundPort port = taskPortProvider.getIfAvailable();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        if (port == null) {
            emitter.completeWithError(new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE));
            return emitter;
        }

        // 回放历史
        long lastSeq = 0;
        for (TaskEvent ev : port.listEvents(taskId)) {
            if (sendEvent(emitter, ev)) {
                lastSeq = ev.seq();
            }
        }

        // 订阅后续事件（去重已回放的 seq）
        final long replayedTo = lastSeq;
        AutoCloseable subscription = port.subscribeEvents(taskId, ev -> {
            if (ev.seq() > replayedTo) {
                if (!sendEvent(emitter, ev) || ev.isTerminal()) {
                    emitter.complete();
                }
            }
        });

        emitter.onCompletion(() -> closeQuietly(subscription));
        emitter.onTimeout(() -> {
            closeQuietly(subscription);
            emitter.complete();
        });
        emitter.onError(e -> closeQuietly(subscription));
        return emitter;
    }

    /**
     * 任务产物列表。
     */
    @GetMapping("/{taskId}/artifacts")
    public ApiResponse<List<TaskArtifactResponse>> artifacts(@PathVariable String taskId) {
        return ApiResponses.requireService(taskPortProvider, port ->
                port.listArtifacts(taskId).stream()
                        .map(a -> TaskArtifactResponse.from(taskId, a))
                        .toList());
    }

    private boolean sendEvent(SseEmitter emitter, TaskEvent ev) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(ev.seq()))
                    .name(ev.type())
                    .data(new TaskEventResponse(ev.seq(), ev.type(), ev.message(), ev.data(), ev.at())));
            return true;
        } catch (IOException | IllegalStateException e) {
            LOG.debug("SSE send failed for task event seq {}: {}", ev.seq(), e.toString());
            return false;
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best-effort unsubscribe
            }
        }
    }
}
