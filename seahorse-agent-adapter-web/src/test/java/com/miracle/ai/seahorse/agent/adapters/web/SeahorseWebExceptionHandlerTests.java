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

import cn.dev33.satoken.exception.NotLoginException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseWebExceptionHandlerTests {

    @Test
    void shouldSanitizeNotLoginMessage() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");

        ResponseEntity<ErrorResponse> response = handler.notLogin(
                new NotLoginException("login", NotLoginException.INVALID_TOKEN, "token invalid: abc-raw-token"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("登录已过期，请重新登录");
        assertThat(response.getBody().path()).isEqualTo("/api/secure");
        assertThat(response.getBody().details()).isEmpty();
        assertThat(response.toString()).doesNotContain("abc-raw-token");
    }

    @Test
    void shouldReturnBadRequestForMissingRequestParameter() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/metadata-extraction/results");

        ResponseEntity<ErrorResponse> response = handler.missingRequestParameter(
                new MissingServletRequestParameterException("tenantId", "String"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("tenantId");
        assertThat(response.getBody().path()).isEqualTo("/metadata-extraction/results");
        assertThat(response.getBody().details()).containsEntry("parameter", "tenantId");
    }
}
