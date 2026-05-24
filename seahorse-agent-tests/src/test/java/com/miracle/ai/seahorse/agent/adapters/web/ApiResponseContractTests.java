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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice 6：ApiResponse / ApiResponses 形状契约（spec §11.3）。
 *
 * <p>验证：
 * <ul>
 *     <li>成功响应 JSON 只含 {@code code} 与 {@code data}，不含 {@code message: null}（NON_NULL 抑制）。</li>
 *     <li>显式 error 响应只含 {@code code} 与 {@code message}，不含 {@code data: null}。</li>
 *     <li>{@link ApiResponses#requireService} 在 provider 缺失 / 返回 null 时抛出统一异常。</li>
 * </ul>
 */
class ApiResponseContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void okResponseSerializesWithoutNullMessageField() throws Exception {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(Map.of("k", "v"));
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.has("code")).isTrue();
        assertThat(json.get("code").asText()).isEqualTo("0");
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").get("k").asText()).isEqualTo("v");
        assertThat(json.has("message")).isFalse();
    }

    @Test
    void errorResponseSerializesWithoutNullDataField() throws Exception {
        ApiResponse<Object> response = ApiResponse.error("boom");
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("code").asText()).isEqualTo("ERROR");
        assertThat(json.get("message").asText()).isEqualTo("boom");
        assertThat(json.has("data")).isFalse();
    }

    @Test
    void customErrorCodePreservesProvidedCode() throws Exception {
        ApiResponse<Object> response = ApiResponse.error("AUTH_FAILED", "invalid token");
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("code").asText()).isEqualTo("AUTH_FAILED");
        assertThat(json.get("message").asText()).isEqualTo("invalid token");
    }

    @Test
    void requireServiceThrowsWhenProviderIsNull() {
        assertThatThrownBy(() -> ApiResponses.requireService(null, port -> "ignored"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
    }

    @Test
    void requireServiceThrowsWhenProviderReturnsNull() {
        ObjectProvider<Object> emptyProvider = new SimpleObjectProvider<>(null);

        assertThatThrownBy(() -> ApiResponses.requireService(emptyProvider, port -> "ignored"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
    }

    @Test
    void requireServiceWrapsActionResultInApiResponseOk() {
        ObjectProvider<String> provider = new SimpleObjectProvider<>("payload");

        ApiResponse<String> response = ApiResponses.requireService(provider, value -> value + "+");

        assertThat(response.code()).isEqualTo(ApiResponse.SUCCESS_CODE);
        assertThat(response.message()).isNull();
        assertThat(response.data()).isEqualTo("payload+");
    }

    @Test
    void requireServiceOrErrorReturnsErrorResponseWhenProviderMissing() {
        ApiResponse<String> response = ApiResponses.requireServiceOrError(null, value -> "ignored");

        assertThat(response.code()).isEqualTo(ApiResponses.SERVICE_NOT_AVAILABLE_ERROR_CODE);
        assertThat(response.message()).isEqualTo(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        assertThat(response.data()).isNull();
    }

    @Test
    void requireServiceOrErrorReturnsErrorResponseWhenProviderEmpty() {
        ObjectProvider<Object> provider = new SimpleObjectProvider<>(null);

        ApiResponse<Object> response = ApiResponses.requireServiceOrError(provider, value -> "ignored");

        assertThat(response.code()).isEqualTo(ApiResponses.SERVICE_NOT_AVAILABLE_ERROR_CODE);
        assertThat(response.message()).isEqualTo(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        assertThat(response.data()).isNull();
    }

    @Test
    void requireServiceOrErrorWithVoidActionProducesEmptySuccess() throws Exception {
        ObjectProvider<String> provider = new SimpleObjectProvider<>("port");

        ApiResponse<Object> response = ApiResponses.requireServiceOrError(provider, value -> null);

        assertThat(response.code()).isEqualTo(ApiResponse.SUCCESS_CODE);
        assertThat(response.message()).isNull();
        assertThat(response.data()).isNull();

        JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.has("code")).isTrue();
        assertThat(json.get("code").asText()).isEqualTo("0");
        assertThat(json.has("message")).isFalse();
        assertThat(json.has("data")).isFalse();
    }

    private static final class SimpleObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private SimpleObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            if (value == null) {
                throw new IllegalStateException("no value");
            }
            return value;
        }

        @Override
        public T getObject() {
            if (value == null) {
                throw new IllegalStateException("no value");
            }
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }
    }
}
