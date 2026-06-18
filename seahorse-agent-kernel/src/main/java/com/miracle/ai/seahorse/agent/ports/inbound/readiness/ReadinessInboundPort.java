/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.readiness;

/**
 * 系统就绪检查入站端口。
 * <p>
 * 提供系统健康诊断能力，根据当前产品模式（demo/rag/enterprise）
 * 判断各项基础设施能力的可用状态，并给出修复建议。
 */
public interface ReadinessInboundPort {

    /**
     * 获取系统就绪状态汇总。
     */
    ReadinessSummary getSummary();

    /**
     * 执行单个检查项。
     *
     * @param checkId 检查项 ID
     * @return 检查结果，找不到时返回 null
     */
    ReadinessCheck runCheck(String checkId);
}
