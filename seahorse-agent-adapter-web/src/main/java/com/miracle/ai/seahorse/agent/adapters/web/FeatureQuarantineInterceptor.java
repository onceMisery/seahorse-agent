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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 非核心能力隔离拦截器（设计 §11 / ADR-001）。
 *
 * <p>按路径前缀把未接入 AdvancedFeatureGate 的非核心控制器统一纳入运行时
 * 开关：功能未启用时统一抛出 {@link AdvancedFeatureDisabledException}，
 * 由全局异常处理器渲染为稳定的 403 ADVANCED_FEATURE_DISABLED 契约，
 * 与逐控制器 requireEnabled 的行为完全一致。路径匹配按段对齐，/api/billing
 * 不会误伤 /api/billing-xyz。
 */
public final class FeatureQuarantineInterceptor implements HandlerInterceptor {

    /**
     * 路径前缀 → 功能开关。前缀按段匹配；同一前缀只允许映射一个功能。
     */
    private static final Map<String, AdvancedFeature> QUARANTINED_PREFIXES = buildPrefixes();

    private final AdvancedFeatureGate gate;

    public FeatureQuarantineInterceptor(AdvancedFeatureGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        AdvancedFeature feature = matchFeature(uri);
        if (feature != null) {
            gate.requireEnabled(feature);
        }
        return true;
    }

    private static AdvancedFeature matchFeature(String uri) {
        if (uri == null) {
            return null;
        }
        for (Map.Entry<String, AdvancedFeature> entry : QUARANTINED_PREFIXES.entrySet()) {
            if (matchesSegment(uri, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean matchesSegment(String uri, String prefix) {
        return uri.equals(prefix) || uri.startsWith(prefix + "/") || uri.startsWith(prefix + "?");
    }

    private static Map<String, AdvancedFeature> buildPrefixes() {
        Map<String, AdvancedFeature> prefixes = new LinkedHashMap<>();
        prefixes.put("/api/admin/users", AdvancedFeature.ADMIN);
        prefixes.put("/api/admin/tenants", AdvancedFeature.ADMIN);
        prefixes.put("/api/admin/audit-logs", AdvancedFeature.AUDIT_LOG);
        prefixes.put("/api/admin/marketplace", AdvancedFeature.MARKETPLACE);
        prefixes.put("/api/marketplace", AdvancedFeature.MARKETPLACE);
        prefixes.put("/marketplace", AdvancedFeature.MARKETPLACE);
        prefixes.put("/api/billing", AdvancedFeature.BILLING);
        prefixes.put("/api/cost-usage", AdvancedFeature.COST_ANALYTICS);
        prefixes.put("/api/run-experiments", AdvancedFeature.RUN_EXPERIMENT);
        prefixes.put("/run-experiments", AdvancedFeature.RUN_EXPERIMENT);
        prefixes.put("/api/audit-events", AdvancedFeature.AUDIT_LOG);
        prefixes.put("/api/notifications", AdvancedFeature.NOTIFICATION);
        prefixes.put("/notifications", AdvancedFeature.NOTIFICATION);
        prefixes.put("/agent/plugins", AdvancedFeature.PLUGIN);
        return prefixes;
    }
}
