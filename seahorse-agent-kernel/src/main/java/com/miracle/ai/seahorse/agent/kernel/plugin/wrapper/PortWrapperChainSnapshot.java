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

import java.util.List;
import java.util.Objects;

/**
 * 包装链快照。
 *
 * @param wrappers    包装器元数据
 * @param diagnostics 诊断项
 */
public record PortWrapperChainSnapshot(
        List<PortWrapperDescriptor> wrappers,
        List<PortWrapperDiagnostic> diagnostics
) {

    public PortWrapperChainSnapshot {
        wrappers = List.copyOf(Objects.requireNonNullElse(wrappers, List.of()));
        diagnostics = List.copyOf(Objects.requireNonNullElse(diagnostics, List.of()));
    }

    public boolean healthy() {
        return diagnostics.stream().noneMatch(diagnostic -> "ERROR".equals(diagnostic.level()));
    }

    public record PortWrapperDescriptor(String name, int order, String type, boolean passThrough) {

        public PortWrapperDescriptor {
            name = Objects.requireNonNullElse(name, "");
            type = Objects.requireNonNullElse(type, "generic");
        }
    }
}
