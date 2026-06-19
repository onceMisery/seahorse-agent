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
import com.miracle.ai.seahorse.agent.kernel.domain.billing.QuotaExceededException;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.DatabaseTimeoutException;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.ExternalServiceException;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.SeahorseBusinessException;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Order(1)
@RestControllerAdvice(basePackages = "com.miracle.ai.seahorse.agent.adapters.web")
public class SeahorseWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseWebExceptionHandler.class);

    @ExceptionHandler(SeahorseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(SeahorseBusinessException ex,
                                                                 HttpServletRequest request) {
        LOGGER.warn("Business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(response(ex.getErrorCode(), ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> quotaExceeded(QuotaExceededException ex, HttpServletRequest request) {
        LOGGER.warn("Quota exceeded: reason={}, hint={}", ex.getReasonCode(), ex.getUpgradeHint());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(response(ex.getErrorCode(), ex.getMessage(), request,
                        Map.of("reasonCode", ex.getReasonCode(), "upgradeHint", ex.getUpgradeHint())));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> externalServiceError(ExternalServiceException ex,
                                                              HttpServletRequest request) {
        LOGGER.error("External service failure [{}]: {}", ex.getServiceName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(response(ex.getErrorCode(), ex.getMessage(), request,
                        Map.of("serviceName", ex.getServiceName())));
    }

    @ExceptionHandler(DatabaseTimeoutException.class)
    public ResponseEntity<ErrorResponse> databaseTimeout(DatabaseTimeoutException ex,
                                                         HttpServletRequest request) {
        LOGGER.error("Database timeout: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(response("DB_TIMEOUT", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validationError(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> details = Map.of(
                "errors", ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                        .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(response("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingRequestParameter(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "parameter", ex.getParameterName(),
                "parameterType", ex.getParameterType());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(response("VALIDATION_ERROR",
                        "Required request parameter '" + ex.getParameterName() + "' is missing",
                        request,
                        details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(response("INVALID_ARGUMENT", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response("RESOURCE_NOT_FOUND", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException ex, HttpServletRequest request) {
        LOGGER.warn("Request rejected by application state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(response("CONFLICT", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ErrorResponse> notLogin(NotLoginException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(response("UNAUTHORIZED", "登录已过期，请重新登录", request, Map.of()));
    }

    @ExceptionHandler(AdvancedFeatureDisabledException.class)
    public ResponseEntity<ErrorResponse> advancedFeatureDisabled(AdvancedFeatureDisabledException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(response("ADVANCED_FEATURE_DISABLED", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> forbidden(SecurityException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(response("FORBIDDEN", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex,
                                                        HttpServletRequest request) {
        LOGGER.warn("Request rejected with status {}: {}", ex.getStatusCode(), ex.getMessage(), ex);
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(response("HTTP_" + ex.getStatusCode().value(), reason, request, Map.of()));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientDisconnected(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        LOGGER.debug("Client disconnected before async response completed: {}", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internalError(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled web request failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response("INTERNAL_ERROR", "Internal server error", request, Map.of()));
    }

    private ErrorResponse response(String code,
                                   String message,
                                   HttpServletRequest request,
                                   Map<String, Object> details) {
        return ErrorResponse.of(
                code,
                message == null || message.isBlank() ? code : message,
                request == null ? null : request.getRequestURI(),
                request == null ? null : request.getHeader("X-Request-Id"),
                TenantContext.get(),
                details == null ? Map.of() : new LinkedHashMap<>(details));
    }
}
