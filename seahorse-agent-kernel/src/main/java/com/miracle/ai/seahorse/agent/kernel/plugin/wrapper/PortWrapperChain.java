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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 端口包装链。
 *
 * @param <T> 端口类型
 */
public class PortWrapperChain<T> {

    private final List<PortWrapper<T>> wrappers;
    private final List<PortWrapperDiagnostic> diagnostics;

    public PortWrapperChain(List<PortWrapper<T>> wrappers) {
        List<PortWrapper<T>> safeWrappers = Objects.requireNonNullElse(wrappers, List.of());
        this.wrappers = safeWrappers.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(PortWrapper::order))
                .toList();
        this.diagnostics = diagnose(this.wrappers);
    }

    /**
     * 按顺序应用包装器。
     *
     * @param delegate 原始端口
     * @return 包装后的端口
     */
    public T wrap(T delegate) {
        T current = Objects.requireNonNull(delegate, "delegate must not be null");
        for (int index = wrappers.size() - 1; index >= 0; index--) {
            PortWrapper<T> wrapper = wrappers.get(index);
            current = wrapper.wrap(current);
        }
        return current;
    }

    public List<PortWrapper<T>> wrappers() {
        return wrappers;
    }

    public PortWrapperChainSnapshot snapshot() {
        return new PortWrapperChainSnapshot(
                wrappers.stream()
                        .map(wrapper -> new PortWrapperChainSnapshot.PortWrapperDescriptor(
                                wrapper.name(), wrapper.order(), wrapper.type()))
                        .toList(),
                diagnostics);
    }

    public List<PortWrapperDiagnostic> diagnostics() {
        return diagnostics;
    }

    private List<PortWrapperDiagnostic> diagnose(List<PortWrapper<T>> wrappers) {
        Map<String, Integer> names = new LinkedHashMap<>();
        Map<Integer, String> orders = new LinkedHashMap<>();
        java.util.ArrayList<PortWrapperDiagnostic> result = new java.util.ArrayList<>();
        for (PortWrapper<T> wrapper : wrappers) {
            String name = Objects.requireNonNullElse(wrapper.name(), "");
            if (names.put(name, wrapper.order()) != null) {
                result.add(PortWrapperDiagnostic.error(name, "duplicate wrapper name"));
            }
            String existing = orders.putIfAbsent(wrapper.order(), name);
            if (existing != null) {
                result.add(PortWrapperDiagnostic.warning(name,
                        "wrapper order conflicts with " + existing + ": " + wrapper.order()));
            }
        }
        return List.copyOf(result);
    }
}
