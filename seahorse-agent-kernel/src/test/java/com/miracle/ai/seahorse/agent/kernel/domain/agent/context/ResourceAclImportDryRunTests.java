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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceAclImportDryRunTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void shouldBuildNaturalKeyWithoutPriority() {
        ResourceAclImportItem lowPriority = item("doc-1", AccessDecisionEffect.ALLOW, 10, null);
        ResourceAclImportItem highPriority = item("doc-1", AccessDecisionEffect.DENY, 100, NOW.plusSeconds(60));

        assertEquals(lowPriority.naturalKey(), highPriority.naturalKey());
    }

    @Test
    void shouldSummarizeDryRunItemStatuses() {
        ResourceAclNaturalKey key = item("doc-1", AccessDecisionEffect.ALLOW, 10, null).naturalKey();
        ResourceAclImportDryRunReport report = new ResourceAclImportDryRunReport(List.of(
                result(0, ResourceAclImportItemStatus.VALID, ResourceAclImportReasonCode.VALID_RULE, key),
                result(1, ResourceAclImportItemStatus.INVALID, ResourceAclImportReasonCode.EXPIRED_INPUT, key),
                result(2, ResourceAclImportItemStatus.DUPLICATE_IN_BATCH,
                        ResourceAclImportReasonCode.NATURAL_KEY_DUPLICATE, key),
                result(3, ResourceAclImportItemStatus.DUPLICATE_EXISTING,
                        ResourceAclImportReasonCode.EXISTING_RULE_DUPLICATE, key),
                result(4, ResourceAclImportItemStatus.CONFLICT,
                        ResourceAclImportReasonCode.DENY_ALLOW_CONFLICT, key),
                result(5, ResourceAclImportItemStatus.UNSUPPORTED_SCOPE,
                        ResourceAclImportReasonCode.UNSUPPORTED_SCOPE, key)));

        assertEquals(1, report.validCount());
        assertEquals(1, report.invalidCount());
        assertEquals(1, report.duplicateInBatchCount());
        assertEquals(1, report.duplicateExistingCount());
        assertEquals(1, report.conflictCount());
        assertEquals(1, report.unsupportedScopeCount());
        assertThrows(UnsupportedOperationException.class, () -> report.items().add(
                result(6, ResourceAclImportItemStatus.VALID, ResourceAclImportReasonCode.VALID_RULE, key)));
    }

    private static ResourceAclImportDryRunItem result(int index,
                                                      ResourceAclImportItemStatus status,
                                                      ResourceAclImportReasonCode reasonCode,
                                                      ResourceAclNaturalKey key) {
        return new ResourceAclImportDryRunItem(
                index,
                item("doc-" + index, AccessDecisionEffect.ALLOW, 10, null),
                key,
                status,
                reasonCode);
    }

    private static ResourceAclImportItem item(String resourceId,
                                             AccessDecisionEffect effect,
                                             int priority,
                                             Instant expiresAt) {
        return new ResourceAclImportItem(
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                resourceId,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                priority,
                expiresAt);
    }
}
