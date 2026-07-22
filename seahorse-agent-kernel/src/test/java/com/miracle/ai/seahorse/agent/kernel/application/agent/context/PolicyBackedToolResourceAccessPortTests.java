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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyBackedToolResourceAccessPortTests {

    @Test
    void shouldMapDistinctResourcesToCanonicalPolicyUsingTrustedRequestScope() {
        List<ResourceAccessRequest> requests = new ArrayList<>();
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request -> {
            requests.add(request);
            return decision(request, AccessDecisionEffect.ALLOW, "RESOURCE_ACL_ALLOW");
        });

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.WRITE,
                Map.of("primary", "resource-b", "duplicate", "resource-b", "secondary", "resource-a")));

        assertTrue(result.allowed());
        assertEquals(List.of("resource-a", "resource-b"),
                requests.stream().map(item -> item.resourceRef().resourceId()).toList());
        assertTrue(requests.stream().allMatch(item -> "tenant-1".equals(item.tenantId())));
        assertTrue(requests.stream().allMatch(item -> item.subjectType() == AccessSubjectType.USER_DELEGATED_AGENT));
        assertTrue(requests.stream().allMatch(item -> "user-1".equals(item.subjectId())));
        assertTrue(requests.stream().allMatch(item -> item.action() == ResourceAction.WRITE));
        assertTrue(requests.stream().allMatch(item -> "MEMORY".equals(item.resourceRef().resourceType())));
        assertTrue(requests.stream().allMatch(item -> "tenant-1".equals(item.resourceRef().tenantId())));
        assertTrue(requests.stream().allMatch(item -> "{}".equals(item.resourceRef().attributesJson())));
        assertTrue(requests.stream().allMatch(item -> item.resourceRef().ownerUserId() == null));
    }

    @Test
    void shouldDenyWhenAnyResourceIsDeniedWithoutLeakingResourceId() {
        String sensitiveResourceId = "sensitive-resource-value";
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request ->
                decision(request,
                        sensitiveResourceId.equals(request.resourceRef().resourceId())
                                ? AccessDecisionEffect.DENY
                                : AccessDecisionEffect.ALLOW,
                        sensitiveResourceId.equals(request.resourceRef().resourceId())
                                ? sensitiveResourceId
                                : "RESOURCE_ACL_ALLOW"));

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.READ,
                Map.of("first", "allowed-resource", "second", sensitiveResourceId)));

        assertFalse(result.allowed());
        assertEquals("Resource access denied", result.reason());
        assertFalse(result.reason().contains(sensitiveResourceId));
    }

    @Test
    void shouldFailClosedWhenCanonicalDecisionDoesNotMatchRequest() {
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request ->
                new AccessDecision(
                        "decision-1",
                        request.tenantId(),
                        request.subjectType(),
                        request.subjectId(),
                        request.action(),
                        request.resourceRef().resourceType(),
                        "different-resource",
                        AccessDecisionEffect.ALLOW,
                        "RESOURCE_ACL_ALLOW",
                        Instant.EPOCH));

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.READ,
                Map.of("memoryId", "memory-1")));

        assertFalse(result.allowed());
        assertEquals("Resource access decision does not match request", result.reason());
    }

    @Test
    void shouldFailClosedWithoutLeakingPolicyFailure() {
        String sensitiveFailure = "policy failed for secret-resource-id";
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request -> {
            throw new IllegalStateException(sensitiveFailure);
        });

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.READ,
                Map.of("memoryId", "memory-1")));

        assertFalse(result.allowed());
        assertEquals("Resource access policy failed", result.reason());
        assertFalse(result.reason().contains(sensitiveFailure));
    }

    @Test
    void shouldFailClosedForUnsupportedExternalSendAction() {
        List<ResourceAccessRequest> requests = new ArrayList<>();
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request -> {
            requests.add(request);
            return decision(request, AccessDecisionEffect.ALLOW, "RESOURCE_ACL_ALLOW");
        });

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.EXTERNAL_SEND,
                Map.of("target", "external-target")));

        assertFalse(result.allowed());
        assertEquals("Resource action is not supported", result.reason());
        assertTrue(requests.isEmpty());
    }

    @Test
    void shouldFailClosedForIncompleteOrInvalidReferences() {
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request -> {
            throw new AssertionError("canonical policy must not be called for invalid input");
        });

        ToolResourceAccessDecision missingTenant = port.decide(new ToolResourceAccessRequest(
                "run-1", "agent-1", "version-1", null, "user-1", "identity-1",
                "tool-1", "MEMORY", ToolActionType.READ, Map.of("memoryId", "memory-1")));
        ToolResourceAccessDecision invalidReference = port.decide(request(
                ToolActionType.READ,
                Map.of("memoryId", " ")));

        assertFalse(missingTenant.allowed());
        assertEquals("Resource access request is incomplete", missingTenant.reason());
        assertFalse(invalidReference.allowed());
        assertEquals("Resource reference is invalid", invalidReference.reason());
    }

    @Test
    void shouldFailClosedWhenCanonicalPolicyReturnsNoDecision() {
        PolicyBackedToolResourceAccessPort port = new PolicyBackedToolResourceAccessPort(request -> null);

        ToolResourceAccessDecision result = port.decide(request(
                ToolActionType.EXECUTE,
                Map.of("sandboxSessionId", "session-1")));

        assertFalse(result.allowed());
        assertEquals("Resource access decision is missing", result.reason());
    }

    private ToolResourceAccessRequest request(ToolActionType actionType, Map<String, String> resourceRefs) {
        return new ToolResourceAccessRequest(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "identity-1",
                "tool-1",
                "MEMORY",
                actionType,
                resourceRefs);
    }

    private AccessDecision decision(ResourceAccessRequest request,
                                    AccessDecisionEffect effect,
                                    String reasonCode) {
        return new AccessDecision(
                "decision-1",
                request.tenantId(),
                request.subjectType(),
                request.subjectId(),
                request.action(),
                request.resourceRef().resourceType(),
                request.resourceRef().resourceId(),
                effect,
                reasonCode,
                Instant.EPOCH);
    }
}
