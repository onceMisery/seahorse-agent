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
import java.util.Set;

/**
 * 扩展加载器契约测试。
 */
class ExtensionLoaderTests {

    @Test
    void shouldLoadExtensionsFromClasspathDescriptor() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        ExtensionLoader loader = ExtensionLoader.usingContextClassLoader();

        int loaded = loader.load(SamplePort.class, FeatureType.SEARCH_CHANNEL, registry);

        List<SamplePort> activatedExtensions = registry.getActivatedExtensions(
                SamplePort.class, FeatureActivationContext.empty());
        Assertions.assertEquals(2, loaded);
        Assertions.assertInstanceOf(SecondaryExtension.class, activatedExtensions.get(0));
        Assertions.assertInstanceOf(PrimaryExtension.class, activatedExtensions.get(1));
        Assertions.assertInstanceOf(PrimaryExtension.class, registry.getDefaultExtension(SamplePort.class));
        ExtensionRegistration primary = registry.registeredExtensions().stream()
                .filter(candidate -> "primary".equals(candidate.descriptor().name()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(Set.of("chat", "memory"), primary.descriptor().capabilities());
        Assertions.assertTrue(primary.descriptor().enabledByDefault());
    }

    interface SamplePort {
    }

    public static class PrimaryExtension implements SamplePort {
    }

    public static class SecondaryExtension implements SamplePort {
    }

    public static class ManagedExtension implements SamplePort {

        public ManagedExtension(String ignored) {
        }
    }
}
