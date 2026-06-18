/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.ports.outbound.readiness;

import java.util.Map;

/**
 * 适配器探针端口，用于查询各基础设施组件的可用状态。
 * <p>
 * 由自动配置模块实现，桥接 Spring 容器内的 Bean 可用性信息。
 */
public interface ReadinessProbePort {

    /**
     * 查询各组件的可用状态。
     *
     * @return 组件 ID 到可用状态的映射
     */
    Map<String, ComponentStatus> probeComponents();

    /**
     * 查询当前适配器类型名称（例如 "local", "redis", "milvus" 等）。
     *
     * @return 组件 ID 到适配器类型名称的映射
     */
    Map<String, String> adapterTypes();

    record ComponentStatus(boolean available, String adapterType, String detail) {
        public static ComponentStatus available(String adapterType) {
            return new ComponentStatus(true, adapterType, "");
        }

        public static ComponentStatus available(String adapterType, String detail) {
            return new ComponentStatus(true, adapterType, detail);
        }

        public static ComponentStatus unavailable(String detail) {
            return new ComponentStatus(false, "none", detail);
        }
    }
}
