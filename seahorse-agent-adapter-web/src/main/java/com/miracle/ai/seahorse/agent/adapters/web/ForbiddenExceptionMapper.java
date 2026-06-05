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

import com.miracle.ai.seahorse.agent.kernel.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps {@link ForbiddenException} to HTTP 403 JSON responses.
 */
@RestControllerAdvice(basePackages = "com.miracle.ai.seahorse.agent.adapters.web")
public class ForbiddenExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForbiddenExceptionMapper.class);

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbidden(ForbiddenException ex) {
        LOGGER.warn("Forbidden access: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FORBIDDEN");
        body.put("message", ex.getMessage());
        if (ex.resourceType() != null) {
            body.put("resourceType", ex.resourceType());
        }
        if (ex.resourceId() != null) {
            body.put("resourceId", ex.resourceId());
        }
        return body;
    }
}
