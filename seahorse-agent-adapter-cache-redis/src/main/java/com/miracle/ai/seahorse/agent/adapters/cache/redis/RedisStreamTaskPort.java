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

package com.miracle.ai.seahorse.agent.adapters.cache.redis;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Redis/PubSub 流式任务协调端口。
 */
public class RedisStreamTaskPort implements StreamTaskPort, AutoCloseable {

    private static final String KEY_PREFIX = "seahorse:agent:stream:";
    private static final String CANCEL_TOPIC = KEY_PREFIX + "cancel";
    private static final String CANCELLED_KEY_PREFIX = KEY_PREFIX + "cancelled:";
    private static final String DONE_PAYLOAD = "[DONE]";
    private static final StreamCompletionPayload EMPTY_COMPLETION_PAYLOAD = new StreamCompletionPayload(null, null);

    private final RedissonClient redissonClient;
    private final Duration cancelTtl;
    private final ConcurrentMap<String, StreamTaskInfo> localTasks = new ConcurrentHashMap<>();
    private final int listenerId;

    public RedisStreamTaskPort(RedissonClient redissonClient) {
        this(redissonClient, Duration.ofMinutes(30));
    }

    public RedisStreamTaskPort(RedissonClient redissonClient, Duration cancelTtl) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.cancelTtl = Objects.requireNonNullElse(cancelTtl, Duration.ofMinutes(30));
        this.listenerId = cancelTopic().addListener(String.class, (channel, taskId) -> cancelLocal(taskId));
    }

    @Override
    public void register(String taskId, StreamEventSender sender, Supplier<StreamCompletionPayload> onCancelSupplier) {
        if (isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        if (isCancelled(taskId)) {
            taskInfo.cancelled.set(true);
            notifySender(taskInfo);
        }
    }

    @Override
    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        if (isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.handle = handle;
        if (isCancelled(taskId) && handle != null) {
            handle.cancel();
        }
    }

    @Override
    public boolean isCancelled(String taskId) {
        if (isBlank(taskId)) {
            return false;
        }
        StreamTaskInfo local = localTasks.get(taskId);
        if (local != null && local.cancelled.get()) {
            return true;
        }
        return Boolean.parseBoolean(Objects.requireNonNullElse(cancelledBucket(taskId).get(), "false"));
    }

    @Override
    public void cancel(String taskId) {
        if (isBlank(taskId)) {
            return;
        }
        cancelledBucket(taskId).set("true", cancelTtl.toMillis(), TimeUnit.MILLISECONDS);
        cancelLocal(taskId);
        cancelTopic().publish(taskId);
    }

    @Override
    public void unregister(String taskId) {
        if (isBlank(taskId)) {
            return;
        }
        localTasks.remove(taskId);
        cancelledBucket(taskId).delete();
    }

    @Override
    public void close() {
        cancelTopic().removeListener(listenerId);
    }

    private void cancelLocal(String taskId) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }
        StreamCancellationHandle handle = taskInfo.handle;
        if (handle != null) {
            handle.cancel();
        }
        notifySender(taskInfo);
    }

    private void notifySender(StreamTaskInfo taskInfo) {
        StreamEventSender sender = taskInfo.sender;
        if (sender == null) {
            return;
        }
        sender.sendEvent(StreamEventType.CANCEL.value(), resolveCancelPayload(taskInfo.onCancelSupplier));
        sender.sendEvent(StreamEventType.DONE.value(), DONE_PAYLOAD);
        sender.complete();
    }

    private StreamCompletionPayload resolveCancelPayload(Supplier<StreamCompletionPayload> onCancelSupplier) {
        if (onCancelSupplier == null) {
            return EMPTY_COMPLETION_PAYLOAD;
        }
        return Objects.requireNonNullElse(onCancelSupplier.get(), EMPTY_COMPLETION_PAYLOAD);
    }

    private StreamTaskInfo getOrCreate(String taskId) {
        return localTasks.computeIfAbsent(taskId, ignored -> new StreamTaskInfo());
    }

    private RBucket<String> cancelledBucket(String taskId) {
        return redissonClient.getBucket(CANCELLED_KEY_PREFIX + taskId, StringCodec.INSTANCE);
    }

    private RTopic cancelTopic() {
        return redissonClient.getTopic(CANCEL_TOPIC, StringCodec.INSTANCE);
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
