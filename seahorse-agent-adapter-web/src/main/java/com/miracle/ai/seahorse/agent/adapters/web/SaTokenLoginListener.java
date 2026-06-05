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

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.miracle.ai.seahorse.agent.kernel.application.auth.UserAgentParser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.LoginHistoryPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sa-Token listener that captures login events and records them with IP/UA info.
 */
public class SaTokenLoginListener implements SaTokenListener {

    private static final Logger log = LoggerFactory.getLogger(SaTokenLoginListener.class);
    private static final String LOGIN_TYPE_TOKEN_REFRESH = "TOKEN_REFRESH";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final LoginHistoryPort loginHistoryPort;

    public SaTokenLoginListener(LoginHistoryPort loginHistoryPort) {
        this.loginHistoryPort = loginHistoryPort;
    }

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        if (loginHistoryPort == null) {
            return;
        }

        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return;
            }

            String ipAddress = extractIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String deviceInfo = UserAgentParser.parse(userAgent);

            long userId = parseUserId(loginId);
            String tenantId = loginParameter != null && loginParameter.getDevice() != null
                    ? loginParameter.getDevice() : "default";

            loginHistoryPort.recordLogin(userId, tenantId, LOGIN_TYPE_TOKEN_REFRESH,
                    ipAddress, userAgent, deviceInfo, STATUS_SUCCESS, null);
        } catch (Exception e) {
            log.debug("Failed to record token login event: {}", e.getMessage());
        }
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        // Optional: could record logout event
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        // Optional: could record kick-out event
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        // Optional: could record replaced event
    }

    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
        // Optional: could record disable event
    }

    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
        // Optional: could record untie-disable event
    }

    @Override
    public void doOpenSafe(String loginType, String deviceId, String service, long safeTime) {
        // Optional: could record open-safe event
    }

    @Override
    public void doCloseSafe(String loginType, String deviceId, String service) {
        // Optional: could record close-safe event
    }

    @Override
    public void doCreateSession(String id) {
        // Optional: could record session creation
    }

    @Override
    public void doLogoutSession(String id) {
        // Optional: could record session logout
    }

    @Override
    public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long timeout) {
        // Optional: could record timeout renewal
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            int commaIdx = xForwardedFor.indexOf(',');
            return commaIdx > 0 ? xForwardedFor.substring(0, commaIdx).trim() : xForwardedFor.trim();
        }
        return request.getRemoteAddr();
    }

    private long parseUserId(Object loginId) {
        if (loginId == null) {
            return 0L;
        }
        try {
            return Long.parseLong(loginId.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
