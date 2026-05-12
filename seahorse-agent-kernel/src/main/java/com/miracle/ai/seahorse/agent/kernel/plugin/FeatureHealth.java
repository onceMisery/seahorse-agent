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

package com.miracle.ai.seahorse.agent.kernel.plugin;

import java.util.Map;
import java.util.Objects;

/**
 * Feature 健康状态。
 * <p>
 * 健康信息用于启动检查、管理端展示和问题定位，不参与主链路决策，避免健康检查影响在线请求。
 *
 * @param name    Feature 名称
 * @param up      是否健康
 * @param message 状态说明
 * @param details 额外详情
 */
public record FeatureHealth(String name, boolean up, String message, Map<String, Object> details) {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    /**
     * 构造不可变健康状态。
     */
    public FeatureHealth {
        name = Objects.requireNonNullElse(name, "");
        message = Objects.requireNonNullElse(message, "");
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
    }

    /**
     * 创建健康状态。
     *
     * @param name Feature 名称
     * @return 健康状态
     */
    public static FeatureHealth up(String name) {
        return new FeatureHealth(name, true, STATUS_UP, Map.of());
    }

    /**
     * 创建不健康状态。
     *
     * @param name    Feature 名称
     * @param message 状态说明
     * @return 不健康状态
     */
    public static FeatureHealth down(String name, String message) {
        return new FeatureHealth(name, false, Objects.requireNonNullElse(message, STATUS_DOWN), Map.of());
    }
}
