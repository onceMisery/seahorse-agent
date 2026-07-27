/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Keeps bounded, prompt-free and expiring feedback for tokenizer underestimation. */
final class ModelContextEnvelopeCalibrator {

    static final Duration CALIBRATION_TTL = Duration.ofHours(24);
    static final Duration CALIBRATION_HALF_LIFE = Duration.ofHours(6);
    static final int MAX_ADDITIONAL_SAFETY_TOKENS = 16_384;

    private static final int MINIMUM_ERROR_MARGIN_TOKENS = 64;
    private static final String CACHE_KEY_PREFIX = "model-context-envelope:calibration:v1:";

    private final ConcurrentMap<CalibrationKey, CalibrationValue> localValues = new ConcurrentHashMap<>();
    private final KeyValueCachePort sharedCache;
    private final Clock clock;

    ModelContextEnvelopeCalibrator() {
        this(null, Clock.systemUTC());
    }

    ModelContextEnvelopeCalibrator(KeyValueCachePort sharedCache) {
        this(sharedCache, Clock.systemUTC());
    }

    ModelContextEnvelopeCalibrator(KeyValueCachePort sharedCache, Clock clock) {
        this.sharedCache = sharedCache;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    int additionalSafetyTokens(
            String modelId,
            TokenCounterPort.EstimatorMode estimatorMode,
            String estimatorVersion) {
        CalibrationKey key = CalibrationKey.of(modelId, estimatorMode, estimatorVersion);
        long now = clock.millis();
        CalibrationValue local = localValues.get(key);
        CalibrationValue shared = readShared(key).orElse(null);
        CalibrationValue selected = stronger(local, shared, now);
        int tokens = decayedTokens(selected, now);
        if (tokens <= 0) {
            localValues.remove(key);
            return 0;
        }
        if (selected != null && selected != local) {
            localValues.put(key, selected);
        }
        return tokens;
    }

    void record(ModelContextEnvelopeEvidence evidence, ChatTokenUsage usage) {
        if (!trusted(evidence, usage)) {
            return;
        }
        long underestimatedTokens = usage.inputTokens() - evidence.selectedInputTokens();
        if (underestimatedTokens <= 0L) {
            return;
        }
        long maximumTrustedDelta = Math.max(
                MINIMUM_ERROR_MARGIN_TOKENS,
                Math.min(MAX_ADDITIONAL_SAFETY_TOKENS, evidence.contextWindow() / 8L));
        if (underestimatedTokens > Math.max(maximumTrustedDelta * 4L, evidence.contextWindow() / 2L)) {
            return;
        }
        long margin = Math.max(MINIMUM_ERROR_MARGIN_TOKENS, underestimatedTokens / 10L);
        int calibratedSafety = toPositiveInt(Math.min(
                maximumTrustedDelta,
                saturatedAdd(underestimatedTokens, margin)));
        CalibrationKey key = CalibrationKey.of(
                evidence.modelId(), evidence.estimatorMode(), evidence.estimatorVersion());
        long now = clock.millis();
        CalibrationValue candidate = new CalibrationValue(calibratedSafety, now);
        CalibrationValue shared = mergeShared(key, calibratedSafety, now).orElse(candidate);
        localValues.compute(key, (ignored, current) -> stronger(
                stronger(candidate, current, now), shared, now));
    }

    private boolean trusted(ModelContextEnvelopeEvidence evidence, ChatTokenUsage usage) {
        return evidence != null
                && usage != null
                && evidence.contextWindow() > 0
                && evidence.selectedInputTokens() > 0L
                && usage.inputTokens() > 0L
                && usage.inputTokens() <= evidence.contextWindow()
                && usage.outputTokens() >= 0L;
    }

    private int decayedTokens(CalibrationValue value, long now) {
        if (value == null || value.tokens() <= 0) {
            return 0;
        }
        long age = Math.max(0L, now - value.updatedAtMillis());
        long periods = age / CALIBRATION_HALF_LIFE.toMillis();
        if (periods >= 31L) {
            return 0;
        }
        return value.tokens() >> (int) periods;
    }

    private Optional<CalibrationValue> readShared(CalibrationKey key) {
        if (sharedCache == null) {
            return Optional.empty();
        }
        try {
            return sharedCache.get(cacheKey(key)).flatMap(this::parseValue);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<CalibrationValue> mergeShared(CalibrationKey key, int candidate, long now) {
        if (sharedCache == null) {
            return Optional.empty();
        }
        try {
            long merged = sharedCache.mergeDecayingMaximum(
                    cacheKey(key),
                    candidate,
                    MAX_ADDITIONAL_SAFETY_TOKENS,
                    now,
                    CALIBRATION_HALF_LIFE,
                    CALIBRATION_TTL);
            if (merged <= 0L || merged > MAX_ADDITIONAL_SAFETY_TOKENS) {
                return Optional.empty();
            }
            return readShared(key).or(() -> Optional.of(new CalibrationValue((int) merged, now)));
        } catch (RuntimeException ignored) {
            // Static safety buffers and the bounded local value remain active.
            return Optional.empty();
        }
    }

    private CalibrationValue stronger(CalibrationValue first, CalibrationValue second, long now) {
        int firstTokens = decayedTokens(first, now);
        int secondTokens = decayedTokens(second, now);
        return secondTokens > firstTokens ? second : first;
    }

    private Optional<CalibrationValue> parseValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int tokens = Integer.parseInt(parts[0]);
            long updatedAt = Long.parseLong(parts[1]);
            if (tokens <= 0 || tokens > MAX_ADDITIONAL_SAFETY_TOKENS || updatedAt <= 0L) {
                return Optional.empty();
            }
            return Optional.of(new CalibrationValue(tokens, updatedAt));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String cacheKey(CalibrationKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return CACHE_KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private int toPositiveInt(long value) {
        return (int) Math.min(MAX_ADDITIONAL_SAFETY_TOKENS, Math.max(0L, value));
    }

    private record CalibrationValue(int tokens, long updatedAtMillis) {
    }

    private record CalibrationKey(
            String modelId,
            TokenCounterPort.EstimatorMode estimatorMode,
            String estimatorVersion) {

        private static CalibrationKey of(
                String modelId,
                TokenCounterPort.EstimatorMode estimatorMode,
                String estimatorVersion) {
            return new CalibrationKey(
                    Objects.requireNonNullElse(modelId, "").trim().toLowerCase(Locale.ROOT),
                    Objects.requireNonNullElse(
                            estimatorMode, TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK),
                    Objects.requireNonNullElse(estimatorVersion, "unknown").trim());
        }
    }
}
