/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;

/**
 * 创建任务命令。
 */
public record CreateTaskCommand(
        TaskType type,
        String userId,
        String question,
        String conversationId,
        String agentId,
        String title
) {
    public CreateTaskCommand {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (type == TaskType.AGENT_RUN && (agentId == null || agentId.isBlank())) {
            throw new IllegalArgumentException("agentId is required for AGENT_RUN tasks");
        }
    }
}
