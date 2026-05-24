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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.ports.common.NoopFallback;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spec §7 生产 noop 风险治理：Slice 2a 骨架。
 *
 * <p>本组件在启动期扫描已注册的关键端口，区分真实实现与 {@link NoopFallback} fallback，并按风险等级
 * 报告结果。Slice 2a 仅做检测与日志，不强制 fail-fast；后续 Slice 2b 将完成更多端口转换以及生产策略强制。
 *
 * <p>配置开关：
 * <ul>
 *     <li>{@code seahorse-agent.runtime.noop-guard.enabled} — 默认 {@code true}；置为 {@code false} 关闭。</li>
 *     <li>{@code seahorse-agent.runtime.noop-guard.enforce-class-a} — 默认 {@code false}；置为 {@code true}
 *         时检测到 A 类 noop fallback 立即抛出 {@link NoopFallbackEnforcementException}。</li>
 * </ul>
 */
public class SeahorseAgentNoopPortGuard implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseAgentNoopPortGuard.class);

    /**
     * 端口风险分类。
     */
    public enum RiskClass {
        /**
         * A 类：丢写入 / 跳过审计 / 跳过关键持久化。生产应 fail-fast。
         */
        CLASS_A_FAIL_FAST,
        /**
         * B 类：跳过索引 / 观测 / 异步增强。允许保留但应 WARN + metric。
         */
        CLASS_B_WARN,
        /**
         * C 类：纯增强降级。允许 noop。
         */
        CLASS_C_OK
    }

    /**
     * 端口检测结果。
     *
     * @param portClass     端口接口类型
     * @param actualClass   实际 bean 实现类
     * @param riskClass     风险分类
     * @param isNoopFallback 是否为显式 noop fallback
     * @param missing       上下文中是否未发现该端口
     */
    public record Inspection(Class<?> portClass,
                              Class<?> actualClass,
                              RiskClass riskClass,
                              boolean isNoopFallback,
                              boolean missing) {
    }

    private final ApplicationContext applicationContext;
    private final boolean enforceClassA;
    private final Map<Class<?>, RiskClass> classifications;

    public SeahorseAgentNoopPortGuard(ApplicationContext applicationContext, boolean enforceClassA) {
        this(applicationContext, enforceClassA, defaultClassifications());
    }

    public SeahorseAgentNoopPortGuard(ApplicationContext applicationContext,
                                      boolean enforceClassA,
                                      Map<Class<?>, RiskClass> classifications) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.enforceClassA = enforceClassA;
        this.classifications = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNullElse(classifications, Map.of())));
    }

    /**
     * 启动期扫描。
     *
     * <p>对 {@link #classifications} 中登记的每个端口：
     * <ol>
     *     <li>从 {@link ApplicationContext} 解析单例 bean（缺失则视为 missing）。</li>
     *     <li>判断 {@code instanceof NoopFallback}。</li>
     *     <li>若是 A 类 noop fallback 且开启 {@link #enforceClassA}，抛出 {@link NoopFallbackEnforcementException}。</li>
     * </ol>
     */
    public List<Inspection> inspect() {
        List<Inspection> inspections = new ArrayList<>(classifications.size());
        for (Map.Entry<Class<?>, RiskClass> entry : classifications.entrySet()) {
            Class<?> portClass = entry.getKey();
            RiskClass riskClass = entry.getValue();
            Object bean = resolveBean(portClass);
            Inspection inspection = inspect(portClass, bean, riskClass);
            inspections.add(inspection);
            report(inspection);
            if (enforceClassA && inspection.isNoopFallback() && inspection.riskClass() == RiskClass.CLASS_A_FAIL_FAST) {
                throw new NoopFallbackEnforcementException("Class A port bound to a NoopFallback fallback in production: "
                        + portClass.getName() + " => " + inspection.actualClass().getName());
            }
        }
        return inspections;
    }

    @Override
    public void afterSingletonsInstantiated() {
        inspect();
    }

    private Object resolveBean(Class<?> portClass) {
        ObjectProvider<?> provider = applicationContext.getBeanProvider(portClass);
        return provider.getIfAvailable();
    }

    private Inspection inspect(Class<?> portClass, Object bean, RiskClass riskClass) {
        if (bean == null) {
            return new Inspection(portClass, null, riskClass, false, true);
        }
        boolean noop = bean instanceof NoopFallback;
        return new Inspection(portClass, bean.getClass(), riskClass, noop, false);
    }

    private void report(Inspection inspection) {
        if (inspection.missing()) {
            LOGGER.debug("Noop guard: port {} missing from context (riskClass={})",
                    inspection.portClass().getName(), inspection.riskClass());
            return;
        }
        if (!inspection.isNoopFallback()) {
            LOGGER.info("Noop guard: port {} bound to real implementation {} (riskClass={})",
                    inspection.portClass().getName(),
                    inspection.actualClass().getName(),
                    inspection.riskClass());
            return;
        }
        switch (inspection.riskClass()) {
            case CLASS_A_FAIL_FAST -> LOGGER.warn(
                    "Noop guard: Class A port {} bound to NoopFallback {} — production deployments must replace this",
                    inspection.portClass().getName(), inspection.actualClass().getName());
            case CLASS_B_WARN -> LOGGER.warn(
                    "Noop guard: Class B port {} bound to NoopFallback {} — index/observation enhancement disabled",
                    inspection.portClass().getName(), inspection.actualClass().getName());
            case CLASS_C_OK -> LOGGER.info(
                    "Noop guard: Class C port {} bound to NoopFallback {} (acceptable)",
                    inspection.portClass().getName(), inspection.actualClass().getName());
        }
    }

    /**
     * Slice 2a-c 默认分类表。当前覆盖：
     * <ul>
     *     <li>Class A：{@link OutputValidationRecordPort}（治理审计）、{@link MemoryOutboxPort}（异步派发）、
     *         {@link MemoryReviewCandidatePort}（人工 review）、{@link MemoryOperationLogPort}（操作日志）、
     *         {@link ToolInvocationAuditPort}（工具调用审计）。</li>
     *     <li>Class B：{@link MemoryVectorPort}（向量索引）、{@link ObservationPort}（观测后端）。</li>
     *     <li>Class C：{@link MemoryRefinerPort}（增强细化）、{@link MemoryCompactionSummarizerPort}
     *         （压缩摘要）、{@link MemoryGraphPort}（图谱增强）。</li>
     * </ul>
     */
    public static Map<Class<?>, RiskClass> defaultClassifications() {
        Map<Class<?>, RiskClass> map = new LinkedHashMap<>();
        map.put(OutputValidationRecordPort.class, RiskClass.CLASS_A_FAIL_FAST);
        map.put(MemoryOutboxPort.class, RiskClass.CLASS_A_FAIL_FAST);
        map.put(MemoryReviewCandidatePort.class, RiskClass.CLASS_A_FAIL_FAST);
        map.put(MemoryOperationLogPort.class, RiskClass.CLASS_A_FAIL_FAST);
        map.put(ToolInvocationAuditPort.class, RiskClass.CLASS_A_FAIL_FAST);
        map.put(MemoryVectorPort.class, RiskClass.CLASS_B_WARN);
        map.put(ObservationPort.class, RiskClass.CLASS_B_WARN);
        map.put(MemoryRefinerPort.class, RiskClass.CLASS_C_OK);
        map.put(MemoryCompactionSummarizerPort.class, RiskClass.CLASS_C_OK);
        map.put(MemoryGraphPort.class, RiskClass.CLASS_C_OK);
        return Collections.unmodifiableMap(map);
    }

    /**
     * A 类 noop 在生产 fail-fast 时抛出。
     */
    public static final class NoopFallbackEnforcementException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoopFallbackEnforcementException(String message) {
            super(message);
        }
    }
}
