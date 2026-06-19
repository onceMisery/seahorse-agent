/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 内存级任务事件总线。
 * <p>
 * 按 taskId 分桶维护事件历史 + 活跃监听器，线程安全。
 * 适用于单实例部署；多实例场景可后续替换为基于 MQ 的实现（端口不变）。
 * <p>
 * 历史缓冲有上界（每任务最多保留 {@value #MAX_HISTORY} 条），完成后保留一段时间供晚到订阅回放。
 */
public class InMemoryTaskEventBus implements TaskEventPort {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryTaskEventBus.class);
    private static final int MAX_HISTORY = 500;

    private static final class Channel {
        final AtomicLong seq = new AtomicLong(0);
        final List<TaskEvent> history = new CopyOnWriteArrayList<>();
        final List<Consumer<TaskEvent>> listeners = new CopyOnWriteArrayList<>();
        volatile boolean completed = false;
    }

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    @Override
    public TaskEvent publish(String taskId, String type, String message, Map<String, Object> data) {
        Channel ch = channels.computeIfAbsent(taskId, k -> new Channel());
        long seq = ch.seq.incrementAndGet();
        TaskEvent event = new TaskEvent(taskId, seq, type, message, data, Instant.now());

        ch.history.add(event);
        if (ch.history.size() > MAX_HISTORY) {
            ch.history.remove(0);
        }

        for (Consumer<TaskEvent> listener : ch.listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.debug("Task event listener failed for task {} seq {}: {}", taskId, seq, e.toString());
            }
        }
        return event;
    }

    @Override
    public List<TaskEvent> history(String taskId) {
        Channel ch = channels.get(taskId);
        return ch == null ? List.of() : new ArrayList<>(ch.history);
    }

    @Override
    public AutoCloseable subscribe(String taskId, Consumer<TaskEvent> listener) {
        Channel ch = channels.computeIfAbsent(taskId, k -> new Channel());
        ch.listeners.add(listener);
        return () -> ch.listeners.remove(listener);
    }

    @Override
    public void complete(String taskId) {
        Channel ch = channels.get(taskId);
        if (ch != null) {
            ch.completed = true;
        }
    }
}
