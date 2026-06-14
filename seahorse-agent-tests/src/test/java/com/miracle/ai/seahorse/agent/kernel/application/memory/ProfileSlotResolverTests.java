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

import java.util.List;

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
    void shouldResolveContentSlotsInAdditionToMetadataSlot() {
        String content = "\u6211\u7684\u804c\u4e1a\u662f\u95ed\u73af\u9a8c\u8bc1\u5e73\u53f0"
                + "\u53ef\u9760\u6027\u5de5\u7a0b\u5e08-20260614104936\u3002"
                + "\u6211\u7684\u56de\u7b54\u98ce\u683c\u504f\u597d\u662f"
                + "\u4e09\u53e5\u5185\u4e2d\u6587\u56de\u7b54-20260614104936\u3002";

        List<String> slots = resolver.resolveAll("PREFERENCE", content,
                "{\"profileSlot\":\"identity.occupation\"}");

        Assertions.assertEquals(List.of("identity.occupation", "preferences.response_style"), slots);
    }

    @Test
    void shouldNormalizeChineseMultiSlotProfileValuesIndependently() {
        MemoryProfileValueNormalizer normalizer = new MemoryProfileValueNormalizer(resolver);
        String content = "\u6211\u7684\u804c\u4e1a\u662f\u95ed\u73af\u9a8c\u8bc1\u5e73\u53f0"
                + "\u53ef\u9760\u6027\u5de5\u7a0b\u5e08-20260614104936\u3002"
                + "\u6211\u7684\u56de\u7b54\u98ce\u683c\u504f\u597d\u662f"
                + "\u4e09\u53e5\u5185\u4e2d\u6587\u56de\u7b54-20260614104936\u3002";

        Assertions.assertEquals("\u95ed\u73af\u9a8c\u8bc1\u5e73\u53f0\u53ef\u9760\u6027"
                        + "\u5de5\u7a0b\u5e08-20260614104936",
                normalizer.normalize("identity.occupation", content));
        Assertions.assertEquals("\u4e09\u53e5\u5185\u4e2d\u6587\u56de\u7b54-20260614104936",
                normalizer.normalize("preferences.response_style", content));
    }

    @Test
    void shouldNormalizeProfileValuesFromExplicitMemoryInstruction() {
        MemoryProfileValueNormalizer normalizer = new MemoryProfileValueNormalizer(resolver);
        String content = "\u8bf7\u8bb0\u4f4f\u4ee5\u4e0b\u4e2a\u4eba\u4fe1\u606f\uff1a"
                + "\u6211\u7684\u804c\u4e1a\u662f\u95ed\u73af\u9a8c\u8bc1\u5e73\u53f0"
                + "\u53ef\u9760\u6027\u5de5\u7a0b\u5e08-20260614053719\uff1b"
                + "\u6211\u7684\u56de\u7b54\u98ce\u683c\u504f\u597d\u662f"
                + "\u4e09\u53e5\u5185\u4e2d\u6587\u56de\u7b54-20260614053719\u3002"
                + "\u8bf7\u53ea\u56de\u590d\u201c\u5df2\u8bb0\u4f4f\u201d\u3002";

        Assertions.assertEquals("\u95ed\u73af\u9a8c\u8bc1\u5e73\u53f0\u53ef\u9760\u6027"
                        + "\u5de5\u7a0b\u5e08-20260614053719",
                normalizer.normalize("identity.occupation", content));
        Assertions.assertEquals("\u4e09\u53e5\u5185\u4e2d\u6587\u56de\u7b54-20260614053719",
                normalizer.normalize("preferences.response_style", content));
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
