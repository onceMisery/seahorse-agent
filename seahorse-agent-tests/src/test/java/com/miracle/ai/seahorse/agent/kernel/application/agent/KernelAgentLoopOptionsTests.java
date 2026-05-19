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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task A5 契约测试：KernelAgentLoopOptions。
 */
class KernelAgentLoopOptionsTests {

    @Test
    void defaultsAreSane() {
        KernelAgentLoopOptions opts = KernelAgentLoopOptions.defaults();
        assertEquals(6, opts.maxSteps());
        assertEquals(Duration.ofSeconds(30), opts.perToolTimeout());
        assertEquals(4, opts.maxParallelTools());
    }

    @Test
    void builderOverridesIndividually() {
        KernelAgentLoopOptions opts = KernelAgentLoopOptions.builder()
                .maxSteps(8)
                .perToolTimeout(Duration.ofSeconds(10))
                .maxParallelTools(2)
                .build();
        assertEquals(8, opts.maxSteps());
        assertEquals(Duration.ofSeconds(10), opts.perToolTimeout());
        assertEquals(2, opts.maxParallelTools());
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> KernelAgentLoopOptions.builder().maxSteps(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> KernelAgentLoopOptions.builder().maxParallelTools(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> KernelAgentLoopOptions.builder().perToolTimeout(Duration.ZERO).build());
        assertThrows(NullPointerException.class,
                () -> KernelAgentLoopOptions.builder().perToolTimeout(null).build());
    }
}
