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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生 Web 异常响应适配器。
 */
@RestControllerAdvice(basePackages = "com.miracle.ai.seahorse.agent.adapters.web")
public class SeahorseWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseWebExceptionHandler.class);
    private static final String ERROR_CODE = "1";

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException ex) {
        return error(ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(IllegalStateException ex) {
        LOGGER.warn("Request rejected by application state: {}", ex.getMessage(), ex);
        return error(ex);
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> notLogin(NotLoginException ex) {
        return Map.of(
                "code", ERROR_CODE,
                "message", "登录已过期，请重新登录");
    }

    @ExceptionHandler(AdvancedFeatureDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> advancedFeatureDisabled(AdvancedFeatureDisabledException ex) {
        return error(ex);
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(SecurityException ex) {
        return error(ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex) {
        LOGGER.warn("Request rejected with status {}: {}", ex.getStatusCode(), ex.getMessage(), ex);
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(error(ex));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> internalError(Exception ex) {
        LOGGER.error("Unhandled web request failed", ex);
        return Map.of(
                "code", ERROR_CODE,
                "message", "Internal server error");
    }

    private Map<String, Object> error(Exception ex) {
        return Map.of(
                "code", ERROR_CODE,
                "message", Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()));
    }
}
