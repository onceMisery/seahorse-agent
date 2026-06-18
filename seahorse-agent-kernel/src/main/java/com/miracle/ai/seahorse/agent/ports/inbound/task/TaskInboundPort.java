/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;

import java.util.List;

/**
 * 任务驱动端口 — Workspace 用户任务的统一入口。
 */
public interface TaskInboundPort {

    /**
     * 创建任务。对于 QUICK_CHAT 只创建记录和会话；对于 AGENT_RUN 还会异步启动 AgentRun。
     */
    Task createTask(CreateTaskCommand command);

    /**
     * 根据 taskId 查询任务详情。
     */
    Task getTask(String taskId);

    /**
     * 列出用户最近的任务。
     */
    List<Task> listUserTasks(String userId, int limit);

    /**
     * 取消任务。
     */
    Task cancelTask(String taskId);
}
