/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.outbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;

import java.util.List;
import java.util.Optional;

/**
 * 任务仓储端口 — 持久化任务记录。
 */
public interface TaskRepositoryPort {

    Task save(Task task);

    Optional<Task> findById(String taskId);

    List<Task> findByUserId(String userId, int limit);

    void updateStatus(String taskId, TaskStatus status);

    void updateRunId(String taskId, String runId);

    void updateConversationId(String taskId, String conversationId);

    /**
     * 根据 conversationId 查找处于 RUNNING 状态的任务。
     */
    Optional<Task> findRunningByConversationId(String conversationId);
}
