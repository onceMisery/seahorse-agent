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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the context window for the exact model selected for a request.
 */
@FunctionalInterface
public interface ModelContextWindowPort {

    ModelContextWindow resolve(String modelId);

    default String resolveModelId(String modelId) {
        return Objects.requireNonNullElse(modelId, "").trim();
    }

    static ModelContextWindowPort fixed(int tokens, String source) {
        ModelContextWindow window = new ModelContextWindow(tokens, source);
        return ignored -> window;
    }

    static ModelContextWindowPort configured(Map<String, Integer> modelWindows,
                                             int defaultTokens,
                                             String sourcePrefix) {
        String prefix = Objects.requireNonNullElse(sourcePrefix, "configured").trim();
        Map<String, Integer> normalized = new LinkedHashMap<>();
        Objects.requireNonNullElse(modelWindows, Map.<String, Integer>of()).forEach((modelId, tokens) -> {
            String key = normalizeModelId(modelId);
            if (!key.isBlank() && tokens != null && tokens > 0) {
                normalized.put(key, tokens);
            }
        });
        Map<String, Integer> immutable = Map.copyOf(normalized);
        return modelId -> {
            String normalizedModelId = normalizeModelId(modelId);
            Integer configuredTokens = immutable.get(normalizedModelId);
            if (configuredTokens != null) {
                return new ModelContextWindow(configuredTokens, prefix + ":model:" + normalizedModelId);
            }
            return new ModelContextWindow(defaultTokens, prefix + ":default");
        };
    }

    static ModelContextWindowPort strictConfigured(Map<String, Integer> modelWindows, String sourcePrefix) {
        return strictConfigured(modelWindows, sourcePrefix, "");
    }

    static ModelContextWindowPort strictConfigured(
            Map<String, Integer> modelWindows, String sourcePrefix, String defaultModelId) {
        return strictConfigured(modelWindows, sourcePrefix, defaultModelId, null);
    }

    static ModelContextWindowPort strictConfigured(
            Map<String, Integer> modelWindows,
            String sourcePrefix,
            String defaultModelId,
            Integer defaultModelSafeProfileTokens) {
        String prefix = Objects.requireNonNullElse(sourcePrefix, "configured").trim();
        String configuredDefaultModelId = Objects.requireNonNullElse(defaultModelId, "").trim();
        int safeProfileTokens = defaultModelSafeProfileTokens == null
                ? 0
                : Math.max(0, defaultModelSafeProfileTokens);
        Map<String, Integer> normalized = new LinkedHashMap<>();
        Objects.requireNonNullElse(modelWindows, Map.<String, Integer>of()).forEach((modelId, tokens) -> {
            String key = normalizeModelId(modelId);
            if (!key.isBlank() && tokens != null && tokens > 0) {
                normalized.put(key, tokens);
            }
        });
        Map<String, Integer> immutable = Map.copyOf(normalized);
        return new ModelContextWindowPort() {
            @Override
            public ModelContextWindow resolve(String modelId) {
                String resolvedModelId = resolveModelId(modelId);
                Integer configuredTokens = immutable.get(normalizeModelId(resolvedModelId));
                if (configuredTokens != null) {
                    return new ModelContextWindow(
                            configuredTokens, prefix + ":model:" + normalizeModelId(resolvedModelId));
                }
                if (safeProfileTokens > 0
                        && !configuredDefaultModelId.isBlank()
                        && normalizeModelId(configuredDefaultModelId).equals(normalizeModelId(resolvedModelId))) {
                    return new ModelContextWindow(
                            safeProfileTokens, prefix + ":safe-profile:default-model");
                }
                return ModelContextWindow.unknown(prefix + ":unconfigured-model");
            }

            @Override
            public String resolveModelId(String modelId) {
                String requested = Objects.requireNonNullElse(modelId, "").trim();
                return requested.isBlank() ? configuredDefaultModelId : requested;
            }
        };
    }

    private static String normalizeModelId(String modelId) {
        return Objects.requireNonNullElse(modelId, "").trim().toLowerCase(Locale.ROOT);
    }
}
