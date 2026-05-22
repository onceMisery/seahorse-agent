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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.util.Map;
import java.util.Objects;

public record MemoryFusionPolicy(
        int rrfK,
        double decayLambda,
        int finalTopK,
        boolean timeDecayEnabled,
        long channelTimeoutMillis,
        Map<String, Double> channelWeights
) {

    public static final int DEFAULT_RRF_K = 60;
    public static final double DEFAULT_DECAY_LAMBDA = 0.05D;
    public static final int DEFAULT_FINAL_TOP_K = 8;
    public static final int DEFAULT_CHANNEL_TOP_K = 20;
    public static final long DEFAULT_CHANNEL_TIMEOUT_MILLIS = 50L;

    public MemoryFusionPolicy {
        rrfK = rrfK > 0 ? rrfK : DEFAULT_RRF_K;
        decayLambda = decayLambda >= 0D ? decayLambda : DEFAULT_DECAY_LAMBDA;
        finalTopK = finalTopK > 0 ? finalTopK : DEFAULT_FINAL_TOP_K;
        channelTimeoutMillis = channelTimeoutMillis > 0L ? channelTimeoutMillis : DEFAULT_CHANNEL_TIMEOUT_MILLIS;
        channelWeights = Map.copyOf(Objects.requireNonNullElse(channelWeights, Map.of()));
    }

    public MemoryFusionPolicy(int rrfK,
                              double decayLambda,
                              int finalTopK,
                              boolean timeDecayEnabled) {
        this(rrfK, decayLambda, finalTopK, timeDecayEnabled, DEFAULT_CHANNEL_TIMEOUT_MILLIS, Map.of());
    }

    public static MemoryFusionPolicy defaults() {
        return new MemoryFusionPolicy(
                DEFAULT_RRF_K,
                DEFAULT_DECAY_LAMBDA,
                DEFAULT_FINAL_TOP_K,
                true,
                DEFAULT_CHANNEL_TIMEOUT_MILLIS,
                Map.of());
    }

    public MemoryFusionPolicy withFinalTopK(int newFinalTopK) {
        return new MemoryFusionPolicy(
                rrfK, decayLambda, newFinalTopK, timeDecayEnabled, channelTimeoutMillis, channelWeights);
    }

    public MemoryFusionPolicy withTimeDecayEnabled(boolean enabled) {
        return new MemoryFusionPolicy(rrfK, decayLambda, finalTopK, enabled, channelTimeoutMillis, channelWeights);
    }

    public MemoryFusionPolicy withDecayLambda(double newDecayLambda) {
        return new MemoryFusionPolicy(
                rrfK, newDecayLambda, finalTopK, timeDecayEnabled, channelTimeoutMillis, channelWeights);
    }

    public MemoryFusionPolicy withChannelTimeoutMillis(long newChannelTimeoutMillis) {
        return new MemoryFusionPolicy(
                rrfK, decayLambda, finalTopK, timeDecayEnabled, newChannelTimeoutMillis, channelWeights);
    }
}
