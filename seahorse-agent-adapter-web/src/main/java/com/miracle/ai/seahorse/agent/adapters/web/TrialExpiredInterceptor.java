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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.trial.KernelTrialService;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 试用期到期拦截器。
 *
 * <p>试用期到期后，仅允许 GET 请求（只读模式），
 * 对 POST/PUT/DELETE 请求返回 403 + 提示消息。
 */
public class TrialExpiredInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrialExpiredInterceptor.class);
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CurrentUserPort currentUserPort;
    private final KernelTrialService trialService;
    private final ObjectMapper objectMapper;

    public TrialExpiredInterceptor(CurrentUserPort currentUserPort,
                                   KernelTrialService trialService,
                                   ObjectMapper objectMapper) {
        this.currentUserPort = currentUserPort;
        this.trialService = trialService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Allow read-only requests
        if (READ_ONLY_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }

        Optional<CurrentUser> userOpt = currentUserPort.currentUser();
        if (userOpt.isEmpty()) {
            return true; // Not logged in, let other interceptors handle it
        }

        CurrentUser user = userOpt.get();
        String tenantId = user.effectiveTenantId();

        if (trialService.isTrialExpired(tenantId)) {
            LOGGER.info("试用期已到期，拒绝写操作: tenantId={}, method={}, uri={}",
                    tenantId, request.getMethod(), request.getRequestURI());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "TRIAL_EXPIRED");
            body.put("message", "试用期已到期，请升级套餐");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        return true;
    }
}
