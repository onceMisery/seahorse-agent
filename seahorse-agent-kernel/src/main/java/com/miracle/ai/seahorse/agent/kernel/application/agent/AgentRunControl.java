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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class AgentRunControl {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<StreamCancellationHandle> modelHandle = new AtomicReference<>();
    private final AtomicReference<Future<?>> workerFuture = new AtomicReference<>();
    private final Set<ExecutorService> toolExecutors = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static AgentRunControl direct() {
        return new AgentRunControl();
    }

    void bindWorkerFuture(Future<?> future) {
        workerFuture.set(future);
        if (cancelled.get() && future != null) {
            future.cancel(true);
        }
    }

    void bindModelHandle(StreamCancellationHandle handle) {
        modelHandle.set(handle);
        if (cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    void clearModelHandle(StreamCancellationHandle handle) {
        modelHandle.compareAndSet(handle, null);
    }

    void bindToolExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        toolExecutors.add(executor);
        if (cancelled.get()) {
            executor.shutdownNow();
        }
    }

    void clearToolExecutor(ExecutorService executor) {
        if (executor != null) {
            toolExecutors.remove(executor);
        }
    }

    boolean cancelled() {
        return cancelled.get();
    }

    void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        StreamCancellationHandle handle = modelHandle.get();
        if (handle != null) {
            handle.cancel();
        }
        toolExecutors.forEach(ExecutorService::shutdownNow);
        Future<?> future = workerFuture.get();
        if (future != null) {
            future.cancel(true);
        }
    }

    void checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new AgentLoopCancelledException("Agent loop cancelled");
        }
    }
}
