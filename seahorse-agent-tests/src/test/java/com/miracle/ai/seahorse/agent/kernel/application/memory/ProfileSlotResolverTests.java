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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProfileSlotResolverTests {

    private final ProfileSlotResolver resolver = new ProfileSlotResolver();

    @Test
    void shouldResolveProfileSlotFromMetadata() {
        MemoryItem item = MemoryItem.builder()
                .type("SUMMARY")
                .content("old value")
                .metadataJson("{\"profileSlot\":\"identity.occupation\"}")
                .build();

        Assertions.assertEquals("identity.occupation", resolver.resolve(item));
    }

    @Test
    void shouldResolveLegacyOccupationSemanticKey() {
        MemoryItem item = MemoryItem.builder()
                .type("PROFILE")
                .content("teacher")
                .metadataJson("{\"semanticKey\":\"profile:occupation\"}")
                .build();

        Assertions.assertEquals("identity.occupation", resolver.resolve(item));
    }

    @Test
    void shouldResolveProfileSlotFromProfileLikeContent() {
        MemoryItem item = MemoryItem.builder()
                .type("PREFERENCE")
                .content("我喜欢简短回答")
                .build();

        Assertions.assertEquals("preferences.response_style", resolver.resolve(item));
    }

    @Test
    void shouldIgnoreNonProfileLikeContentWithoutMetadata() {
        MemoryItem item = MemoryItem.builder()
                .type("BUSINESS_DOCUMENT")
                .content("学生报销规则")
                .build();

        Assertions.assertEquals("", resolver.resolve(item));
    }
}
