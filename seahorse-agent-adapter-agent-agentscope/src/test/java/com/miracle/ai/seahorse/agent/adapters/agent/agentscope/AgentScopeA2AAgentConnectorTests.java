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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentScopeA2AAgentConnectorTests {

    @Test
    void rejectsRemoteCardFromDifferentTenant() {
        AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-b", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(resolver(card), (ignored, request) -> "");

        assertThrows(SecurityException.class,
                () -> connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner")));
    }

    @Test
    void resolvesAndInvokesMatchingTenant() {
        AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(card),
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));
        A2AAgentResult result = connector.invoke(new A2AAgentRequest("tenant-a", "planner", "draft", Map.of()));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
        assertEquals("remote: draft", result.content());
    }

    @Test
    void resolvesTenantQualifiedCardBeforePlainAgentName() {
        AgentCard wrongTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-b", Map.of());
        AgentCard matchingTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "tenant-a/planner".equals(agentName) ? matchingTenant : wrongTenant,
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
    }

    @Test
    void continuesCandidateLookupWhenResolverThrowsForMissingQualifiedName() {
        AgentCard matchingTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> {
                    if ("planner".equals(agentName)) {
                        return matchingTenant;
                    }
                    throw new IllegalStateException("agent not found: " + agentName);
                },
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
    }

    @Test
    void exposesEffectiveJsonRpcUrlFromAdditionalInterfaces() {
        AgentCard card = A2ATenantMetadata.withTenant(new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("planner")
                .description("Planner")
                .version("1.0.0")
                .url("http://metadata-only/a2a")
                .additionalInterfaces(List.of(new AgentInterface("JSONRPC", "http://runtime/a2a")))
                .capabilities(A2ATenantMetadataTests.baseCard().capabilities())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(A2ATenantMetadataTests.baseCard().skills())
                .build(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(card),
                (ignored, request) -> "remote");

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("http://runtime/a2a", remoteCard.url());
    }

    @Test
    void exposesM3MetadataFromResolvedAgentCard() {
        AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of(
                "mode", "M3",
                "namespace", "seahorse-agent",
                "group", "DEFAULT_GROUP",
                "clusterName", "local"));
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(card),
                (ignored, request) -> "remote");

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("M3", remoteCard.metadata().get("m3.mode"));
        assertEquals("seahorse-agent", remoteCard.metadata().get("m3.namespace"));
        assertEquals("DEFAULT_GROUP", remoteCard.metadata().get("m3.group"));
        assertEquals("local", remoteCard.metadata().get("m3.clusterName"));
    }

    @Test
    void exposesA2aGovernanceMetadataFromResolvedAgentCard() {
        AgentCard card = new AgentScopeAgentCardFactory().agentCard(a2aPropertiesWithTenantSignedAuth());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(card),
                (ignored, request) -> "remote");

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("tenant-signed", remoteCard.metadata().get("authMode"));
        assertEquals("http://runtime.example/a2a", remoteCard.metadata().get("jsonrpcUrl"));
        assertEquals("http://runtime.example/actuator/health", remoteCard.metadata().get("healthUrl"));
    }

    @Test
    void prefersSameM3ClusterWhenMultipleTenantCardsAreAvailable() {
        AgentCard remoteCluster = tenantCardWithM3Url("tenant-a", "remote", "http://remote-cluster/a2a");
        AgentCard localCluster = tenantCardWithM3Url("tenant-a", "local", "http://local-cluster/a2a");
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "planner".equals(agentName) ? localCluster : remoteCluster,
                (ignored, request) -> "remote",
                A2aDiscoveryPolicy.preferM3(Map.of(
                        "mode", "M3",
                        "namespace", "seahorse-agent",
                        "group", "DEFAULT_GROUP",
                        "clusterName", "local")));

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("http://local-cluster/a2a", remoteCard.url());
        assertEquals("local", remoteCard.metadata().get("m3.clusterName"));
    }

    @Test
    void prefersHealthyCandidateWhenHealthUrlIsAvailable() {
        AgentCard down = tenantCardWithM3Url("tenant-a", "local", "http://down-runtime/a2a");
        AgentCard up = tenantCardWithM3Url("tenant-a", "local", "http://up-runtime/a2a");
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "planner".equals(agentName) ? up : down,
                (ignored, request) -> "remote",
                A2aDiscoveryPolicy.preferM3(Map.of(
                        "mode", "M3",
                        "namespace", "seahorse-agent",
                        "group", "DEFAULT_GROUP",
                        "clusterName", "local"),
                        url -> url.contains("up-runtime")
                                ? A2aEndpointHealthStatus.UP
                                : A2aEndpointHealthStatus.DOWN));

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("http://up-runtime/a2a", remoteCard.url());
    }

    @Test
    void resolvesRequestedVersionBeforeLatestAgentCard() {
        AgentCard latest = tenantCardWithVersionUrl("tenant-a", "2.0.0", "http://latest-runtime/a2a");
        AgentCard requested = tenantCardWithVersionUrl("tenant-a", "1.0.0", "http://v1-runtime/a2a");
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "tenant-a/planner@1.0.0".equals(agentName) ? requested : latest,
                (ignored, request) -> "remote");

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner", "1.0.0"));

        assertEquals("1.0.0", remoteCard.version());
        assertEquals("http://v1-runtime/a2a", remoteCard.url());
    }

    @Test
    void rejectsLatestFallbackWhenRequestedVersionIsMissing() {
        AgentCard latest = tenantCardWithVersionUrl("tenant-a", "2.0.0", "http://latest-runtime/a2a");
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(latest),
                (ignored, request) -> "remote");

        assertThrows(IllegalStateException.class,
                () -> connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner", "1.0.0")));
    }

    @Test
    void invokeUsesMetadataVersionWhenResolvingRemoteAgent() {
        AgentCard latest = tenantCardWithVersionUrl("tenant-a", "2.0.0", "http://latest-runtime/a2a");
        AgentCard requested = tenantCardWithVersionUrl("tenant-a", "1.0.0", "http://v1-runtime/a2a");
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "tenant-a/planner@1.0.0".equals(agentName) ? requested : latest,
                (card, request) -> card.version() + ":" + request.prompt());

        A2AAgentResult result = connector.invoke(new A2AAgentRequest("tenant-a", "planner", "draft",
                Map.of("version", "1.0.0")));

        assertEquals("1.0.0:draft", result.content());
        assertEquals("1.0.0", result.metadata().get("version"));
        assertEquals("http://v1-runtime/a2a", result.metadata().get("jsonrpcUrl"));
    }

    @Test
    void avoidsExpiredCandidateWhenFreshCandidateExists() {
        AgentCard expired = tenantCardWithA2aExpiry("tenant-a", "http://expired-runtime/a2a",
                "2026-06-20T23:59:00Z");
        AgentCard fresh = tenantCardWithA2aExpiry("tenant-a", "http://fresh-runtime/a2a",
                "2026-06-21T00:05:00Z");
        A2aDiscoveryPolicy policy = A2aDiscoveryPolicy.withClock(
                Map.of(),
                A2aEndpointHealthProbe.unknown(),
                Clock.fixed(Instant.parse("2026-06-21T00:00:00Z"), ZoneOffset.UTC));
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "planner".equals(agentName) ? fresh : expired,
                (ignored, request) -> "remote",
                policy);

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("http://fresh-runtime/a2a", remoteCard.url());
        assertEquals("2026-06-21T00:05:00Z", remoteCard.metadata().get("expiresAt"));
    }

    private AgentCardResolver resolver(AgentCard card) {
        return agentName -> card;
    }

    private AgentScopeProperties a2aPropertiesWithTenantSignedAuth() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");
        properties.getA2a().setUrl("http://runtime.example/a2a");
        properties.getA2a().setAuthMode(A2aAuthMode.TENANT_SIGNED);
        return properties;
    }

    private AgentCard tenantCardWithM3Url(String tenantId, String clusterName, String url) {
        return A2ATenantMetadata.withTenant(new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("planner")
                .description("Planner")
                .version("1.0.0")
                .url(url)
                .additionalInterfaces(List.of(new AgentInterface("JSONRPC", url)))
                .capabilities(A2ATenantMetadataTests.baseCard().capabilities())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(A2ATenantMetadataTests.baseCard().skills())
                .build(), tenantId, Map.of(
                        "mode", "M3",
                        "namespace", "seahorse-agent",
                        "group", "DEFAULT_GROUP",
                        "clusterName", clusterName));
    }

    private AgentCard tenantCardWithVersionUrl(String tenantId, String version, String url) {
        return A2ATenantMetadata.withTenant(new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("planner")
                .description("Planner")
                .version(version)
                .url(url)
                .additionalInterfaces(List.of(new AgentInterface("JSONRPC", url)))
                .capabilities(A2ATenantMetadataTests.baseCard().capabilities())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(A2ATenantMetadataTests.baseCard().skills())
                .build(), tenantId, Map.of());
    }

    private AgentCard tenantCardWithA2aExpiry(String tenantId, String url, String expiresAt) {
        AgentSkill runtimeSkill = new AgentSkill.Builder()
                .id("seahorse.agent")
                .name("planner")
                .description("Planner")
                .tags(List.of(
                        A2ATenantMetadata.A2A_TAG_PREFIX + "healthUrl=" + url.replace("/a2a", "/actuator/health"),
                        A2ATenantMetadata.A2A_TAG_PREFIX + "expiresAt=" + expiresAt))
                .build();
        return A2ATenantMetadata.withTenant(new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("planner")
                .description("Planner")
                .version("1.0.0")
                .url(url)
                .additionalInterfaces(List.of(new AgentInterface("JSONRPC", url)))
                .capabilities(A2ATenantMetadataTests.baseCard().capabilities())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(runtimeSkill))
                .build(), tenantId, Map.of());
    }
}
