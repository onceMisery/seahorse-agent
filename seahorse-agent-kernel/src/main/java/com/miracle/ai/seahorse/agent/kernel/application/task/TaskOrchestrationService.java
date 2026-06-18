/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Task 编排服务。
 * <p>
 * 实现 TaskInboundPort，负责：
 * 1. 创建会话（若未提供 conversationId）
 * 2. 创建 Task 记录
 * 3. 根据类型分发：QUICK_CHAT 直接返回，AGENT_RUN 异步启动
 */
public class TaskOrchestrationService implements TaskInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(TaskOrchestrationService.class);

    private final TaskRepositoryPort taskRepository;
    private final ConversationManagementInboundPort conversationPort;
    private final ChatInboundPort chatPort;
    private final AgentRunInboundPort agentRunPort;

    public TaskOrchestrationService(TaskRepositoryPort taskRepository,
                                    ConversationManagementInboundPort conversationPort,
                                    ChatInboundPort chatPort,
                                    AgentRunInboundPort agentRunPort) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.conversationPort = Objects.requireNonNull(conversationPort, "conversationPort");
        this.chatPort = chatPort;
        this.agentRunPort = agentRunPort;
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

        // 3. 根据 type 分发
        if (task.getType() == TaskType.QUICK_CHAT) {
            // QUICK_CHAT: 不在此处启动流，返回 Task（前端直接调 chat SSE）
            LOG.info("Task {} is QUICK_CHAT, frontend will initiate chat stream", task.getTaskId());
        } else if (task.getType() == TaskType.AGENT_RUN) {
            // AGENT_RUN: 异步启动 AgentRun
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

        // 调用 ChatInboundPort 或 AgentRunInboundPort 取消
        if (task.getType() == TaskType.QUICK_CHAT && chatPort != null) {
            chatPort.stopTask(taskId);
            LOG.info("Cancelled chat for task {}", taskId);
        } else if (task.getType() == TaskType.AGENT_RUN && task.getRunId() != null && agentRunPort != null) {
            agentRunPort.cancel(task.getRunId());
            LOG.info("Cancelled agent run {} for task {}", task.getRunId(), taskId);
        }

        // 更新 Task 状态
        Task cancelled = task.transitionTo(TaskStatus.CANCELLED);
        taskRepository.updateStatus(taskId, TaskStatus.CANCELLED);
        return cancelled;
    }

    private void startAgentRunAsync(Task task, CreateTaskCommand command) {
        if (agentRunPort == null) {
            LOG.warn("AgentRunInboundPort not available, cannot start agent run for task {}", task.getTaskId());
            taskRepository.updateStatus(task.getTaskId(), TaskStatus.FAILED);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // 更新状态为 RUNNING
                taskRepository.updateStatus(task.getTaskId(), TaskStatus.RUNNING);

                // 构造 AgentRunStartCommand
                AgentRunStartCommand runCommand = new AgentRunStartCommand(
                        command.agentId(),
                        null, // versionId: use latest
                        "default", // tenantId: default for now
                        task.getConversationId(),
                        AgentRunTriggerType.API,
                        command.question(),
                        null // traceId
                );

                // 启动 AgentRun
                AgentRun run = agentRunPort.startRun(runCommand);
                String runId = run.runId();

                // 更新 Task 的 runId
                taskRepository.updateRunId(task.getTaskId(), runId);
                LOG.info("Started agent run {} for task {}", runId, task.getTaskId());

                // 注意：AgentRun 的状态由 AgentLoop 管理，Task 状态需要通过轮询或回调同步
                // MVP 阶段：前端通过 runId 查询 run 状态
            } catch (Exception e) {
                LOG.error("Failed to start agent run for task {}", task.getTaskId(), e);
                taskRepository.updateStatus(task.getTaskId(), TaskStatus.FAILED);
            }
        });
    }
}
