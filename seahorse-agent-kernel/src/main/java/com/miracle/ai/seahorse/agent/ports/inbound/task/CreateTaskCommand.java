/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;

import java.util.List;

/**
 * 创建任务命令。
 *
 * @param type            任务类型
 * @param userId          用户 ID
 * @param question        用户问题/输入
 * @param conversationId  会话 ID（可选，为空时自动创建）
 * @param agentId         Agent ID（AGENT_RUN 必需）
 * @param title           任务标题（可选）
 * @param knowledgeBaseId 知识库 ID（KNOWLEDGE_QA 场景使用，可选）
 * @param attachmentIds   附件 ID 列表（DOCUMENT_QA 场景使用，可选）
 * @param mode            执行模式提示（auto/manual 等，可选）
 */
public record CreateTaskCommand(
        TaskType type,
        String userId,
        String question,
        String conversationId,
        String agentId,
        String title,
        String knowledgeBaseId,
        List<String> attachmentIds,
        String mode,
        CurrentUser currentUser
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
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }

    public CreateTaskCommand(TaskType type,
                             String userId,
                             String question,
                             String conversationId,
                             String agentId,
                             String title,
                             String knowledgeBaseId,
                             List<String> attachmentIds,
                             String mode) {
        this(type, userId, question, conversationId, agentId, title, knowledgeBaseId, attachmentIds, mode, null);
    }

    /** 兼容旧的 6 参构造（无 KB/附件/mode）。 */
    public CreateTaskCommand(TaskType type, String userId, String question,
                             String conversationId, String agentId, String title) {
        this(type, userId, question, conversationId, agentId, title, null, null, null, null);
    }
}
