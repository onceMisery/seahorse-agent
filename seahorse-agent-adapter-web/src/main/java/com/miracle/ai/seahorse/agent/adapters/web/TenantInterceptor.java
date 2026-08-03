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
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Resolves the authenticated tenant once per request and owns the complete
 * {@link TenantContext} lifecycle, including asynchronous redispatch.
 * <p>
 * This interceptor is registered after authentication. An authenticated request
 * without a valid tenant is rejected instead of falling back to another tenant.
 */
public class TenantInterceptor implements AsyncHandlerInterceptor {

    private static final String SESSION_KEY_TENANT_ID = "tenantId";
    private static final String REQUEST_ATTRIBUTE_TENANT_ID = TenantInterceptor.class.getName() + ".tenantId";
    private static final String INVALID_SESSION_MESSAGE = "Authentication session is missing a valid tenant";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantContext.clear();
        TenantContext.set(resolveTenantId(request));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        TenantContext.clear();
    }

    private String resolveTenantId(HttpServletRequest request) {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return requireTenantId(request.getAttribute(REQUEST_ATTRIBUTE_TENANT_ID));
        }

        try {
            if (!StpUtil.isLogin()) {
                throw new TenantResolutionException(INVALID_SESSION_MESSAGE);
            }
            Object tenantId = StpUtil.getSession().get(SESSION_KEY_TENANT_ID);
            String resolvedTenantId = requireTenantId(tenantId);
            request.setAttribute(REQUEST_ATTRIBUTE_TENANT_ID, resolvedTenantId);
            return resolvedTenantId;
        } catch (TenantResolutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TenantResolutionException(INVALID_SESSION_MESSAGE, ex);
        }
    }

    private String requireTenantId(Object tenantId) {
        if (tenantId instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new TenantResolutionException(INVALID_SESSION_MESSAGE);
    }

    static final class TenantResolutionException extends RuntimeException {

        private TenantResolutionException(String message) {
            super(message);
        }

        TenantResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
