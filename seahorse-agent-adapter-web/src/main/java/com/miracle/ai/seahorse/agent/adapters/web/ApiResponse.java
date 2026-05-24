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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Slice 6：controller 响应统一容器。
 *
 * <p>历史 controller 各自构造 {@code Map.of("code", "0", "data", data)}，导致 44 处重复样板。
 * 本 record 用于替换样板，保留 {@code {code, message, data}} 形状，且通过
 * {@link JsonInclude.Include#NON_NULL} 确保成功响应不出现 {@code "message": null}。
 *
 * @param code    应答码；成功为 {@value #SUCCESS_CODE}
 * @param message 错误信息；成功时建议 {@code null}（被 NON_NULL 抑制）
 * @param data    业务数据；可为 {@code null}（同样被 NON_NULL 抑制）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "0";
    public static final String ERROR_CODE = "ERROR";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, null, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ERROR_CODE, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
