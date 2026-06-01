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
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class SeahorseSecurityWebMvcConfiguration implements WebMvcConfigurer {

    private static final String ROLE_ADMIN = "admin";

    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "/",
            "/index.html",
            "/login",
            "/error",
            "/features",
            "/api/features",
            "/prototype/ai-infra");
    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/auth/",
            "/assets/",
            "/prototype/");

    /**
     * 仅管理员可访问的后端管理类接口前缀。普通用户即便绕过前端直接调用也会被拒绝。
     * 注意：聊天、会话、个人记忆（/api/me）、用户自助（/user/me、/user/password）等
     * 普通用户必需的接口不在此列。
     */
    private static final Set<String> ADMIN_PATH_PREFIXES = Set.of(
            "/admin/",
            "/users",
            "/intent-tree",
            "/ingestion/",
            "/mappings",
            "/metadata-quarantine/",
            "/metadata-review/",
            "/rag/traces",
            "/sample-questions",
            "/agents");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null && shouldSkip(attrs.getRequest())) {
                        return;
                    }
                    StpUtil.checkLogin();
                    if (attrs != null && isAdminPath(attrs.getRequest().getRequestURI())) {
                        StpUtil.checkRole(ROLE_ADMIN);
                    }
                }) {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                            throws Exception {
                        try {
                            return super.preHandle(request, response, handler);
                        } catch (NotLoginException e) {
                            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已过期，请重新登录");
                            return false;
                        } catch (NotRoleException e) {
                            writeError(response, HttpServletResponse.SC_FORBIDDEN, "无管理员权限");
                            return false;
                        }
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/login",
                        "/features",
                        "/api/features",
                        "/auth/**",
                        "/error",
                        "/assets/**",
                        "/prototype/**");
    }

    private void writeError(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write("{\"code\":\"1\",\"message\":\"" + message + "\"}");
        } catch (IOException ignored) {
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || isPublicPath(request.getRequestURI());
    }

    static boolean isAdminPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String path = uri.split("\\?", 2)[0];
        return ADMIN_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    static boolean isPublicPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String path = uri.split("\\?", 2)[0];
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
