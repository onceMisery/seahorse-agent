/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.domain.task;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seahorse 用户任务实体。
 * <p>
 * 轻量 wrapper：封装 conversationId + 可选 runId，提供用户级别的任务历史与状态跟踪。
 * 不替代 AgentRun，只做编排层记录。
 */
public class Task {

    private final String taskId;
    private final TaskType type;
    private TaskStatus status;
    private final String userId;
    private String conversationId;
    private String runId;
    private final String agentId;
    private final String title;
    private final String question;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    private Task(String taskId,
                 TaskType type,
                 TaskStatus status,
                 String userId,
                 String conversationId,
                 String runId,
                 String agentId,
                 String title,
                 String question,
                 Instant createdAt,
                 Instant startedAt,
                 Instant finishedAt) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.conversationId = conversationId;
        this.runId = runId;
        this.agentId = agentId;
        this.title = title;
        this.question = question;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * 静态工厂 — 创建新任务 (status=PENDING)。
     */
    public static Task create(TaskType type,
                              String userId,
                              String conversationId,
                              String agentId,
                              String title,
                              String question) {
        return new Task(
                generateTaskId(),
                type,
                TaskStatus.PENDING,
                userId,
                conversationId,
                null,
                agentId,
                title,
                question,
                Instant.now(),
                null,
                null
        );
    }

    /**
     * 完整构造 — 用于仓储还原。
     */
    public static Task rehydrate(String taskId,
                                 TaskType type,
                                 TaskStatus status,
                                 String userId,
                                 String conversationId,
                                 String runId,
                                 String agentId,
                                 String title,
                                 String question,
                                 Instant createdAt,
                                 Instant startedAt,
                                 Instant finishedAt) {
        return new Task(taskId, type, status, userId, conversationId, runId,
                agentId, title, question, createdAt, startedAt, finishedAt);
    }

    // ---- 状态变更 ----

    public Task transitionTo(TaskStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.status + " to " + target);
        }
        Instant now = Instant.now();
        Instant newStartedAt = this.startedAt;
        Instant newFinishedAt = this.finishedAt;
        if (target == TaskStatus.RUNNING && newStartedAt == null) {
            newStartedAt = now;
        }
        if (target.isTerminal()) {
            newFinishedAt = now;
        }
        return new Task(this.taskId, this.type, target, this.userId,
                this.conversationId, this.runId, this.agentId, this.title,
                this.question, this.createdAt, newStartedAt, newFinishedAt);
    }

    public Task withRunId(String runId) {
        return new Task(this.taskId, this.type, this.status, this.userId,
                this.conversationId, runId, this.agentId, this.title,
                this.question, this.createdAt, this.startedAt, this.finishedAt);
    }

    public Task withConversationId(String conversationId) {
        return new Task(this.taskId, this.type, this.status, this.userId,
                conversationId, this.runId, this.agentId, this.title,
                this.question, this.createdAt, this.startedAt, this.finishedAt);
    }

    // ---- ID 生成 ----

    private static String generateTaskId() {
        long r = ThreadLocalRandom.current().nextLong(1_000_000_000L);
        return "task_" + String.format("%09d", r);
    }

    // ---- Getters ----

    public String getTaskId() { return taskId; }
    public TaskType getType() { return type; }
    public TaskStatus getStatus() { return status; }
    public String getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public String getRunId() { return runId; }
    public String getAgentId() { return agentId; }
    public String getTitle() { return title; }
    public String getQuestion() { return question; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
