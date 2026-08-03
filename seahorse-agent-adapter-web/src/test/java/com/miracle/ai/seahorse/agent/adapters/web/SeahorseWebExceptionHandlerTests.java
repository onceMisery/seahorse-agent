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
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.DatabaseTimeoutException;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
    void shouldReturnStableUnauthorizedResponseForInvalidTenantSession() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");

        ResponseEntity<ErrorResponse> response = handler.invalidTenantSession(
                new TenantInterceptor.TenantResolutionException(
                        "Authentication session is missing a valid tenant",
                        new IllegalStateException("redis unavailable")),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_SESSION_INVALID");
        assertThat(response.getBody().message()).isEqualTo("Authentication session is missing a valid tenant");
        assertThat(response.toString()).doesNotContain("redis unavailable");
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

    @Test
    void shouldRedactCredentialTextFromClientVisibleErrorMessages() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/tools/token=sk-live-secret");

        ResponseEntity<ErrorResponse> badRequest = handler.badRequest(
                new IllegalArgumentException("invalid Authorization: Bearer abcdefghijklmnop"),
                request);
        ResponseEntity<ErrorResponse> conflict = handler.conflict(
                new IllegalStateException("state failed secret_key: plain-secret"),
                request);
        ResponseEntity<ErrorResponse> external = handler.externalServiceError(
                new ExternalServiceException("openapi", "upstream Cookie: sid=plain-cookie"),
                request);
        ResponseEntity<ErrorResponse> responseStatus = handler.responseStatus(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "proxy token=sk-live-secret"),
                request);

        assertThat(badRequest.getBody()).isNotNull();
        assertThat(conflict.getBody()).isNotNull();
        assertThat(external.getBody()).isNotNull();
        assertThat(responseStatus.getBody()).isNotNull();
        assertThat(badRequest.getBody().message()).doesNotContain("abcdefghijklmnop").contains("[REDACTED]");
        assertThat(badRequest.getBody().path()).doesNotContain("sk-live-secret").contains("[REDACTED]");
        assertThat(conflict.getBody().message()).doesNotContain("plain-secret").contains("[REDACTED]");
        assertThat(external.getBody().message()).doesNotContain("plain-cookie").contains("[REDACTED]");
        assertThat(responseStatus.getBody().message()).doesNotContain("sk-live-secret").contains("[REDACTED]");
    }

    @Test
    void shouldMarkExternalServiceAndDatabaseTimeoutsRetryable() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");

        ResponseEntity<ErrorResponse> external = handler.externalServiceError(
                new ExternalServiceException("vector", "vector search is unavailable"),
                request);
        ResponseEntity<ErrorResponse> dbTimeout = handler.databaseTimeout(
                new DatabaseTimeoutException("db timeout"),
                request);

        assertThat(external.getBody()).isNotNull();
        assertThat(external.getBody().retryable()).isTrue();
        assertThat(dbTimeout.getBody()).isNotNull();
        assertThat(dbTimeout.getBody().retryable()).isTrue();
    }

    @Test
    void shouldMarkPermanentFailuresNotRetryable() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");

        ResponseEntity<ErrorResponse> notLogin = handler.notLogin(
                new NotLoginException("login", NotLoginException.INVALID_TOKEN, "bad token"),
                request);
        ResponseEntity<ErrorResponse> advancedDisabled = handler.advancedFeatureDisabled(
                new AdvancedFeatureDisabledException(AdvancedFeature.SANDBOX, ProductMode.DEMO),
                request);

        assertThat(notLogin.getBody()).isNotNull();
        assertThat(notLogin.getBody().retryable()).isFalse();
        assertThat(advancedDisabled.getBody()).isNotNull();
        assertThat(advancedDisabled.getBody().retryable()).isFalse();
    }
}
