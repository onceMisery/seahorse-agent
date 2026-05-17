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

package com.miracle.ai.seahorse.agent.kernel.plugin.wrapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 端口包装链测试。
 */
class PortWrapperChainTests {

    @Test
    void shouldApplyWrappersByOrder() {
        List<String> calls = new ArrayList<>();
        PortWrapper<SamplePort> second = wrapper("second", 20, calls);
        PortWrapper<SamplePort> first = wrapper("first", 10, calls);
        PortWrapperChain<SamplePort> chain = new PortWrapperChain<>(List.of(second, first));

        SamplePort wrapped = chain.wrap(() -> calls.add("delegate"));
        wrapped.invoke();

        Assertions.assertEquals(List.of("first", "second", "delegate"), calls);
    }

    @Test
    void shouldExposeWrapperChainDiagnostics() {
        PortWrapper<SamplePort> first = wrapper("same", 10, new ArrayList<>());
        PortWrapper<SamplePort> duplicate = wrapper("same", 20, new ArrayList<>());
        PortWrapper<SamplePort> orderConflict = wrapper("other", 20, new ArrayList<>());

        PortWrapperChain<SamplePort> chain = new PortWrapperChain<>(List.of(first, duplicate, orderConflict));

        Assertions.assertFalse(chain.snapshot().healthy());
        Assertions.assertEquals(3, chain.snapshot().wrappers().size());
        Assertions.assertEquals(2, chain.snapshot().diagnostics().size());
    }

    @Test
    void shouldExposePassThroughWrappersInSnapshot() {
        PortWrapperChain<SamplePort> chain = new PortWrapperChain<>(List.of(new RetryPortWrapper<>()));

        PortWrapperChainSnapshot.PortWrapperDescriptor descriptor = chain.snapshot().wrappers().get(0);

        Assertions.assertEquals("retry", descriptor.name());
        Assertions.assertTrue(descriptor.passThrough());
    }

    private PortWrapper<SamplePort> wrapper(String name, int order, List<String> calls) {
        return new PortWrapper<>() {
            @Override
            public SamplePort wrap(SamplePort delegate) {
                SamplePort safeDelegate = Objects.requireNonNull(delegate, "delegate must not be null");
                return () -> {
                    calls.add(name);
                    safeDelegate.invoke();
                };
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    @FunctionalInterface
    private interface SamplePort {

        void invoke();
    }
}
