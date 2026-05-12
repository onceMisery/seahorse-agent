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

package com.miracle.ai.seahorse.agent.ports.inbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;

/**
 * 问答入站端口。
 *
 * <p>L3 Web、RPC 或 CLI 入口只负责协议转换，真正的问答编排必须进入该端口再委派给 L1 Kernel。
 */
public interface ChatInboundPort {

    /**
     * 执行流式问答。
     *
     * @param command 流式问答命令
     * @param callback 流式输出回调
     */
    void streamChat(StreamChatCommand command, StreamCallback callback);

    /**
     * 取消指定流式任务。
     *
     * @param taskId 任务 ID
     */
    void stopTask(String taskId);
}
