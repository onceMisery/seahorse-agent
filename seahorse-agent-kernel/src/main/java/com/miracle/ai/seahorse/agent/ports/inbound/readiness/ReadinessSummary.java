/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.readiness;

import java.util.List;

/**
 * 系统就绪状态汇总。
 *
 * @param mode    当前产品模式（demo / rag / enterprise）
 * @param overall 整体状态：healthy（全部通过）、degraded（部分降级）、blocked（关键阻断）
 * @param checks  检查项列表
 */
public record ReadinessSummary(
        String mode,
        OverallStatus overall,
        List<ReadinessCheck> checks
) {

    public enum OverallStatus { HEALTHY, DEGRADED, BLOCKED }

    public static ReadinessSummary of(String mode, List<ReadinessCheck> checks) {
        OverallStatus overall = computeOverall(checks);
        return new ReadinessSummary(mode, overall, checks);
    }

    private static OverallStatus computeOverall(List<ReadinessCheck> checks) {
        boolean hasError = false;
        boolean hasWarn = false;
        for (ReadinessCheck check : checks) {
            if (check.status() == ReadinessCheck.Status.FAILED) {
                if (check.severity() == ReadinessCheck.Severity.ERROR) {
                    hasError = true;
                } else if (check.severity() == ReadinessCheck.Severity.WARN) {
                    hasWarn = true;
                }
            }
        }
        if (hasError) return OverallStatus.BLOCKED;
        if (hasWarn) return OverallStatus.DEGRADED;
        return OverallStatus.HEALTHY;
    }
}
