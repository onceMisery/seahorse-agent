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

import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统就绪诊断控制器。
 * <p>
 * 提供系统健康检查 API，根据当前产品模式（demo/rag/enterprise）
 * 展示各项基础设施的可用状态和修复建议。
 */
@RestController
@RequestMapping("/api/readiness")
public class ReadinessController {

    private final ReadinessInboundPort readinessPort;
    private final AdvancedFeatureGate featureGate;

    public ReadinessController(ReadinessInboundPort readinessPort,
                               AdvancedFeatureGate featureGate) {
        this.readinessPort = readinessPort;
        this.featureGate = featureGate;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        ReadinessSummary summary = readinessPort.getSummary();
        return Map.of(
                "mode", summary.mode(),
                "overall", summary.overall().name().toLowerCase(),
                "overallLabel", overallLabel(summary.overall()),
                "checks", summary.checks().stream()
                        .map(this::toCheckMap)
                        .collect(Collectors.toList()),
                "passedCount", summary.checks().stream().filter(c -> c.status() == ReadinessCheck.Status.PASSED).count(),
                "failedCount", summary.checks().stream().filter(c -> c.status() == ReadinessCheck.Status.FAILED).count(),
                "totalCount", (long) summary.checks().size()
        );
    }

    @GetMapping("/checks")
    public Map<String, Object> checks() {
        ReadinessSummary summary = readinessPort.getSummary();
        return Map.of(
                "mode", summary.mode(),
                "overall", summary.overall().name().toLowerCase(),
                "checks", summary.checks().stream()
                        .map(this::toCheckMap)
                        .collect(Collectors.toList())
        );
    }

    @PostMapping("/checks/{checkId}/run")
    public Map<String, Object> runCheck(@PathVariable String checkId) {
        ReadinessCheck check = readinessPort.runCheck(checkId);
        if (check == null) {
            return Map.of("error", "Check not found: " + checkId);
        }
        return toCheckMap(check);
    }

    @GetMapping("/product-mode")
    public Map<String, Object> productMode() {
        ProductMode mode = featureGate.productMode();
        return Map.of(
                "mode", mode.name(),
                "label", modeLabel(mode),
                "description", modeDescription(mode)
        );
    }

    private Map<String, Object> toCheckMap(ReadinessCheck check) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", check.id());
        map.put("name", check.name());
        map.put("severity", check.severity().name().toLowerCase());
        map.put("status", check.status().name().toLowerCase());
        map.put("message", check.message());
        if (!check.impact().isEmpty()) map.put("impact", check.impact());
        if (!check.suggestion().isEmpty()) map.put("suggestion", check.suggestion());
        if (!check.docsUrl().isEmpty()) map.put("docsUrl", check.docsUrl());
        return map;
    }

    private String overallLabel(ReadinessSummary.OverallStatus status) {
        return switch (status) {
            case HEALTHY -> "系统就绪";
            case DEGRADED -> "部分能力降级";
            case BLOCKED -> "关键能力缺失";
        };
    }

    private String modeLabel(ProductMode mode) {
        return switch (mode) {
            case DEMO -> "演示模式";
            case RAG -> "RAG 模式";
            case ENTERPRISE -> "企业模式";
        };
    }

    private String modeDescription(ProductMode mode) {
        return switch (mode) {
            case DEMO -> "体验模式，基础聊天和示例任务可用，RAG 和企业治理能力为轻量版本";
            case RAG -> "知识库问答模式，RAG 检索和 Trace 可用，企业治理能力部分启用";
            case ENTERPRISE -> "企业级模式，全部能力可用，依赖缺失将阻断相关功能";
        };
    }
}
