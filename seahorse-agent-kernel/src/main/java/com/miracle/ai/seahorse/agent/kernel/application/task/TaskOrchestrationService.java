/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskEventPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Task 编排服务。
 * <p>
 * 实现 TaskInboundPort，负责：
 * 1. 创建会话（若未提供 conversationId）
 * 2. 创建 Task 记录并发布 task.created 事件
 * 3. 根据类型分发：
 *    - 对话类（QUICK_CHAT/DOCUMENT_QA/KNOWLEDGE_QA）：发 task.started，前端订阅 chat SSE 完成对话
 *    - AGENT_RUN：异步启动 AgentRun，轮询其终态并回写 Task 状态，发布产物/完成/失败事件
 */
public class TaskOrchestrationService implements TaskInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(TaskOrchestrationService.class);

    /** AgentRun 终态轮询间隔与上限（默认最长约 10 分钟）。 */
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final int MAX_POLLS = 300;

    private final TaskRepositoryPort taskRepository;
    private final ConversationManagementInboundPort conversationPort;
    private final ChatInboundPort chatPort;
    private final AgentRunInboundPort agentRunPort;
    private final AgentArtifactQueryInboundPort artifactQueryPort;
    private final TaskEventPort eventPort;

    public TaskOrchestrationService(TaskRepositoryPort taskRepository,
                                    ConversationManagementInboundPort conversationPort,
                                    ChatInboundPort chatPort,
                                    AgentRunInboundPort agentRunPort,
                                    AgentArtifactQueryInboundPort artifactQueryPort,
                                    TaskEventPort eventPort) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.conversationPort = Objects.requireNonNull(conversationPort, "conversationPort");
        this.chatPort = chatPort;
        this.agentRunPort = agentRunPort;
        this.artifactQueryPort = artifactQueryPort;
        this.eventPort = Objects.requireNonNull(eventPort, "eventPort");
    }

    @Override
    public Task createTask(CreateTaskCommand command) {
        Objects.requireNonNull(command, "command");

        // 1. 若 conversationId 为空 → 创建会话
        String conversationId = command.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationPort.create(command.userId());
            LOG.info("Created conversation {} for user {}", conversationId, command.userId());
        }

        // 2. 创建 Task 实体 (status=PENDING)
        Task task = Task.create(
                command.type(),
                command.userId(),
                conversationId,
                command.agentId(),
                command.title(),
                command.question()
        );
        task = taskRepository.save(task);
        LOG.info("Created task {} (type={}, status={})", task.getTaskId(), task.getType(), task.getStatus());

        eventPort.publish(task.getTaskId(), TaskEvent.CREATED, "任务已创建",
                Map.of("type", task.getType().name().toLowerCase(), "status", "pending"));

        // 3. 根据 type 分发
        if (task.getType().isConversational()) {
            // 对话类：会话已就绪，前端订阅 chat SSE 完成对话
            taskRepository.updateStatus(task.getTaskId(), TaskStatus.RUNNING);
            task = task.transitionTo(TaskStatus.RUNNING);
            eventPort.publish(task.getTaskId(), TaskEvent.STARTED, "会话已就绪，开始对话",
                    Map.of("conversationId", conversationId, "status", "running"));
            LOG.info("Task {} is {}, frontend will initiate chat stream", task.getTaskId(), task.getType());
        } else if (task.getType() == TaskType.AGENT_RUN) {
            startAgentRunAsync(task, command);
        }

        return task;
    }

    @Override
    public Task getTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    @Override
    public List<Task> listUserTasks(String userId, int limit) {
        Objects.requireNonNull(userId, "userId");
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        return taskRepository.findByUserId(userId, safeLimit);
    }

    @Override
    public Task cancelTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        Task task = getTask(taskId);

        if (task.getStatus().isTerminal()) {
            LOG.warn("Task {} is already terminal (status={})", taskId, task.getStatus());
            return task;
        }

        if (task.getType().isConversational() && chatPort != null) {
            chatPort.stopTask(taskId);
            LOG.info("Cancelled chat for task {}", taskId);
        } else if (task.getType() == TaskType.AGENT_RUN && task.getRunId() != null && agentRunPort != null) {
            agentRunPort.cancel(task.getRunId());
            LOG.info("Cancelled agent run {} for task {}", task.getRunId(), taskId);
        }

        taskRepository.updateStatus(taskId, TaskStatus.CANCELLED);
        Task cancelled = task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RUNNING
                ? task.transitionTo(TaskStatus.CANCELLED)
                : task;
        eventPort.publish(taskId, TaskEvent.FAILED, "任务已取消", Map.of("reason", "cancelled"));
        eventPort.complete(taskId);
        return cancelled;
    }

    @Override
    public List<TaskEvent> listEvents(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return eventPort.history(taskId);
    }

    @Override
    public AutoCloseable subscribeEvents(String taskId, Consumer<TaskEvent> listener) {
        Objects.requireNonNull(taskId, "taskId");
        return eventPort.subscribe(taskId, listener);
    }

    @Override
    public List<AgentArtifact> listArtifacts(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        Task task = getTask(taskId);
        if (task.getRunId() == null || artifactQueryPort == null) {
            return List.of();
        }
        try {
            return artifactQueryPort.listByRunId(task.getRunId());
        } catch (Exception e) {
            LOG.warn("Failed to list artifacts for task {} (run {}): {}", taskId, task.getRunId(), e.toString());
            return List.of();
        }
    }

    private void startAgentRunAsync(Task task, CreateTaskCommand command) {
        if (agentRunPort == null) {
            LOG.warn("AgentRunInboundPort not available, cannot start agent run for task {}", task.getTaskId());
            taskRepository.updateStatus(task.getTaskId(), TaskStatus.FAILED);
            eventPort.publish(task.getTaskId(), TaskEvent.FAILED, "Agent 运行能力不可用",
                    Map.of("reason", "agent_run_port_unavailable"));
            eventPort.complete(task.getTaskId());
            return;
        }

        CompletableFuture.runAsync(() -> {
            String taskId = task.getTaskId();
            try {
                taskRepository.updateStatus(taskId, TaskStatus.RUNNING);
                eventPort.publish(taskId, TaskEvent.STARTED, "Agent 运行已启动", Map.of());

                AgentRunStartCommand runCommand = new AgentRunStartCommand(
                        command.agentId(),
                        null,
                        "default",
                        task.getConversationId(),
                        AgentRunTriggerType.API,
                        command.question(),
                        null
                );

                AgentRun run = agentRunPort.startRun(runCommand);
                String runId = run.runId();
                taskRepository.updateRunId(taskId, runId);
                eventPort.publish(taskId, TaskEvent.MODEL_SELECTED, "Agent 已分配运行 ID",
                        Map.of("runId", runId, "agentId", command.agentId()));
                LOG.info("Started agent run {} for task {}", runId, taskId);

                // 轮询 AgentRun 终态并回写 Task 状态
                pollAgentRunToTerminal(taskId, runId);
            } catch (Exception e) {
                LOG.error("Failed to start agent run for task {}", taskId, e);
                taskRepository.updateStatus(taskId, TaskStatus.FAILED);
                eventPort.publish(taskId, TaskEvent.FAILED, "Agent 启动失败: " + e.getMessage(),
                        Map.of("error", String.valueOf(e.getMessage())));
                eventPort.complete(taskId);
            }
        });
    }

    /**
     * 轮询 AgentRun 状态直至终态，再把结果映射回 Task 并发布产物/完成事件。
     */
    private void pollAgentRunToTerminal(String taskId, String runId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            Optional<AgentRun> maybeRun = agentRunPort.findRunById(runId);
            if (maybeRun.isEmpty()) {
                continue;
            }
            AgentRunStatus status = maybeRun.get().status();
            if (status == AgentRunStatus.WAITING_APPROVAL) {
                eventPort.publish(taskId, TaskEvent.APPROVAL_REQUIRED, "等待人工审批", Map.of("runId", runId));
                continue;
            }
            if (!status.isFinished()) {
                continue;
            }

            // 终态：发布产物事件，回写 Task 状态
            publishArtifactEvents(taskId, runId);
            if (status == AgentRunStatus.SUCCEEDED) {
                taskRepository.updateStatus(taskId, TaskStatus.SUCCEEDED);
                eventPort.publish(taskId, TaskEvent.COMPLETED, "任务已完成", Map.of("runId", runId));
            } else {
                String reason = maybeRun.get().errorMessage();
                taskRepository.updateStatus(taskId,
                        status == AgentRunStatus.CANCELLED ? TaskStatus.CANCELLED : TaskStatus.FAILED);
                eventPort.publish(taskId, TaskEvent.FAILED,
                        "任务" + (status == AgentRunStatus.CANCELLED ? "已取消" : "失败")
                                + (reason != null ? ": " + reason : ""),
                        Map.of("runId", runId, "status", status.name().toLowerCase()));
            }
            eventPort.complete(taskId);
            return;
        }
        // 超时未终态
        LOG.warn("Agent run {} for task {} did not reach terminal state within poll budget", runId, taskId);
        eventPort.publish(taskId, TaskEvent.DEGRADED, "运行时间较长，请稍后在任务列表查看结果",
                Map.of("runId", runId));
    }

    private void publishArtifactEvents(String taskId, String runId) {
        if (artifactQueryPort == null) {
            return;
        }
        try {
            List<AgentArtifact> artifacts = artifactQueryPort.listByRunId(runId);
            for (AgentArtifact a : artifacts) {
                eventPort.publish(taskId, TaskEvent.ARTIFACT_CREATED, "生成产物: " + a.title(),
                        Map.of("artifactId", a.artifactId(),
                                "type", a.artifactType().name().toLowerCase(),
                                "title", a.title()));
            }
        } catch (Exception e) {
            LOG.debug("Failed to publish artifact events for run {}: {}", runId, e.toString());
        }
    }
}
