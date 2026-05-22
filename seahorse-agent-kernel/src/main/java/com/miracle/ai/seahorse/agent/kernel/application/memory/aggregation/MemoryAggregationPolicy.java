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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

public record MemoryAggregationPolicy(
        boolean enabled,
        long idleFlushMillis,
        int maxTurns,
        int maxTokens,
        int maxContextBlocks,
        long bufferTtlMillis,
        boolean captureOnError,
        boolean topicShiftFlushEnabled
) {

    private static final long DEFAULT_IDLE_FLUSH_MILLIS = 40_000L;
    private static final int DEFAULT_MAX_TURNS = 10;
    private static final int DEFAULT_MAX_TOKENS = 2_000;
    private static final int DEFAULT_MAX_CONTEXT_BLOCKS = 32;
    private static final long DEFAULT_BUFFER_TTL_MILLIS = 86_400_000L;

    public MemoryAggregationPolicy {
        idleFlushMillis = positiveOrDefault(idleFlushMillis, DEFAULT_IDLE_FLUSH_MILLIS);
        maxTurns = positiveOrDefault(maxTurns, DEFAULT_MAX_TURNS);
        maxTokens = positiveOrDefault(maxTokens, DEFAULT_MAX_TOKENS);
        maxContextBlocks = positiveOrDefault(maxContextBlocks, DEFAULT_MAX_CONTEXT_BLOCKS);
        bufferTtlMillis = positiveOrDefault(bufferTtlMillis, DEFAULT_BUFFER_TTL_MILLIS);
    }

    public MemoryAggregationPolicy(boolean enabled,
                                   long idleFlushMillis,
                                   int maxTurns,
                                   int maxTokens,
                                   int maxContextBlocks,
                                   long bufferTtlMillis,
                                   boolean captureOnError) {
        this(enabled, idleFlushMillis, maxTurns, maxTokens, maxContextBlocks, bufferTtlMillis, captureOnError, false);
    }

    public static MemoryAggregationPolicy defaults() {
        return new MemoryAggregationPolicy(
                false,
                DEFAULT_IDLE_FLUSH_MILLIS,
                DEFAULT_MAX_TURNS,
                DEFAULT_MAX_TOKENS,
                DEFAULT_MAX_CONTEXT_BLOCKS,
                DEFAULT_BUFFER_TTL_MILLIS,
                false,
                false);
    }

    public MemoryAggregationPolicy withEnabled(boolean enabled) {
        return new MemoryAggregationPolicy(
                enabled,
                idleFlushMillis,
                maxTurns,
                maxTokens,
                maxContextBlocks,
                bufferTtlMillis,
                captureOnError,
                topicShiftFlushEnabled);
    }

    public MemoryAggregationPolicy withCaptureOnError(boolean captureOnError) {
        return new MemoryAggregationPolicy(
                enabled,
                idleFlushMillis,
                maxTurns,
                maxTokens,
                maxContextBlocks,
                bufferTtlMillis,
                captureOnError,
                topicShiftFlushEnabled);
    }

    public MemoryAggregationPolicy withTopicShiftFlushEnabled(boolean topicShiftFlushEnabled) {
        return new MemoryAggregationPolicy(
                enabled,
                idleFlushMillis,
                maxTurns,
                maxTokens,
                maxContextBlocks,
                bufferTtlMillis,
                captureOnError,
                topicShiftFlushEnabled);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private static long positiveOrDefault(long value, long fallback) {
        return value <= 0L ? fallback : value;
    }
}
