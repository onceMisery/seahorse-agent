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

import com.miracle.ai.seahorse.agent.kernel.application.admin.RequireSuperAdmin;
import com.miracle.ai.seahorse.agent.kernel.exception.ForbiddenException;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

/**
 * AOP 切面：拦截 {@link RequireSuperAdmin} 注解，校验超级管理员权限。
 *
 * <p>判定逻辑（满足其一即可）：
 * <ol>
 *   <li>当前用户角色为 SUPER_ADMIN</li>
 *   <li>请求来源 IP 在白名单内（配置项 {@code seahorse.admin.allowed-ips}）</li>
 * </ol>
 */
@Aspect
public class SuperAdminAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuperAdminAspect.class);
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final CurrentUserPort currentUserPort;
    private final List<String> allowedIps;

    public SuperAdminAspect(CurrentUserPort currentUserPort, List<String> allowedIps) {
        this.currentUserPort = currentUserPort;
        this.allowedIps = allowedIps != null ? allowedIps : Collections.emptyList();
    }

    @Around("@annotation(requireSuperAdmin)")
    public Object checkSuperAdmin(ProceedingJoinPoint joinPoint,
                                  RequireSuperAdmin requireSuperAdmin) throws Throwable {
        CurrentUser user = currentUserPort.requireCurrentUser();

        // Check 1: role-based
        if (user.hasRole(SUPER_ADMIN_ROLE)) {
            return joinPoint.proceed();
        }

        // Check 2: IP whitelist
        if (!allowedIps.isEmpty()) {
            String clientIp = getClientIp();
            if (clientIp != null && allowedIps.contains(clientIp)) {
                return joinPoint.proceed();
            }
        }

        LOGGER.warn("超级管理员权限校验失败: userId={}, role={}, ip={}",
                user.userId(), user.role(), getClientIp());
        throw new ForbiddenException(
                "需要超级管理员权限",
                "admin", "super-admin");
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        // Respect X-Forwarded-For for reverse-proxy deployments
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
