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

import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * 多维度限流过滤器。
 *
 * <p>覆盖维度：
 * <ul>
 *   <li>IP 级：每分钟最大请求数（防爬虫/DDoS）</li>
 *   <li>用户级：由 Controller 层通过 RateLimiterPort 单独处理</li>
 *   <li>模板级：高成本模板每日上限由 Controller 层处理</li>
 *   <li>上传级：单文件大小由 ConversationAttachmentParserService 校验</li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int IP_PERMITS_PER_MINUTE = 120;
    private static final Duration IP_WINDOW = Duration.ofMinutes(1);
    private static final int UPLOAD_PERMITS_PER_HOUR = 50;
    private static final Duration UPLOAD_WINDOW = Duration.ofHours(1);
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "{\"code\":\"1\",\"message\":\"rate limit exceeded\"}";

    private final RateLimiterPort rateLimiterPort;

    public RateLimitFilter(RateLimiterPort rateLimiterPort) {
        this.rateLimiterPort = Objects.requireNonNullElse(rateLimiterPort, RateLimiterPort.noop());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);

        // IP 级限流
        RateLimitDecision ipDecision = rateLimiterPort.tryAcquire("ip", clientIp, IP_PERMITS_PER_MINUTE, IP_WINDOW);
        if (!ipDecision.allowed()) {
            rejectWithRateLimit(response);
            return;
        }

        // 上传接口额外限流
        if (isUploadRequest(request)) {
            RateLimitDecision uploadDecision = rateLimiterPort.tryAcquire(
                    "upload", clientIp, UPLOAD_PERMITS_PER_HOUR, UPLOAD_WINDOW);
            if (!uploadDecision.allowed()) {
                rejectWithRateLimit(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/health");
    }

    private boolean isUploadRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().contains("/attachments");
    }

    private void rejectWithRateLimit(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(RATE_LIMIT_EXCEEDED_MESSAGE);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }
}
