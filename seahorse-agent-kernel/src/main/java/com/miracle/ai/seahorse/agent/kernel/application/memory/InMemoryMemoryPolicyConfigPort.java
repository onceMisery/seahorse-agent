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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryMemoryPolicyConfigPort implements MemoryPolicyConfigPort {

    private final AtomicReference<MemoryPolicyConfig> config;

    public InMemoryMemoryPolicyConfigPort(MemoryPolicyConfig initialConfig) {
        this.config = new AtomicReference<>(Objects.requireNonNullElseGet(initialConfig, MemoryPolicyConfig::defaults));
    }

    @Override
    public MemoryPolicyConfig current() {
        return config.get();
    }

    @Override
    public MemoryPolicyConfig update(MemoryPolicyConfig config) {
        MemoryPolicyConfig safeConfig = Objects.requireNonNullElseGet(config, MemoryPolicyConfig::defaults);
        this.config.set(safeConfig);
        return safeConfig;
    }
}
