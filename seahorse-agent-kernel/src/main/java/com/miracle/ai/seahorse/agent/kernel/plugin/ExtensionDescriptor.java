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

import java.util.Objects;
import java.util.Set;

/**
 * 扩展描述符。
 * <p>
 * 描述符在启动期确定扩展所属端口、类型、排序和默认候选资格。
 * 请求期只读取预注册结果，避免动态扫描带来的性能抖动。
 *
 * @param name             扩展名称，同一端口下必须唯一
 * @param portType         扩展端口类型
 * @param featureType      Feature 类型
 * @param order            排序值，数字越小越靠前
 * @param defaultCandidate 是否为默认实现候选
 * @param capabilities     扩展能力标签
 * @param enabledByDefault 未显式配置时是否默认启用
 */
public record ExtensionDescriptor(
        String name,
        Class<?> portType,
        FeatureType featureType,
        int order,
        boolean defaultCandidate,
        Set<String> capabilities,
        boolean enabledByDefault
) {

    public ExtensionDescriptor(String name, Class<?> portType, FeatureType featureType, int order,
                               boolean defaultCandidate) {
        this(name, portType, featureType, order, defaultCandidate, Set.of(), true);
    }

    /**
     * 校验扩展描述符。
     * <p>
     * name 和 portType 是注册表索引的必要字段，缺失时必须快速失败。
     */
    public ExtensionDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("扩展名称不能为空");
        }
        portType = Objects.requireNonNull(portType, "扩展端口类型不能为空");
        featureType = Objects.requireNonNull(featureType, "Feature 类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
    }
}
