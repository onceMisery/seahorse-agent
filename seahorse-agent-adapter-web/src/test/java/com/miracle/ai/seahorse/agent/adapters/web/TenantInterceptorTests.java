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

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TenantInterceptorTests {

    private final TenantInterceptor interceptor = new TenantInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Object handler = new Object();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldRestoreTenantAcrossAsyncRedispatchAndClearBothThreads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SaSession session = mock(SaSession.class);
        when(session.get("tenantId")).thenReturn("tenant-42");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);

            assertThat(interceptor.preHandle(request, response, handler)).isTrue();
            assertThat(TenantContext.require()).isEqualTo("tenant-42");
        }

        interceptor.afterConcurrentHandlingStarted(request, response, handler);
        assertThat(TenantContext.isSet()).isFalse();

        request.setDispatcherType(DispatcherType.ASYNC);
        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(TenantContext.require()).isEqualTo("tenant-42");

        interceptor.afterCompletion(request, response, handler, null);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void shouldRejectAuthenticatedSessionWithoutTenantAndClearStaleContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SaSession session = mock(SaSession.class);
        when(session.get("tenantId")).thenReturn(null);
        TenantContext.set("stale-tenant");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TenantInterceptor.TenantResolutionException.class)
                    .hasMessage("Authentication session is missing a valid tenant");
        }

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void shouldRejectSessionReadFailureWithoutLeakingTenant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        TenantContext.set("stale-tenant");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenThrow(new IllegalStateException("redis unavailable"));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TenantInterceptor.TenantResolutionException.class)
                    .hasMessage("Authentication session is missing a valid tenant")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void shouldRejectAsyncRedispatchWithoutCapturedTenant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(TenantInterceptor.TenantResolutionException.class);
        assertThat(TenantContext.isSet()).isFalse();
    }
}
