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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 默认扩展注册表契约测试。
 * <p>
 * 这些测试冻结微内核插件基础设施的关键语义：
 * 1. 按扩展描述符顺序输出，避免请求期重新排序造成行为漂移；
 * 2. 过滤未启用的 Feature，确保配置驱动开关生效；
 * 3. 默认扩展必须显式声明，避免内核在多个实现之间随机选择。
 */
class DefaultExtensionRegistryTests {

    private static final String PRIMARY_NAME = "primary";
    private static final String SECONDARY_NAME = "secondary";
    private static final String DISABLED_NAME = "disabled";

    @Test
    void shouldReturnActivatedExtensionsInDescriptorOrder() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        AgentFeatureProperties properties = AgentFeatureProperties.empty();
        FeatureActivationContext context = new FeatureActivationContext("tenant-a", "user-a", Map.of(), properties);
        SampleFeature primary = new SampleFeature(PRIMARY_NAME, true);
        SampleFeature secondary = new SampleFeature(SECONDARY_NAME, true);
        SampleFeature disabled = new SampleFeature(DISABLED_NAME, false);

        registry.register(descriptor(SECONDARY_NAME, 20, false), secondary);
        registry.register(descriptor(DISABLED_NAME, 10, false), disabled);
        registry.register(descriptor(PRIMARY_NAME, 5, true), primary);

        List<SampleFeature> extensions = registry.getActivatedExtensions(SampleFeature.class, context);

        Assertions.assertEquals(List.of(primary, secondary), extensions);
    }

    @Test
    void shouldResolveExplicitDefaultExtension() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        SampleFeature primary = new SampleFeature(PRIMARY_NAME, true);
        SampleFeature secondary = new SampleFeature(SECONDARY_NAME, true);

        registry.register(descriptor(SECONDARY_NAME, 20, false), secondary);
        registry.register(descriptor(PRIMARY_NAME, 5, true), primary);

        SampleFeature defaultExtension = registry.getDefaultExtension(SampleFeature.class);

        Assertions.assertSame(primary, defaultExtension);
    }

    @Test
    void shouldRejectDuplicateExtensionNameForSamePort() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();

        registry.register(descriptor(PRIMARY_NAME, 5, true), new SampleFeature(PRIMARY_NAME, true));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> registry.register(descriptor(PRIMARY_NAME, 10, false), new SampleFeature(PRIMARY_NAME, true)));
        Assertions.assertTrue(exception.getMessage().contains(SampleFeature.class.getName()));
        Assertions.assertTrue(exception.getMessage().contains(PRIMARY_NAME));
    }

    @Test
    void shouldRespectDescriptorEnabledByDefault() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        SampleFeature feature = new SampleFeature(PRIMARY_NAME, true);
        ExtensionDescriptor descriptor = new ExtensionDescriptor(
                PRIMARY_NAME, SampleFeature.class, FeatureType.SEARCH_CHANNEL, 5, true, java.util.Set.of(), false);

        registry.register(descriptor, feature);

        List<SampleFeature> extensions = registry.getActivatedExtensions(
                SampleFeature.class, FeatureActivationContext.empty());

        Assertions.assertTrue(extensions.isEmpty());
    }

    @Test
    void shouldExposeRegisteredExtensionSnapshot() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        SampleFeature feature = new SampleFeature(PRIMARY_NAME, true);

        registry.register(descriptor(PRIMARY_NAME, 5, true), feature);

        List<ExtensionRegistration> registrations = registry.registeredExtensions();

        Assertions.assertEquals(1, registrations.size());
        Assertions.assertEquals(PRIMARY_NAME, registrations.get(0).descriptor().name());
        Assertions.assertEquals(feature.getClass().getName(), registrations.get(0).implementationClass());
    }

    private ExtensionDescriptor descriptor(String name, int order, boolean defaultCandidate) {
        return new ExtensionDescriptor(name, SampleFeature.class, FeatureType.SEARCH_CHANNEL, order, defaultCandidate);
    }

    /**
     * 测试专用 Feature。
     * <p>
     * enabled 方法只依赖构造参数，便于精确验证注册表是否正确执行启用过滤。
     */
    private record SampleFeature(String name, boolean active) implements AgentFeature {

        @Override
        public FeatureType type() {
            return FeatureType.SEARCH_CHANNEL;
        }

        @Override
        public boolean enabled(FeatureActivationContext context) {
            return active;
        }
    }
}
