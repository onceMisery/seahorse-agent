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
    AGENT_RUN
}
