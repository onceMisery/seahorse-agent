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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import java.util.Locale;
import java.util.Objects;

/**
 * Runtime budget policy for the final model request envelope.
 */
public record ModelContextEnvelopeOptions(
        Mode mode,
        int defaultOutputReserveTokens,
        int safetyBufferTokens,
        int conservativeSafetyBufferTokens,
        int maxRuntimeContextTokens,
        int maxRuntimeContextItems,
        int estimatedCharsPerToken,
        int messageOverheadTokens,
        int toolOverheadTokens) {

    private static final int DEFAULT_OUTPUT_RESERVE_TOKENS = 8_192;
    private static final int DEFAULT_SAFETY_BUFFER_TOKENS = 2_048;
    private static final int DEFAULT_CONSERVATIVE_SAFETY_BUFFER_TOKENS = 4_096;
    private static final int DEFAULT_MAX_RUNTIME_CONTEXT_TOKENS = 8_192;
    private static final int DEFAULT_MAX_RUNTIME_CONTEXT_ITEMS = 100;
    private static final int DEFAULT_ESTIMATED_CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_MESSAGE_OVERHEAD_TOKENS = 6;
    private static final int DEFAULT_TOOL_OVERHEAD_TOKENS = 12;

    public ModelContextEnvelopeOptions {
        mode = Objects.requireNonNullElse(mode, Mode.ENFORCE);
        defaultOutputReserveTokens = positiveOrDefault(
                defaultOutputReserveTokens, DEFAULT_OUTPUT_RESERVE_TOKENS);
        safetyBufferTokens = positiveOrDefault(safetyBufferTokens, DEFAULT_SAFETY_BUFFER_TOKENS);
        conservativeSafetyBufferTokens = Math.max(
                positiveOrDefault(conservativeSafetyBufferTokens, DEFAULT_CONSERVATIVE_SAFETY_BUFFER_TOKENS),
                safetyBufferTokens);
        maxRuntimeContextTokens = positiveOrDefault(
                maxRuntimeContextTokens, DEFAULT_MAX_RUNTIME_CONTEXT_TOKENS);
        maxRuntimeContextItems = positiveOrDefault(
                maxRuntimeContextItems, DEFAULT_MAX_RUNTIME_CONTEXT_ITEMS);
        estimatedCharsPerToken = positiveOrDefault(
                estimatedCharsPerToken, DEFAULT_ESTIMATED_CHARS_PER_TOKEN);
        messageOverheadTokens = positiveOrDefault(
                messageOverheadTokens, DEFAULT_MESSAGE_OVERHEAD_TOKENS);
        toolOverheadTokens = positiveOrDefault(toolOverheadTokens, DEFAULT_TOOL_OVERHEAD_TOKENS);
    }

    public static ModelContextEnvelopeOptions defaults() {
        return new ModelContextEnvelopeOptions(
                Mode.ENFORCE,
                DEFAULT_OUTPUT_RESERVE_TOKENS,
                DEFAULT_SAFETY_BUFFER_TOKENS,
                DEFAULT_CONSERVATIVE_SAFETY_BUFFER_TOKENS,
                DEFAULT_MAX_RUNTIME_CONTEXT_TOKENS,
                DEFAULT_MAX_RUNTIME_CONTEXT_ITEMS,
                DEFAULT_ESTIMATED_CHARS_PER_TOKEN,
                DEFAULT_MESSAGE_OVERHEAD_TOKENS,
                DEFAULT_TOOL_OVERHEAD_TOKENS);
    }

    public ModelContextEnvelopeOptions withMode(Mode newMode) {
        return new ModelContextEnvelopeOptions(
                newMode,
                defaultOutputReserveTokens,
                safetyBufferTokens,
                conservativeSafetyBufferTokens,
                maxRuntimeContextTokens,
                maxRuntimeContextItems,
                estimatedCharsPerToken,
                messageOverheadTokens,
                toolOverheadTokens);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    public enum Mode {
        DISABLED,
        OBSERVE,
        ENFORCE;

        public static Mode parse(String value) {
            String normalized = Objects.requireNonNullElse(value, "")
                    .trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_');
            if (normalized.isBlank()) {
                return ENFORCE;
            }
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return ENFORCE;
            }
        }
    }
}
