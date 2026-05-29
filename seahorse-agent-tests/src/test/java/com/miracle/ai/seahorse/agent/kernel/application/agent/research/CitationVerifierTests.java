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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitationVerifierTests {

    private final CitationVerifier verifier = new CitationVerifier();

    @Test
    void allCitationsVerified() {
        String report = "According to [1], AI is growing. Also [2] confirms this.";
        List<EvidenceItem> evidence = List.of(
                new EvidenceItem("e1", "s1", "claim1", "q1", "sum1", 0.9, 1),
                new EvidenceItem("e2", "s2", "claim2", "q2", "sum2", 0.8, 2));

        CitationVerifier.VerificationResult result = verifier.verify(report, evidence);

        assertTrue(result.isFullyVerified());
        assertEquals(List.of(1, 2), result.verified().stream().sorted().toList());
        assertTrue(result.missing().isEmpty());
        assertTrue(result.unreferenced().isEmpty());
    }

    @Test
    void missingCitations() {
        String report = "Source [1] says X. Source [3] says Y.";
        List<EvidenceItem> evidence = List.of(
                new EvidenceItem("e1", "s1", "claim1", "q1", "sum1", 0.9, 1));

        CitationVerifier.VerificationResult result = verifier.verify(report, evidence);

        assertFalse(result.isFullyVerified());
        assertEquals(List.of(1), result.verified());
        assertEquals(List.of(3), result.missing());
        assertTrue(result.unreferenced().isEmpty());
    }

    @Test
    void unreferencedEvidence() {
        String report = "Only [1] is cited.";
        List<EvidenceItem> evidence = List.of(
                new EvidenceItem("e1", "s1", "claim1", "q1", "sum1", 0.9, 1),
                new EvidenceItem("e2", "s2", "claim2", "q2", "sum2", 0.8, 2),
                new EvidenceItem("e3", "s3", "claim3", "q3", "sum3", 0.7, 3));

        CitationVerifier.VerificationResult result = verifier.verify(report, evidence);

        assertTrue(result.isFullyVerified());
        assertEquals(List.of(1), result.verified());
        assertTrue(result.missing().isEmpty());
        assertEquals(List.of(2, 3), result.unreferenced().stream().sorted().toList());
    }

    @Test
    void emptyReport() {
        CitationVerifier.VerificationResult result = verifier.verify("", List.of(
                new EvidenceItem("e1", "s1", "c", "q", "s", 0.9, 1)));

        assertTrue(result.isFullyVerified());
        assertTrue(result.verified().isEmpty());
        assertEquals(List.of(1), result.unreferenced());
    }

    @Test
    void nullReport() {
        CitationVerifier.VerificationResult result = verifier.verify(null, List.of(
                new EvidenceItem("e1", "s1", "c", "q", "s", 0.9, 1)));

        assertTrue(result.isFullyVerified());
        assertTrue(result.verified().isEmpty());
    }

    @Test
    void noCitationsNoEvidence() {
        CitationVerifier.VerificationResult result = verifier.verify("No citations here.", List.of());

        assertTrue(result.isFullyVerified());
        assertTrue(result.verified().isEmpty());
        assertTrue(result.missing().isEmpty());
        assertTrue(result.unreferenced().isEmpty());
    }

    @Test
    void duplicateCitationsCountedOnce() {
        String report = "See [1] and again [1] and [1].";
        List<EvidenceItem> evidence = List.of(
                new EvidenceItem("e1", "s1", "c", "q", "s", 0.9, 1));

        CitationVerifier.VerificationResult result = verifier.verify(report, evidence);

        assertTrue(result.isFullyVerified());
        assertEquals(1, result.verified().size());
    }

    @Test
    void handlerRetriesWhenReportReferencesMissingEvidence() {
        VerifyCitationsStepHandler handler = new VerifyCitationsStepHandler(verifier);
        ResearchStepContext context = new ResearchStepContext("run-missing-citation", "q", 0);
        context.setReportContent("Claim [1] is supported, but claim [3] is not.");
        context.addEvidence(new EvidenceItem("e1", "s1", "claim1", "q1", "sum1", 0.9, 1));

        RetryableResearchException ex = assertThrows(RetryableResearchException.class,
                () -> handler.execute(task("run-missing-citation"), context));

        assertTrue(ex.getMessage().contains("Missing citation evidence"));
        assertEquals("Claim [1] is supported, but claim [引用待补] is not.", context.reportContent());
    }

    @Test
    void handlerAcceptsReportWhenAllReferencesHaveEvidence() {
        VerifyCitationsStepHandler handler = new VerifyCitationsStepHandler(verifier);
        ResearchStepContext context = new ResearchStepContext("run-good-citation", "q", 0);
        context.setReportContent("Claim [1] is supported.");
        context.addEvidence(new EvidenceItem("e1", "s1", "claim1", "q1", "sum1", 0.9, 1));

        assertDoesNotThrow(() -> handler.execute(task("run-good-citation"), context));
        assertEquals("Claim [1] is supported.", context.reportContent());
    }

    private static DurableTask task(String runId) {
        return new DurableTask("task-verify", runId, "VERIFY_CITATIONS", 0, Instant.now(), null, null);
    }
}
