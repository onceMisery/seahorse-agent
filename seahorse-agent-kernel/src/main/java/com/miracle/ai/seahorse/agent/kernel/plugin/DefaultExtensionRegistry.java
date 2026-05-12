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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认扩展注册表实现。
 * <p>
 * 该实现面向 Seahorse 原生插件运行时使用：启动时由 Spring 自动配置或显式配置注册扩展，
 * 请求期只做轻量过滤和类型转换。
 * 内部使用 LinkedHashMap 保留注册稳定性，并在注册时拒绝同一端口下的重复名称。
 */
public class DefaultExtensionRegistry implements ExtensionRegistry {

    private final Map<Class<?>, List<RegisteredExtension>> extensions = new LinkedHashMap<>();

    @Override
    public synchronized <T> T getDefaultExtension(Class<T> portType) {
        Objects.requireNonNull(portType, "端口类型不能为空");
        List<RegisteredExtension> candidates = extensionEntries(portType);
        return candidates.stream()
                .filter(entry -> entry.descriptor().defaultCandidate())
                .findFirst()
                .map(entry -> portType.cast(entry.instance()))
                .orElseThrow(() -> new IllegalStateException("未找到默认扩展：" + portType.getName()));
    }

    @Override
    public synchronized <T> List<T> getActivatedExtensions(Class<T> portType, FeatureActivationContext context) {
        Objects.requireNonNull(portType, "端口类型不能为空");
        FeatureActivationContext safeContext = Objects.requireNonNullElse(context, FeatureActivationContext.empty());
        return extensionEntries(portType).stream()
                .filter(entry -> enabledByConfiguration(entry, safeContext))
                .filter(entry -> enabledByFeature(entry, safeContext))
                .map(entry -> portType.cast(entry.instance()))
                .toList();
    }

    @Override
    public synchronized List<ExtensionRegistration> registeredExtensions() {
        return extensions.values().stream()
                .flatMap(List::stream)
                .map(entry -> new ExtensionRegistration(entry.descriptor(), entry.instance().getClass().getName()))
                .toList();
    }

    @Override
    public synchronized void register(ExtensionDescriptor descriptor, Object instance) {
        Objects.requireNonNull(descriptor, "扩展描述符不能为空");
        Objects.requireNonNull(instance, "扩展实例不能为空");
        validatePortType(descriptor, instance);

        List<RegisteredExtension> entries = extensions.computeIfAbsent(descriptor.portType(), ignored -> new ArrayList<>());
        rejectDuplicateName(descriptor, entries);
        entries.add(new RegisteredExtension(descriptor, instance));
        entries.sort(Comparator.comparingInt(entry -> entry.descriptor().order()));
    }

    private List<RegisteredExtension> extensionEntries(Class<?> portType) {
        List<RegisteredExtension> entries = extensions.get(portType);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    private void validatePortType(ExtensionDescriptor descriptor, Object instance) {
        if (!descriptor.portType().isInstance(instance)) {
            throw new IllegalArgumentException("扩展实例不匹配端口类型：" + descriptor.portType().getName());
        }
    }

    private void rejectDuplicateName(ExtensionDescriptor descriptor, List<RegisteredExtension> entries) {
        boolean duplicate = entries.stream()
                .anyMatch(entry -> descriptor.name().equals(entry.descriptor().name()));
        if (duplicate) {
            throw new IllegalArgumentException("扩展名称重复，port=" + descriptor.portType().getName()
                    + ", name=" + descriptor.name());
        }
    }

    private boolean enabledByConfiguration(RegisteredExtension entry, FeatureActivationContext context) {
        AgentFeatureProperties properties = context.properties();
        return properties.enabled(entry.descriptor().name(), entry.descriptor().enabledByDefault());
    }

    private boolean enabledByFeature(RegisteredExtension entry, FeatureActivationContext context) {
        Object instance = entry.instance();
        if (instance instanceof AgentFeature feature) {
            return feature.enabled(context);
        }
        return true;
    }

    /**
     * 注册后的扩展条目。
     * <p>
     * descriptor 和 instance 绑定在一起，保证请求期无需额外查表。
     */
    private record RegisteredExtension(ExtensionDescriptor descriptor, Object instance) {
    }
}
