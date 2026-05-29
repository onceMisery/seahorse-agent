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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Selects the model tier for research and web-agent tasks.
 */
public class ModelRoutingPolicy {

    private static final String CAPABILITY_CHAT = "chat";
    private static final String HIGH_COST_RESOURCE = "high_cost_concurrency";

    private final ModelRoutingProperties properties;
    private final ModelProviderPort modelProviderPort;
    private final RateLimiterPort rateLimiterPort;

    public ModelRoutingPolicy() {
        this(ModelRoutingProperties.defaults(), null, null);
    }

    public ModelRoutingPolicy(ModelRoutingProperties properties,
                              ModelProviderPort modelProviderPort,
                              RateLimiterPort rateLimiterPort) {
        this.properties = Objects.requireNonNullElseGet(properties, ModelRoutingProperties::defaults);
        this.modelProviderPort = modelProviderPort;
        this.rateLimiterPort = rateLimiterPort;
    }

    /**
     * Backward-compatible entry point. Uses the configured policy and skips user-specific concurrency keys.
     */
    public ModelSelection selectModel(String templateModelTier, double remainingQuota, int contextTokens) {
        return selectWithFallback(templateModelTier, remainingQuota, contextTokens, "");
    }

    public ModelSelection selectWithFallback(String templateModelTier,
                                             double remainingQuota,
                                             int contextTokens,
                                             String subject) {
        String requestedTier = requestedTier(templateModelTier);
        ModelSelection queued = rejectIfHighCostQueueFull(requestedTier, subject);
        if (queued != null) {
            return queued;
        }

        ModelSelection direct = selectDirect(requestedTier, remainingQuota, contextTokens);
        if (direct != null) {
            return direct;
        }

        return selectFallback(requestedTier);
    }

    private ModelSelection selectDirect(String requestedTier, double remainingQuota, int contextTokens) {
        ModelRoutingProperties.Tier tier = properties.tier(requestedTier);
        if (tier == null) {
            return availableSelection(ModelRoutingProperties.LOW,
                    "unknown tier " + requestedTier + ", downgraded to low");
        }
        if (contextTokens > 0 && tier.maxContextTokens() > 0 && contextTokens > tier.maxContextTokens()) {
            ModelSelection largeContext = availableSelection(ModelRoutingProperties.LARGE_CONTEXT, null);
            if (largeContext != null) {
                return largeContext;
            }
        }
        if (remainingQuota <= 0 && isHighCost(tier)) {
            return availableSelection(ModelRoutingProperties.LOW, "quota exhausted, downgraded to low");
        }
        return availableSelection(requestedTier, null);
    }

    private ModelSelection selectFallback(String requestedTier) {
        List<String> attempted = new ArrayList<>();
        attempted.add(requestedTier);
        for (ModelRoutingProperties.Fallback fallback : properties.fallbackChain()) {
            String fallbackTier = fallback.tier();
            if (attempted.contains(fallbackTier)) {
                continue;
            }
            ModelSelection selection = availableSelection(
                    fallbackTier,
                    "tier " + requestedTier + " unavailable, downgraded to " + fallbackTier);
            if (selection != null) {
                return selection;
            }
            attempted.add(fallbackTier);
        }
        return new ModelSelection(null, "no available model in fallback chain: " + String.join(" -> ", attempted));
    }

    private ModelSelection availableSelection(String tierId, String downgradeReason) {
        ModelRoutingProperties.Tier tier = properties.tier(tierId);
        if (tier == null || tier.modelId().isBlank()) {
            return null;
        }
        if (!providerKnowsModels() || modelProviderPort.available(tier.modelId())) {
            return new ModelSelection(tier.modelId(), downgradeReason);
        }
        return null;
    }

    private boolean providerKnowsModels() {
        return modelProviderPort != null && !modelProviderPort.listModels(CAPABILITY_CHAT).isEmpty();
    }

    private ModelSelection rejectIfHighCostQueueFull(String requestedTier, String subject) {
        if (rateLimiterPort == null || properties.highCostConcurrencyLimit() <= 0) {
            return null;
        }
        ModelRoutingProperties.Tier tier = properties.tier(requestedTier);
        if (tier == null || !isHighCost(tier)) {
            return null;
        }
        RateLimitDecision decision = rateLimiterPort.tryAcquire(
                HIGH_COST_RESOURCE,
                Objects.requireNonNullElse(subject, ""),
                properties.highCostConcurrencyLimit(),
                properties.highCostConcurrencyWindow());
        if (decision.allowed()) {
            return null;
        }
        return new ModelSelection(null, "high cost model queue is full, please retry later");
    }

    private boolean isHighCost(ModelRoutingProperties.Tier tier) {
        return tier.costPer1kInput() >= properties.highCostThreshold();
    }

    private String requestedTier(String templateModelTier) {
        String normalized = ModelRoutingProperties.normalizeTierId(templateModelTier);
        if ("largecontext".equals(normalized)) {
            return ModelRoutingProperties.LARGE_CONTEXT;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public record ModelSelection(String modelId, String downgradeReason) {

        public boolean isDowngraded() {
            return downgradeReason != null;
        }
    }
}
