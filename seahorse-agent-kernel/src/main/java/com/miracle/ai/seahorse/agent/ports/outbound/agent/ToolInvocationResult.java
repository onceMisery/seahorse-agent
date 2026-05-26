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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

/**
 * 工具调用结果。content 总是字符串（JSON 序列化由 Tool 实现负责），
 * 便于直接以 OpenAI 兼容 "tool" role 消息回填给 LLM。
 */
public record ToolInvocationResult(boolean success, String content, String error, String approvalId) {

    public ToolInvocationResult(boolean success, String content, String error) {
        this(success, content, error, null);
    }

    public static ToolInvocationResult ok(String content) {
        return new ToolInvocationResult(true, content, null, null);
    }

    public static ToolInvocationResult failed(String error) {
        return failed(error, null);
    }

    public static ToolInvocationResult failed(String error, String approvalId) {
        return new ToolInvocationResult(false, null, error, approvalId);
    }
}
