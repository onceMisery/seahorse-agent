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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultResourceAccessPolicyPortTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAllowDelegatedUserToReadOwnUserInputAndMemory() {
        DefaultResourceAccessPolicyPort policy = new DefaultResourceAccessPolicyPort(FIXED_CLOCK);

        AccessDecision userInput = policy.decide(request(ContextResourceType.USER_INPUT, "input-1", "user-1", "{}"));
        AccessDecision memory = policy.decide(request(ContextResourceType.MEMORY, "memory-1", "user-1", "{}"));

        assertEquals(AccessDecisionEffect.ALLOW, userInput.effect());
        assertEquals(ResourceAccessReasonCodes.OWNER_MATCH, userInput.reasonCode());
        assertEquals(AccessDecisionEffect.ALLOW, memory.effect());
        assertEquals(ResourceAccessReasonCodes.OWNER_MATCH, memory.reasonCode());
        assertEquals(NOW, memory.createdAt());
    }

    @Test
    void shouldDenyDelegatedUserReadingAnotherUsersMemory() {
        DefaultResourceAccessPolicyPort policy = new DefaultResourceAccessPolicyPort(FIXED_CLOCK);

        AccessDecision decision = policy.decide(request(ContextResourceType.MEMORY, "memory-2", "user-2", "{}"));

        assertEquals(AccessDecisionEffect.DENY, decision.effect());
        assertEquals(ResourceAccessReasonCodes.OWNER_REQUIRED, decision.reasonCode());
    }

    @Test
    void shouldAllowPublicOrOwnedDocumentsAndDenyUnownedPrivateDocuments() {
        DefaultResourceAccessPolicyPort policy = new DefaultResourceAccessPolicyPort(FIXED_CLOCK);

        AccessDecision publicDocument = policy.decide(request(ContextResourceType.DOCUMENT, "doc-1", "user-2",
                "{\"visibility\":\"public\"}"));
        AccessDecision ownedDocument = policy.decide(request(ContextResourceType.DOCUMENT, "doc-2", "user-1",
                "{}"));
        AccessDecision privateDocument = policy.decide(request(ContextResourceType.DOCUMENT, "doc-3", "user-2",
                "{}"));

        assertEquals(AccessDecisionEffect.ALLOW, publicDocument.effect());
        assertEquals(ResourceAccessReasonCodes.PUBLIC_RESOURCE, publicDocument.reasonCode());
        assertEquals(AccessDecisionEffect.ALLOW, ownedDocument.effect());
        assertEquals(ResourceAccessReasonCodes.OWNER_MATCH, ownedDocument.reasonCode());
        assertEquals(AccessDecisionEffect.DENY, privateDocument.effect());
        assertEquals(ResourceAccessReasonCodes.PUBLIC_OR_OWNER_REQUIRED, privateDocument.reasonCode());
    }

    @Test
    void shouldDenyTenantMismatchAndUnsupportedActions() {
        DefaultResourceAccessPolicyPort policy = new DefaultResourceAccessPolicyPort(FIXED_CLOCK);

        AccessDecision tenantMismatch = policy.decide(new ResourceAccessRequest(
                "tenant-2",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                new ResourceRef(ContextResourceType.MEMORY, "memory-1", "tenant-1", "user-1", "{}")));
        AccessDecision write = policy.decide(new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.WRITE,
                new ResourceRef(ContextResourceType.MEMORY, "memory-1", "tenant-1", "user-1", "{}")));

        assertEquals(AccessDecisionEffect.DENY, tenantMismatch.effect());
        assertEquals(ResourceAccessReasonCodes.TENANT_MISMATCH, tenantMismatch.reasonCode());
        assertEquals(AccessDecisionEffect.DENY, write.effect());
        assertEquals(ResourceAccessReasonCodes.READ_ONLY_POLICY, write.reasonCode());
    }

    private static ResourceAccessRequest request(ContextResourceType resourceType,
                                                 String resourceId,
                                                 String ownerUserId,
                                                 String attributesJson) {
        return new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                new ResourceRef(resourceType, resourceId, "tenant-1", ownerUserId, attributesJson));
    }
}
