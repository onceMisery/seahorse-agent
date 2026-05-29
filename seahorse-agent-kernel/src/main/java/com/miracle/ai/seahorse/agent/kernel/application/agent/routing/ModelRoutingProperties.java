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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure kernel contract for model routing configuration.
 */
public record ModelRoutingProperties(
        Map<String, Tier> tiers,
        List<Fallback> fallbackChain,
        int highCostConcurrencyLimit,
        Duration highCostConcurrencyWindow,
        double highCostThreshold
) {

    public static final String LOW = "low";
    public static final String MEDIUM = "medium";
    public static final String HIGH = "high";
    public static final String LARGE_CONTEXT = "large_context";

    public ModelRoutingProperties {
        tiers = normalizeTiers(tiers);
        fallbackChain = Objects.requireNonNullElseGet(fallbackChain, ModelRoutingProperties::defaultFallbackChain)
                .stream()
                .filter(Objects::nonNull)
                .map(fallback -> new Fallback(normalizeTierId(fallback.tier()), Math.max(0, fallback.retryCount())))
                .toList();
        highCostConcurrencyLimit = Math.max(0, highCostConcurrencyLimit);
        highCostConcurrencyWindow = Objects.requireNonNullElse(highCostConcurrencyWindow, Duration.ofMinutes(1));
        highCostThreshold = highCostThreshold <= 0 ? 0.002d : highCostThreshold;
    }

    public static ModelRoutingProperties defaults() {
        return new ModelRoutingProperties(
                defaultTiers(),
                defaultFallbackChain(),
                0,
                Duration.ofMinutes(1),
                0.002d);
    }

    public Tier tier(String tierId) {
        return tiers.get(normalizeTierId(tierId));
    }

    public static String normalizeTierId(String tierId) {
        String normalized = Objects.requireNonNullElse(tierId, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        return normalized.isBlank() ? LOW : normalized;
    }

    private static Map<String, Tier> normalizeTiers(Map<String, Tier> source) {
        Map<String, Tier> normalized = new LinkedHashMap<>(defaultTiers());
        if (source != null) {
            source.forEach((key, value) -> {
                if (value != null) {
                    normalized.put(normalizeTierId(key), value);
                }
            });
        }
        return Map.copyOf(normalized);
    }

    private static Map<String, Tier> defaultTiers() {
        return Map.of(
                LOW, new Tier("gpt-4o-mini", 128_000, 0.00015d),
                MEDIUM, new Tier("gpt-4o-mini", 128_000, 0.00015d),
                HIGH, new Tier("gpt-4o", 128_000, 0.0025d),
                LARGE_CONTEXT, new Tier("gpt-4o", 200_000, 0.0025d)
        );
    }

    private static List<Fallback> defaultFallbackChain() {
        return List.of(new Fallback(HIGH, 2), new Fallback(MEDIUM, 1), new Fallback(LOW, 0));
    }

    public record Tier(String modelId, int maxContextTokens, double costPer1kInput) {

        public Tier {
            modelId = Objects.requireNonNullElse(modelId, "").trim();
            maxContextTokens = Math.max(0, maxContextTokens);
            costPer1kInput = Math.max(0.0d, costPer1kInput);
        }
    }

    public record Fallback(String tier, int retryCount) {

        public Fallback {
            tier = normalizeTierId(tier);
            retryCount = Math.max(0, retryCount);
        }
    }
}
