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

package com.miracle.ai.seahorse.agent.kernel.domain.chat;

/**
 * 聊天模式：决定 KernelChatInboundService 把请求路由到哪条主链路。
 *
 * <ul>
 *     <li>{@link #RAG}：默认。走 KernelChatPipeline，行为与 Phase A 之前完全一致。</li>
 *     <li>{@link #AGENT}：走 KernelAgentLoop（ReAct 风格的 LLM-Driven 工具调用循环）。</li>
 * </ul>
 *
 * 兼容性：旧 StreamChatCommand 5 参构造缺省 chatMode 时归一化为 {@link #RAG}。
 */
public enum ChatMode {
    RAG,
    AGENT
}
