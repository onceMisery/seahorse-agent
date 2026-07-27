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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContextEnvelopeCalibratorTests {

    private static final Clock FIRST_CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldShareBoundedCalibrationAcrossInstancesAndDecayIt() {
        MemoryCache cache = new MemoryCache();
        ModelContextEnvelopeCalibrator first = new ModelContextEnvelopeCalibrator(cache, FIRST_CLOCK);
        first.record(evidence(1_000, 32_768), new ChatTokenUsage(1_200, 10));

        int firstValue = first.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1");
        ModelContextEnvelopeCalibrator second = new ModelContextEnvelopeCalibrator(cache, FIRST_CLOCK);
        int sharedValue = second.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1");
        Clock later = Clock.fixed(
                FIRST_CLOCK.instant().plus(ModelContextEnvelopeCalibrator.CALIBRATION_HALF_LIFE),
                ZoneOffset.UTC);
        int decayedValue = new ModelContextEnvelopeCalibrator(cache, later).additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1");

        assertEquals(firstValue, sharedValue);
        assertTrue(firstValue > 0);
        assertEquals(firstValue / 2, decayedValue);
        assertEquals(ModelContextEnvelopeCalibrator.CALIBRATION_TTL, cache.lastTtl);
    }

    @Test
    void shouldRejectProviderUsageOutsideTheTrustedContextWindow() {
        MemoryCache cache = new MemoryCache();
        ModelContextEnvelopeCalibrator calibrator = new ModelContextEnvelopeCalibrator(cache, FIRST_CLOCK);

        calibrator.record(evidence(1_000, 32_768), new ChatTokenUsage(99_999, 10));

        assertEquals(0, calibrator.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1"));
        assertTrue(cache.values.isEmpty());
    }

    @Test
    void shouldNotRefreshLocalDecayEpochOnReads() {
        MemoryCache cache = new MemoryCache();
        MutableClock clock = new MutableClock(FIRST_CLOCK.instant());
        ModelContextEnvelopeCalibrator calibrator = new ModelContextEnvelopeCalibrator(cache, clock);
        calibrator.record(evidence(1_000, 32_768), new ChatTokenUsage(1_200, 10));
        int initial = calibrator.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1");

        clock.advance(Duration.ofHours(3));
        assertEquals(initial, calibrator.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1"));
        clock.advance(Duration.ofHours(3));

        assertEquals(initial / 2, calibrator.additionalSafetyTokens(
                "test-model", TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK, "test-v1"));
    }

    @Test
    void shouldPreserveStoredEpochWhenLowerCandidateLoses() {
        MemoryCache cache = new MemoryCache();
        String key = "calibration";
        long observedAt = FIRST_CLOCK.millis();
        cache.set(key, "1000:" + observedAt, Duration.ofHours(24));

        long merged = cache.mergeDecayingMaximum(
                key,
                100,
                16_384,
                observedAt + Duration.ofHours(3).toMillis(),
                Duration.ofHours(6),
                Duration.ofHours(24));

        assertEquals(1_000L, merged);
        assertEquals("1000:" + observedAt, cache.values.get(key));
    }

    private static ModelContextEnvelopeEvidence evidence(long selectedInputTokens, int contextWindow) {
        return new ModelContextEnvelopeEvidence(
                "test-model",
                "sha256:test",
                "TEST",
                ModelContextEnvelopeOptions.Mode.ENFORCE,
                TokenCounterPort.EstimatorMode.CONSERVATIVE_FALLBACK,
                "test-v1",
                "LOW",
                contextWindow,
                "test",
                1_024,
                256,
                contextWindow - 1_280L,
                selectedInputTokens,
                contextWindow - 1_280L - selectedInputTokens,
                selectedInputTokens,
                contextWindow - 1_280L - selectedInputTokens,
                1,
                0,
                Map.of(),
                List.of(),
                List.of(),
                "OK",
                null,
                null,
                null);
    }

    private static final class MemoryCache implements KeyValueCachePort {
        private final Map<String, String> values = new LinkedHashMap<>();
        private Duration lastTtl;
        private int mergeCalls;

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            lastTtl = ttl;
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        public long mergeDecayingMaximum(
                String key,
                long candidate,
                long maximum,
                long observedAtMillis,
                Duration halfLife,
                Duration ttl) {
            mergeCalls++;
            lastTtl = ttl;
            String encoded = values.get(key);
            long current = 0L;
            if (encoded != null) {
                String[] parts = encoded.split(":", -1);
                long value = Long.parseLong(parts[0]);
                long storedAt = Long.parseLong(parts[1]);
                long periods = Math.max(0L, observedAtMillis - storedAt) / halfLife.toMillis();
                current = periods >= Long.SIZE - 1 ? 0L : value >> (int) periods;
            }
            if (encoded == null || candidate > current) {
                set(key, candidate + ":" + observedAtMillis, ttl);
                return candidate;
            }
            return current;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
