/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.ports.outbound.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * 任务事件发布/订阅端口。
 * <p>
 * 编排服务通过 {@link #publish} 发布生命周期事件；
 * Web 适配器通过 {@link #subscribe} 注册 SSE 监听器，并用 {@link #history}
 * 在订阅时回放已发生的事件（支持断线重连续传与晚到订阅）。
 * <p>
 * 实现需为内存级、按 taskId 分桶、线程安全，并自动分配单调递增的 seq。
 */
public interface TaskEventPort {

    /**
     * 发布一个事件。实现负责为该 taskId 分配下一个 seq（调用方传入的 seq 会被忽略/覆盖）。
     *
     * @return 实际写入的事件（含分配后的 seq）
     */
    TaskEvent publish(String taskId, String type, String message, java.util.Map<String, Object> data);

    /**
     * 返回该任务已发生的全部事件（按 seq 升序）。
     */
    List<TaskEvent> history(String taskId);

    /**
     * 注册一个监听器，后续 publish 的事件会推送给它。
     *
     * @return 取消订阅的句柄；调用 close() 解除注册
     */
    AutoCloseable subscribe(String taskId, Consumer<TaskEvent> listener);

    /**
     * 标记任务事件流结束（通常在 task.completed / task.failed 后），允许实现回收资源。
     */
    void complete(String taskId);
}
