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

package com.miracle.ai.seahorse.agent.kernel.application.agent.routing;

import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRoutingPolicyConfigDrivenTests {

    @Test
    void selectsConfiguredTierModelInsteadOfHardcodedDefaults() {
        ModelRoutingPolicy policy = new ModelRoutingPolicy(config(), available("claude-opus", "claude-haiku"), null);

        ModelRoutingPolicy.ModelSelection selection = policy.selectWithFallback("HIGH", 10.0d, 2_000, "user-1");

        assertEquals("claude-opus", selection.modelId());
        assertNull(selection.downgradeReason());
    }

    @Test
    void unknownTierFallsBackToConfiguredLowTier() {
        ModelRoutingPolicy policy = new ModelRoutingPolicy(config(), available("claude-haiku"), null);

        ModelRoutingPolicy.ModelSelection selection = policy.selectWithFallback("EXPERIMENTAL", 10.0d, 2_000, "user-1");

        assertEquals("claude-haiku", selection.modelId());
        assertTrue(selection.isDowngraded());
    }

    @Test
    void unavailablePrimaryModelWalksConfiguredFallbackChain() {
        ModelRoutingPolicy policy = new ModelRoutingPolicy(config(), available("claude-sonnet"), null);

        ModelRoutingPolicy.ModelSelection selection = policy.selectWithFallback("HIGH", 10.0d, 2_000, "user-1");

        assertEquals("claude-sonnet", selection.modelId());
        assertTrue(selection.downgradeReason().contains("high"));
        assertTrue(selection.downgradeReason().contains("medium"));
    }

    @Test
    void largeContextUsesConfiguredLargeContextTier() {
        ModelRoutingPolicy policy = new ModelRoutingPolicy(config(), available("gemini-1.5-pro"), null);

        ModelRoutingPolicy.ModelSelection selection = policy.selectWithFallback("LOW", 10.0d, 80_000, "user-1");

        assertEquals("gemini-1.5-pro", selection.modelId());
    }

    @Test
    void highCostConcurrencyRejectionReturnsQueuedSelection() {
        RecordingRateLimiter rateLimiter = new RecordingRateLimiter(RateLimitDecision.rejected(
                Duration.ofSeconds(30), "busy"));
        ModelRoutingPolicy policy = new ModelRoutingPolicy(config(), available("claude-opus"), rateLimiter);

        ModelRoutingPolicy.ModelSelection selection = policy.selectWithFallback("HIGH", 10.0d, 2_000, "user-42");

        assertNull(selection.modelId());
        assertTrue(selection.isDowngraded());
        assertEquals("high_cost_concurrency", rateLimiter.resource);
        assertEquals("user-42", rateLimiter.subject);
        assertEquals(2, rateLimiter.permits);
        assertEquals(Duration.ofSeconds(60), rateLimiter.ttl);
    }

    private static ModelRoutingProperties config() {
        return new ModelRoutingProperties(
                Map.of(
                        "low", new ModelRoutingProperties.Tier("claude-haiku", 32_000, 0.00025d),
                        "medium", new ModelRoutingProperties.Tier("claude-sonnet", 64_000, 0.003d),
                        "high", new ModelRoutingProperties.Tier("claude-opus", 64_000, 0.015d),
                        "large_context", new ModelRoutingProperties.Tier("gemini-1.5-pro", 1_000_000, 0.00125d)
                ),
                List.of(
                        new ModelRoutingProperties.Fallback("high", 2),
                        new ModelRoutingProperties.Fallback("medium", 1),
                        new ModelRoutingProperties.Fallback("low", 0)
                ),
                2,
                Duration.ofSeconds(60),
                0.01d);
    }

    private static ModelProviderPort available(String... modelIds) {
        Set<String> available = Set.of(modelIds);
        return new ModelProviderPort() {
            @Override
            public boolean available(String modelId) {
                return available.contains(modelId);
            }

            @Override
            public List<String> listModels(String capability) {
                return List.copyOf(available);
            }
        };
    }

    private static final class RecordingRateLimiter implements RateLimiterPort {
        private final RateLimitDecision decision;
        private String resource;
        private String subject;
        private int permits;
        private Duration ttl;

        private RecordingRateLimiter(RateLimitDecision decision) {
            this.decision = decision;
        }

        @Override
        public RateLimitDecision tryAcquire(String resource, String subject, int permits, Duration ttl) {
            this.resource = resource;
            this.subject = subject;
            this.permits = permits;
            this.ttl = ttl;
            return decision;
        }
    }
}
