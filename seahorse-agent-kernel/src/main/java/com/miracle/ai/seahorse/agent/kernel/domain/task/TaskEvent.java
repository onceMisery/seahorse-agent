/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.domain.task;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 任务运行过程事件 — 用于 Task Facade 的统一事件流。
 * <p>
 * 事件按 taskId 分组、按 seq 单调递增，供前端 Timeline 逐步渲染。
 * 类型遵循路线图规范（点号命名）：task.created / task.started / model.selected /
 * memory.recalled / retrieval.started / retrieval.completed / skill.selected /
 * tool.started / tool.completed / approval.required / artifact.created /
 * degraded / task.completed / task.failed。
 *
 * @param taskId  所属任务
 * @param seq     事件序号（单调递增，从 1 开始），用于断线重连续传
 * @param type    事件类型（点号命名）
 * @param message 人类可读的阶段描述（中文）
 * @param data    结构化附加数据（产物 ID、工具名、耗时、降级原因等），可为空
 * @param at      事件发生时间
 */
public record TaskEvent(
        String taskId,
        long seq,
        String type,
        String message,
        Map<String, Object> data,
        Instant at
) {
    public TaskEvent {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        at = at == null ? Instant.now() : at;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    // ---- 事件类型常量 ----
    public static final String CREATED = "task.created";
    public static final String STARTED = "task.started";
    public static final String MODEL_SELECTED = "model.selected";
    public static final String MEMORY_RECALLED = "memory.recalled";
    public static final String RETRIEVAL_STARTED = "retrieval.started";
    public static final String RETRIEVAL_COMPLETED = "retrieval.completed";
    public static final String SKILL_SELECTED = "skill.selected";
    public static final String TOOL_STARTED = "tool.started";
    public static final String TOOL_COMPLETED = "tool.completed";
    public static final String APPROVAL_REQUIRED = "approval.required";
    public static final String ARTIFACT_CREATED = "artifact.created";
    public static final String DEGRADED = "degraded";
    public static final String COMPLETED = "task.completed";
    public static final String FAILED = "task.failed";

    public boolean isTerminal() {
        return COMPLETED.equals(type) || FAILED.equals(type);
    }
}
