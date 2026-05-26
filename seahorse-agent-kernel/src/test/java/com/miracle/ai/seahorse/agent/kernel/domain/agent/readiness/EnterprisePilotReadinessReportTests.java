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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnterprisePilotReadinessReportTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldRequireAllReadinessCheckCodes() {
        List<EnterprisePilotReadinessCheckResult> incomplete = List.of(
                result(EnterprisePilotReadinessCheckCode.OWNER, EnterprisePilotReadinessStatus.PASS));

        assertThrows(IllegalArgumentException.class, () -> report(incomplete));
    }

    @Test
    void shouldAggregateFailBeforeWarnAndPass() {
        List<EnterprisePilotReadinessCheckResult> results = EnterprisePilotReadinessCheckCode.all().stream()
                .map(code -> result(code, EnterprisePilotReadinessStatus.PASS))
                .map(result -> result.code() == EnterprisePilotReadinessCheckCode.QUOTA
                        ? result.withStatus(EnterprisePilotReadinessStatus.WARN,
                        EnterprisePilotReadinessReasonCode.QUOTA_MISSING)
                        : result)
                .map(result -> result.code() == EnterprisePilotReadinessCheckCode.EVAL
                        ? result.withStatus(EnterprisePilotReadinessStatus.FAIL,
                        EnterprisePilotReadinessReasonCode.EVAL_FAILED)
                        : result)
                .toList();

        EnterprisePilotReadinessReport report = report(results);

        assertEquals(EnterprisePilotReadinessStatus.FAIL, report.status());
        assertEquals(EnterprisePilotReadinessStatus.WARN,
                report.result(EnterprisePilotReadinessCheckCode.QUOTA).orElseThrow().status());
        assertEquals(EnterprisePilotReadinessStatus.FAIL,
                report.result(EnterprisePilotReadinessCheckCode.EVAL).orElseThrow().status());
    }

    @Test
    void shouldTrimEvidenceAndRejectSecretLikeEvidenceRef() {
        assertThrows(IllegalArgumentException.class, () -> new EnterprisePilotReadinessCheckResult(
                EnterprisePilotReadinessCheckCode.AUDIT,
                EnterprisePilotReadinessStatus.PASS,
                EnterprisePilotReadinessReasonCode.AUDIT_READY,
                " secret-token ",
                "audit evidence",
                NOW));
    }

    @Test
    void shouldRejectSecretLikeReadinessMessage() {
        assertThrows(IllegalArgumentException.class, () -> new EnterprisePilotReadinessCheckResult(
                EnterprisePilotReadinessCheckCode.AUDIT,
                EnterprisePilotReadinessStatus.PASS,
                EnterprisePilotReadinessReasonCode.AUDIT_READY,
                "evidence:AUDIT",
                "rawPrompt bearer secret-token",
                NOW));
    }

    private static EnterprisePilotReadinessReport report(List<EnterprisePilotReadinessCheckResult> results) {
        return new EnterprisePilotReadinessReport(
                "readiness-1",
                "tenant-1",
                "agent-1",
                "version-1",
                null,
                results,
                NOW);
    }

    private static EnterprisePilotReadinessCheckResult result(EnterprisePilotReadinessCheckCode code,
                                                              EnterprisePilotReadinessStatus status) {
        return new EnterprisePilotReadinessCheckResult(
                code,
                status,
                EnterprisePilotReadinessReasonCode.READY,
                "evidence:" + code.name(),
                code.name(),
                NOW);
    }
}
