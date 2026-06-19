/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;

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

    /**
     * 返回任务已发生的全部事件（按 seq 升序），用于 SSE 订阅时回放与晚到订阅。
     */
    List<TaskEvent> listEvents(String taskId);

    /**
     * 注册任务事件监听器，返回取消订阅句柄。
     */
    AutoCloseable subscribeEvents(String taskId, java.util.function.Consumer<TaskEvent> listener);

    /**
     * 聚合任务产物。对于关联了 AgentRun 的任务，返回该 run 的全部 artifact；否则返回空列表。
     */
    List<AgentArtifact> listArtifacts(String taskId);

    /**
     * 将指定任务标记为已完成（SUCCEEDED）。对于对话类任务（quick_chat / knowledge_qa / document_qa），
     * 当 SSE 流结束时由回调方调用此方法闭合任务生命周期。
     *
     * @param taskId 任务 ID
     * @return 更新后的任务；如果任务已是终态或不存在则返回原任务或 null
     */
    Task completeTask(String taskId);

    /**
     * 根据 conversationId 查找处于 RUNNING 状态的任务。
     * 用于 SSE 对话流结束时反查对应的 Task Facade 任务。
     */
    Task findRunningTaskByConversation(String conversationId);
}
