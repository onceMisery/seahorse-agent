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

package com.miracle.ai.seahorse.agent.adapters.web;

import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Function;

/**
 * Slice 6：controller 端口可用性辅助工具，集中 44 处 "Service not available" 样板。
 *
 * <p>原 controller 中重复模式：
 * <pre>{@code
 * Port port = provider.getIfAvailable();
 * if (port == null) {
 *     throw new IllegalStateException("Service not available");
 * }
 * return Map.of("code", "0", "data", port.doSomething());
 * }</pre>
 *
 * <p>替换为：
 * <pre>{@code
 * return ApiResponses.requireService(provider, port -> port.doSomething());
 * }</pre>
 *
 * <p>异常类型与消息严格保持 {@link IllegalStateException} + {@value #SERVICE_NOT_AVAILABLE_MESSAGE}，
 * 以便上游 {@code @ControllerAdvice} 错误映射不变（spec §11.3：不改 {@code {code,message,data}} 形状）。
 */
public final class ApiResponses {

    public static final String SERVICE_NOT_AVAILABLE_MESSAGE = "Service not available";

    private ApiResponses() {
    }

    public static <P, T> ApiResponse<T> requireService(ObjectProvider<P> provider, Function<P, T> action) {
        if (provider == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        P port = provider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return ApiResponse.ok(action.apply(port));
    }
}
