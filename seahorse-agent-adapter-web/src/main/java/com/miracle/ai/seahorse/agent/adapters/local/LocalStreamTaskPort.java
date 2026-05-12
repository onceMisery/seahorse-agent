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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 单节点本地流任务适配器。
 */
public class LocalStreamTaskPort implements StreamTaskPort {

    private static final StreamCompletionPayload EMPTY_COMPLETION_PAYLOAD = new StreamCompletionPayload(null, null);
    private static final String DONE_PAYLOAD = "[DONE]";

    private final ConcurrentMap<String, StreamTaskInfo> tasks = new ConcurrentHashMap<>();

    @Override
    public void register(String taskId,
                         StreamEventSender sender,
                         Supplier<StreamCompletionPayload> onCancelSupplier) {
        if (isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        if (taskInfo.cancelled.get() && sender != null) {
            sendCancelAndDone(sender, resolveCancelPayload(onCancelSupplier));
            sender.complete();
        }
    }

    @Override
    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        if (isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.handle = handle;
        if (taskInfo.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    @Override
    public boolean isCancelled(String taskId) {
        if (isBlank(taskId)) {
            return false;
        }
        StreamTaskInfo taskInfo = tasks.get(taskId);
        return taskInfo != null && taskInfo.cancelled.get();
    }

    @Override
    public void cancel(String taskId) {
        if (isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }
        cancelHandle(taskInfo);
        notifySender(taskInfo);
    }

    @Override
    public void unregister(String taskId) {
        if (isBlank(taskId)) {
            return;
        }
        tasks.remove(taskId);
    }

    private StreamTaskInfo getOrCreate(String taskId) {
        return tasks.computeIfAbsent(taskId, ignored -> new StreamTaskInfo());
    }

    private void cancelHandle(StreamTaskInfo taskInfo) {
        StreamCancellationHandle handle = taskInfo.handle;
        if (handle != null) {
            handle.cancel();
        }
    }

    private void notifySender(StreamTaskInfo taskInfo) {
        StreamEventSender sender = taskInfo.sender;
        if (sender == null) {
            return;
        }
        sendCancelAndDone(sender, resolveCancelPayload(taskInfo.onCancelSupplier));
        sender.complete();
    }

    private StreamCompletionPayload resolveCancelPayload(Supplier<StreamCompletionPayload> onCancelSupplier) {
        if (onCancelSupplier == null) {
            return EMPTY_COMPLETION_PAYLOAD;
        }
        return Objects.requireNonNullElse(onCancelSupplier.get(), EMPTY_COMPLETION_PAYLOAD);
    }

    private void sendCancelAndDone(StreamEventSender sender, StreamCompletionPayload payload) {
        sender.sendEvent(StreamEventType.CANCEL.value(), payload);
        sender.sendEvent(StreamEventType.DONE.value(), DONE_PAYLOAD);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class StreamTaskInfo {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile StreamCancellationHandle handle;
        private volatile StreamEventSender sender;
        private volatile Supplier<StreamCompletionPayload> onCancelSupplier;
    }
}
