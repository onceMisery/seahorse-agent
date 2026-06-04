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

import cn.dev33.satoken.stp.StpUtil;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 多租户拦截器：在请求进入时从 Sa-Token Session 解析 tenantId 并写入 {@link TenantContext}，
 * 在请求结束时清理 ThreadLocal，防止线程池复用导致跨租户数据泄漏。
 * <p>
 * 注册位置：在 {@link SeahorseSecurityWebMvcConfiguration} 的 SaInterceptor 之后注册，
 * 确保只有已认证的请求才会设置租户上下文。
 */
public class TenantInterceptor implements HandlerInterceptor {

    private static final String SESSION_KEY_TENANT_ID = "tenantId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!StpUtil.isLogin()) {
            // 未登录的请求不设置租户上下文（公开接口走默认租户）
            return true;
        }
        String tenantId = resolveTenantId();
        TenantContext.set(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    /**
     * 从 Sa-Token Session 解析租户 ID。
     * 如果 Session 中没有 tenantId（兼容旧数据），返回 {@link TenantConstants#DEFAULT_TENANT_ID}。
     */
    private String resolveTenantId() {
        try {
            Object tenantId = StpUtil.getSession().get(SESSION_KEY_TENANT_ID);
            if (tenantId instanceof String str && !str.isBlank()) {
                return str;
            }
        } catch (Exception ignored) {
            // Session 获取异常时降级为默认租户
        }
        return TenantConstants.DEFAULT_TENANT_ID;
    }
}
