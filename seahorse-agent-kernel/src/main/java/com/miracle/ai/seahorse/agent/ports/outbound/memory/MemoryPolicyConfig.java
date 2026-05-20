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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record MemoryPolicyConfig(
        double captureAcceptThreshold,
        double highValueThreshold,
        double riskRejectThreshold,
        int tokenBudget,
        boolean reviewEnabled,
        Map<String, Boolean> enabledTracks,
        int schemaFailureAlertThreshold,
        int outboxBacklogAlertThreshold,
        String greyReleaseKey
) {

    public static final double DEFAULT_CAPTURE_ACCEPT_THRESHOLD = 0.40D;
    public static final double DEFAULT_HIGH_VALUE_THRESHOLD = 0.75D;
    public static final double DEFAULT_RISK_REJECT_THRESHOLD = 0.70D;
    public static final int DEFAULT_TOKEN_BUDGET = 2400;

    public MemoryPolicyConfig {
        captureAcceptThreshold = ratioOrDefault(captureAcceptThreshold, DEFAULT_CAPTURE_ACCEPT_THRESHOLD);
        highValueThreshold = ratioOrDefault(highValueThreshold, DEFAULT_HIGH_VALUE_THRESHOLD);
        riskRejectThreshold = ratioOrDefault(riskRejectThreshold, DEFAULT_RISK_REJECT_THRESHOLD);
        tokenBudget = tokenBudget > 0 ? tokenBudget : DEFAULT_TOKEN_BUDGET;
        enabledTracks = Map.copyOf(Objects.requireNonNullElseGet(enabledTracks, MemoryPolicyConfig::defaultTracks));
        schemaFailureAlertThreshold = Math.max(0, schemaFailureAlertThreshold);
        outboxBacklogAlertThreshold = Math.max(0, outboxBacklogAlertThreshold);
        greyReleaseKey = Objects.requireNonNullElse(greyReleaseKey, "");
    }

    public static MemoryPolicyConfig defaults() {
        return new MemoryPolicyConfig(
                DEFAULT_CAPTURE_ACCEPT_THRESHOLD,
                DEFAULT_HIGH_VALUE_THRESHOLD,
                DEFAULT_RISK_REJECT_THRESHOLD,
                DEFAULT_TOKEN_BUDGET,
                false,
                defaultTracks(),
                0,
                0,
                "");
    }

    public MemoryPolicyConfig withCaptureAcceptThreshold(double value) {
        return new MemoryPolicyConfig(value, highValueThreshold, riskRejectThreshold, tokenBudget,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold,
                greyReleaseKey);
    }

    public MemoryPolicyConfig withHighValueThreshold(double value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, value, riskRejectThreshold, tokenBudget,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold,
                greyReleaseKey);
    }

    public MemoryPolicyConfig withRiskRejectThreshold(double value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, value, tokenBudget,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold,
                greyReleaseKey);
    }

    public MemoryPolicyConfig withTokenBudget(int value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, value,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold,
                greyReleaseKey);
    }

    public MemoryPolicyConfig withReviewEnabled(boolean value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, tokenBudget,
                value, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold, greyReleaseKey);
    }

    public MemoryPolicyConfig withTrackEnabled(String track, boolean enabled) {
        Map<String, Boolean> tracks = new LinkedHashMap<>(enabledTracks);
        if (track != null && !track.isBlank()) {
            tracks.put(track.trim(), enabled);
        }
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, tokenBudget,
                reviewEnabled, tracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold, greyReleaseKey);
    }

    public MemoryPolicyConfig withSchemaFailureAlertThreshold(int value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, tokenBudget,
                reviewEnabled, enabledTracks, value, outboxBacklogAlertThreshold, greyReleaseKey);
    }

    public MemoryPolicyConfig withOutboxBacklogAlertThreshold(int value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, tokenBudget,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, value, greyReleaseKey);
    }

    public MemoryPolicyConfig withGreyReleaseKey(String value) {
        return new MemoryPolicyConfig(captureAcceptThreshold, highValueThreshold, riskRejectThreshold, tokenBudget,
                reviewEnabled, enabledTracks, schemaFailureAlertThreshold, outboxBacklogAlertThreshold, value);
    }

    private static Map<String, Boolean> defaultTracks() {
        Map<String, Boolean> tracks = new LinkedHashMap<>();
        tracks.put("correction", true);
        tracks.put("profile", true);
        tracks.put("episodic", true);
        tracks.put("business_doc", true);
        tracks.put("short_window", true);
        return tracks;
    }

    private static double ratioOrDefault(double value, double fallback) {
        return value >= 0D && value <= 1D ? value : fallback;
    }
}
