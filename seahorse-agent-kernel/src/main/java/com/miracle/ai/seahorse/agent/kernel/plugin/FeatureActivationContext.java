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
 * Feature 激活上下文。
 * <p>
 * 内核在选择扩展链时传入该上下文，Feature 可以按租户、用户、灰度属性和配置决定是否启用。
 *
 * @param tenantId   租户 ID，可以为空字符串
 * @param userId     用户 ID，可以为空字符串
 * @param attributes 灰度、实验或请求属性
 * @param properties Feature 配置快照
 */
public record FeatureActivationContext(
        String tenantId,
        String userId,
        Map<String, Object> attributes,
        AgentFeatureProperties properties
) {

    /**
     * 构造不可变上下文。
     * <p>
     * tenantId 和 userId 使用空字符串兜底，避免 Feature 中出现空指针分支。
     */
    public FeatureActivationContext {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        userId = Objects.requireNonNullElse(userId, "");
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
        properties = Objects.requireNonNullElse(properties, AgentFeatureProperties.empty());
    }

    /**
     * 创建默认上下文。
     *
     * @return 默认激活上下文
     */
    public static FeatureActivationContext empty() {
        return new FeatureActivationContext("", "", Map.of(), AgentFeatureProperties.empty());
    }
}
