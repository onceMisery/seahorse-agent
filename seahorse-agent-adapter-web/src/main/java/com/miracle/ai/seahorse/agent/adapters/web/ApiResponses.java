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
    public static final String SERVICE_NOT_AVAILABLE_ERROR_CODE = "1";

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

    /**
     * 非抛错变体：port 不可用时直接返回 {@code {code:"1", message:"Service not available"}}，
     * 保留历史 controller 返回错误响应（而非抛异常）的语义。
     *
     * <p>action 可返回 {@code null}（针对 void 端点），由 {@link ApiResponse} 的 NON_NULL
     * 行为抑制 data 字段，最终输出 {@code {code:"0"}}。
     */
    public static <P, T> ApiResponse<T> requireServiceOrError(ObjectProvider<P> provider, Function<P, T> action) {
        P port = provider == null ? null : provider.getIfAvailable();
        if (port == null) {
            return ApiResponse.error(SERVICE_NOT_AVAILABLE_ERROR_CODE, SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return ApiResponse.ok(action.apply(port));
    }
}
