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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ExtractionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ResearchStepContextSerializationTests {

    @Test
    void roundTrip_emptyContext() {
        ResearchStepContext ctx = new ResearchStepContext("run-1", "test query", 5L);
        ctx.setTenantId("tenant-a");
        ctx.setUserId("user-b");

        String json = ctx.toJson();
        assertNotNull(json);

        ResearchStepContext restored = ResearchStepContext.fromJson(json);
        assertNotNull(restored);
        assertEquals("run-1", restored.runId());
        assertEquals("test query", restored.query());
        assertEquals("tenant-a", restored.tenantId());
        assertEquals("user-b", restored.userId());
        assertTrue(restored.searchQueries().isEmpty());
        assertTrue(restored.sources().isEmpty());
        assertTrue(restored.evidence().isEmpty());
    }

    @Test
    void roundTrip_fullContext() {
        ResearchStepContext ctx = new ResearchStepContext("run-2", "AI research", 10L);
        ctx.setTenantId("t1");
        ctx.setUserId("u1");
        ctx.setArtifactId("art-123");
        ctx.addSearchQuery("machine learning trends");
        ctx.addSearchQuery("deep learning 2025");
        ctx.addSource(new WebSource(
                "src-1", "run-2", "https://example.com", "Example",
                "snippet text", Instant.ofEpochMilli(1700000000000L),
                SourceTrustLevel.UNTRUSTED, "hash123", ExtractionStatus.EXTRACTED));
        ctx.addEvidence(new EvidenceItem(
                "ev-1", "src-1", "AI is growing", "quote preview", "summary", 0.95, 1));
        ctx.putFetchedContent("src-1", "full page content here");
        ctx.setReportContent("# Report\n\nContent [1]");

        String json = ctx.toJson();
        ResearchStepContext restored = ResearchStepContext.fromJson(json);

        assertEquals("run-2", restored.runId());
        assertEquals("AI research", restored.query());
        assertEquals("t1", restored.tenantId());
        assertEquals("u1", restored.userId());
        assertEquals("art-123", restored.artifactId());
        assertEquals(2, restored.searchQueries().size());
        assertEquals("machine learning trends", restored.searchQueries().get(0));
        assertEquals(1, restored.sources().size());
        WebSource restoredSource = restored.sources().get(0);
        assertEquals("src-1", restoredSource.sourceId());
        assertEquals("https://example.com", restoredSource.url());
        assertEquals(Instant.ofEpochMilli(1700000000000L), restoredSource.retrievedAt());
        assertEquals(SourceTrustLevel.UNTRUSTED, restoredSource.trustLevel());
        assertEquals(ExtractionStatus.EXTRACTED, restoredSource.extractionStatus());
        assertEquals(1, restored.evidence().size());
        assertEquals("AI is growing", restored.evidence().get(0).claim());
        assertEquals(0.95, restored.evidence().get(0).confidence(), 0.001);
        assertEquals("full page content here", restored.getFetchedContent("src-1"));
        assertEquals("# Report\n\nContent [1]", restored.reportContent());
    }

    @Test
    void fromJson_nullOrBlank_returnsNull() {
        assertNull(ResearchStepContext.fromJson(null));
        assertNull(ResearchStepContext.fromJson(""));
        assertNull(ResearchStepContext.fromJson("   "));
    }

    @Test
    void fromJson_invalidJson_throwsException() {
        assertThrows(IllegalStateException.class, () -> ResearchStepContext.fromJson("{invalid"));
    }

    @Test
    void seqCounter_preservedAcrossRoundTrip() {
        ResearchStepContext ctx = new ResearchStepContext("run-3", "q", 42L);
        ctx.nextSeq();
        ctx.nextSeq();

        String json = ctx.toJson();
        ResearchStepContext restored = ResearchStepContext.fromJson(json);
        assertEquals(45, restored.nextSeq());
    }

    @Test
    void sourceWithNullInstant_handledGracefully() {
        ResearchStepContext ctx = new ResearchStepContext("run-4", "q", 0L);
        ctx.addSource(new WebSource("s1", "run-4", "http://x.com", "X", null, null,
                SourceTrustLevel.LOW, null, ExtractionStatus.PENDING));

        String json = ctx.toJson();
        ResearchStepContext restored = ResearchStepContext.fromJson(json);
        assertEquals(1, restored.sources().size());
        assertNull(restored.sources().get(0).retrievedAt());
    }

    @Test
    void profileLimits_preservedAcrossRoundTrip() {
        ResearchStepContext ctx = new ResearchStepContext("run-limits", "q", 0L);
        ctx.setMaxSearchQueries(2);
        ctx.setMaxSources(4);

        ResearchStepContext restored = ResearchStepContext.fromJson(ctx.toJson());

        assertEquals(2, restored.maxSearchQueries());
        assertEquals(4, restored.maxSources());
    }
}
