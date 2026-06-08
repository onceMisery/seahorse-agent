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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import com.miracle.ai.seahorse.agent.kernel.application.agent.routing.ModelRoutingProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "seahorse-agent.routing")
public class RoutingProperties {

    private Map<String, Tier> tiers = defaultTiers();
    private List<Fallback> fallbackChain = List.of(
            new Fallback(ModelRoutingProperties.HIGH, 2),
            new Fallback(ModelRoutingProperties.MEDIUM, 1),
            new Fallback(ModelRoutingProperties.LOW, 0));
    private int highCostConcurrencyLimit = 0;
    private Duration highCostConcurrencyWindow = Duration.ofMinutes(1);
    private double highCostThreshold = 0.002d;

    public Map<String, Tier> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, Tier> tiers) {
        this.tiers = tiers;
    }

    public List<Fallback> getFallbackChain() {
        return fallbackChain;
    }

    public void setFallbackChain(List<Fallback> fallbackChain) {
        this.fallbackChain = fallbackChain;
    }

    public int getHighCostConcurrencyLimit() {
        return highCostConcurrencyLimit;
    }

    public void setHighCostConcurrencyLimit(int highCostConcurrencyLimit) {
        this.highCostConcurrencyLimit = highCostConcurrencyLimit;
    }

    public Duration getHighCostConcurrencyWindow() {
        return highCostConcurrencyWindow;
    }

    public void setHighCostConcurrencyWindow(Duration highCostConcurrencyWindow) {
        this.highCostConcurrencyWindow = highCostConcurrencyWindow;
    }

    public double getHighCostThreshold() {
        return highCostThreshold;
    }

    public void setHighCostThreshold(double highCostThreshold) {
        this.highCostThreshold = highCostThreshold;
    }

    public ModelRoutingProperties toKernelProperties() {
        Map<String, ModelRoutingProperties.Tier> kernelTiers = new LinkedHashMap<>();
        if (tiers != null) {
            tiers.forEach((key, value) -> {
                if (value != null) {
                    kernelTiers.put(key, value.toKernelTier());
                }
            });
        }
        List<ModelRoutingProperties.Fallback> kernelFallbacks = fallbackChain == null
                ? List.of()
                : fallbackChain.stream()
                .map(Fallback::toKernelFallback)
                .toList();
        return new ModelRoutingProperties(
                kernelTiers,
                kernelFallbacks,
                highCostConcurrencyLimit,
                highCostConcurrencyWindow,
                highCostThreshold);
    }

    private static Map<String, Tier> defaultTiers() {
        Map<String, Tier> defaults = new LinkedHashMap<>();
        defaults.put(ModelRoutingProperties.LOW, new Tier("gpt-4o-mini", 128_000, 0.00015d));
        defaults.put(ModelRoutingProperties.MEDIUM, new Tier("gpt-4o-mini", 128_000, 0.00015d));
        defaults.put(ModelRoutingProperties.HIGH, new Tier("gpt-4o", 128_000, 0.0025d));
        defaults.put(ModelRoutingProperties.LARGE_CONTEXT, new Tier("gpt-4o", 200_000, 0.0025d));
        return defaults;
    }

    public static class Tier {
        private String modelId = "";
        private int maxContextTokens = 0;
        private double costPer1kInput = 0.0d;

        public Tier() {
        }

        public Tier(String modelId, int maxContextTokens, double costPer1kInput) {
            this.modelId = modelId;
            this.maxContextTokens = maxContextTokens;
            this.costPer1kInput = costPer1kInput;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public int getMaxContextTokens() {
            return maxContextTokens;
        }

        public void setMaxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
        }

        public double getCostPer1kInput() {
            return costPer1kInput;
        }

        public void setCostPer1kInput(double costPer1kInput) {
            this.costPer1kInput = costPer1kInput;
        }

        private ModelRoutingProperties.Tier toKernelTier() {
            return new ModelRoutingProperties.Tier(modelId, maxContextTokens, costPer1kInput);
        }
    }

    public static class Fallback {
        private String tier = ModelRoutingProperties.LOW;
        private int retryCount = 0;

        public Fallback() {
        }

        public Fallback(String tier, int retryCount) {
            this.tier = tier;
            this.retryCount = retryCount;
        }

        public String getTier() {
            return tier;
        }

        public void setTier(String tier) {
            this.tier = tier;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        private ModelRoutingProperties.Fallback toKernelFallback() {
            return new ModelRoutingProperties.Fallback(tier, retryCount);
        }
    }
}
