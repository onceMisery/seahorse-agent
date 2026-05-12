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

package com.miracle.ai.seahorse.agent.ports.outbound.stream;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;

import java.util.function.Supplier;

/**
 * 流式任务协调端口。
 * <p>
 * Kernel 只依赖任务注册、取消和句柄绑定语义；本地缓存、Redis 标记和跨节点通知由 L3 Adapter 承担。
 */
public interface StreamTaskPort {

    /**
     * 注册流式任务。
     *
     * @param taskId           任务 ID
     * @param sender           SSE 发送器
     * @param onCancelSupplier 取消完成载荷提供器
     */
    void register(String taskId, StreamEventSender sender, Supplier<StreamCompletionPayload> onCancelSupplier);

    /**
     * 绑定模型流式取消句柄。
     *
     * @param taskId 任务 ID
     * @param handle 可取消句柄
     */
    void bindHandle(String taskId, StreamCancellationHandle handle);

    /**
     * 判断任务是否已取消。
     *
     * @param taskId 任务 ID
     * @return true 表示已取消
     */
    boolean isCancelled(String taskId);

    /**
     * 取消任务。
     *
     * @param taskId 任务 ID
     */
    void cancel(String taskId);

    /**
     * 注销任务。
     *
     * @param taskId 任务 ID
     */
    void unregister(String taskId);

    static StreamTaskPort noop() {
        return new StreamTaskPort() {
            @Override
            public void register(String taskId,
                                 StreamEventSender sender,
                                 Supplier<StreamCompletionPayload> onCancelSupplier) {
            }

            @Override
            public void bindHandle(String taskId, StreamCancellationHandle handle) {
            }

            @Override
            public boolean isCancelled(String taskId) {
                return false;
            }

            @Override
            public void cancel(String taskId) {
            }

            @Override
            public void unregister(String taskId) {
            }
        };
    }
}
