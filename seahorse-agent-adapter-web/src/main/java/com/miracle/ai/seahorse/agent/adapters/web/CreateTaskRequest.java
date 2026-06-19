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

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;

import java.util.List;

/**
 * 创建任务请求 DTO。
 *
 * @param type            任务类型: "quick_chat" | "agent_run" | "document_qa" | "knowledge_qa"
 * @param question        用户问题/输入
 * @param conversationId  会话 ID（可选，为空时自动创建）
 * @param agentId         Agent ID（agent_run 类型必需）
 * @param title           任务标题（可选）
 * @param knowledgeBaseId 知识库 ID（knowledge_qa 可选）
 * @param attachmentIds   附件 ID 列表（document_qa 可选）
 * @param mode            执行模式（auto/manual，可选）
 */
public record CreateTaskRequest(
        String type,
        String question,
        String conversationId,
        String agentId,
        String title,
        String knowledgeBaseId,
        List<String> attachmentIds,
        String mode
) {
    public TaskType toTaskType() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Task type is required");
        }
        return switch (type.toLowerCase()) {
            case "quick_chat" -> TaskType.QUICK_CHAT;
            case "agent_run" -> TaskType.AGENT_RUN;
            case "document_qa" -> TaskType.DOCUMENT_QA;
            case "knowledge_qa" -> TaskType.KNOWLEDGE_QA;
            default -> throw new IllegalArgumentException("Unknown task type: " + type);
        };
    }
}
