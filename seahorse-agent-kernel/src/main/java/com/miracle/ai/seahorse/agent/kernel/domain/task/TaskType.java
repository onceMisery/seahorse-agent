/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.domain.task;

/**
 * Seahorse 任务类型。
 */
public enum TaskType {

    /** 快速聊天 — 走 RAG/Chat 管道 */
    QUICK_CHAT,

    /** Agent 运行 — 走 Agent Loop */
    AGENT_RUN,

    /** 文档问答 — 上传附件 + 解析 + RAG 检索 + 引用 */
    DOCUMENT_QA,

    /** 知识库问答 — 基于已有知识库的 RAG 检索 */
    KNOWLEDGE_QA;

    /** 是否走对话/RAG 管道（前端订阅 chat SSE），而非独立 Agent Loop。 */
    public boolean isConversational() {
        return this == QUICK_CHAT || this == DOCUMENT_QA || this == KNOWLEDGE_QA;
    }
}
