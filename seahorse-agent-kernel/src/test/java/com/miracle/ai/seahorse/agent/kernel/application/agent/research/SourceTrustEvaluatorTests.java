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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SourceTrustEvaluatorTests {

    private static final Instant NOW = Instant.parse("2026-05-29T00:00:00Z");
    private final SourceTrustEvaluator evaluator = new SourceTrustEvaluator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void evaluatesFreshHttpsHighReputationDomainsAsHighTrust() {
        WebSource source = source(
                "https://en.wikipedia.org/wiki/Prefix_cache",
                "Prefix cache",
                "A detailed reference page with enough summary text to be useful for grounded research.",
                NOW.minusSeconds(600));

        assertEquals(SourceTrustLevel.HIGH, evaluator.evaluate(source));
    }

    @Test
    void evaluatesHttpsTechnicalDomainsAsMediumTrust() {
        WebSource source = source(
                "https://github.com/openai/openai-java",
                "OpenAI Java SDK",
                "Repository documentation and release notes with implementation details for developers.",
                NOW.minusSeconds(3600));

        assertEquals(SourceTrustLevel.MEDIUM, evaluator.evaluate(source));
    }

    @Test
    void evaluatesOldHttpUnknownShortSourcesAsUntrusted() {
        WebSource source = source(
                "http://unknown.example/post",
                "post",
                "tiny",
                NOW.minusSeconds(10 * 24 * 60 * 60));

        assertEquals(SourceTrustLevel.UNTRUSTED, evaluator.evaluate(source));
    }

    @Test
    void contentHashIsStableForEquivalentSourceMetadata() {
        WebSource first = source(
                "https://www.nature.com/articles/example?utm_source=newsletter",
                "Nature result",
                "Long enough snippet for a research source that should produce a deterministic fingerprint.",
                NOW);
        WebSource second = source(
                "https://www.nature.com/articles/example?utm_source=newsletter",
                "Nature result",
                "Long enough snippet for a research source that should produce a deterministic fingerprint.",
                NOW.plusSeconds(60));

        assertNotNull(evaluator.contentHash(first));
        assertEquals(evaluator.contentHash(first), evaluator.contentHash(second));
    }

    private static WebSource source(String url, String title, String snippet, Instant retrievedAt) {
        return new WebSource(
                "source-1",
                "run-1",
                url,
                title,
                snippet,
                retrievedAt,
                SourceTrustLevel.UNTRUSTED,
                null,
                ExtractionStatus.PENDING);
    }
}
