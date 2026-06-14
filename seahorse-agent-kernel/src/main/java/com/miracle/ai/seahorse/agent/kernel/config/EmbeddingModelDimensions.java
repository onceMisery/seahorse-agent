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

package com.miracle.ai.seahorse.agent.kernel.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Resolves embedding output dimensions from the configured embedding model.
 */
public final class EmbeddingModelDimensions {

    public static final int DEFAULT_DIMENSION = 768;

    private static final Map<String, Integer> BUILT_IN_DIMENSIONS = Map.ofEntries(
            Map.entry("nomic-embed-text", 768),
            Map.entry("bge-base-zh", 768),
            Map.entry("bge-base-zh-v1.5", 768),
            Map.entry("bge-large-zh", 1024),
            Map.entry("bge-large-zh-v1.5", 1024),
            Map.entry("bge-m3", 1024),
            Map.entry("mxbai-embed-large", 1024),
            Map.entry("text-embedding-ada-002", 1536),
            Map.entry("text-embedding-3-small", 1536),
            Map.entry("text-embedding-3-large", 3072));

    private EmbeddingModelDimensions() {
    }

    public static int resolveOrThrow(String explicitDimension,
                                     String embeddingModel,
                                     String configuredModelDimensions) {
        OptionalInt explicit = parseOptionalDimension(explicitDimension, "vector dimension");
        if (explicit.isPresent()) {
            return explicit.getAsInt();
        }
        String model = normalizeModel(embeddingModel);
        if (model.isBlank()) {
            return DEFAULT_DIMENSION;
        }
        Integer configured = parseConfiguredDimensions(configuredModelDimensions).get(model);
        if (configured != null) {
            return configured;
        }
        Integer builtIn = BUILT_IN_DIMENSIONS.get(model);
        if (builtIn != null) {
            return builtIn;
        }
        throw new IllegalArgumentException("Unknown embedding model dimension for '" + embeddingModel
                + "'. Configure seahorse.agent.adapters.ai.embedding-model-dimensions="
                + model + "=<dimension> or set seahorse.agent.adapters.vector.dimension explicitly.");
    }

    public static String normalizeModel(String model) {
        String normalized = Objects.requireNonNullElse(model, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            normalized = normalized.substring(slashIndex + 1);
        }
        int tagIndex = normalized.indexOf(':');
        if (tagIndex > 0) {
            normalized = normalized.substring(0, tagIndex);
        }
        return normalized;
    }

    private static Map<String, Integer> parseConfiguredDimensions(String value) {
        String text = Objects.requireNonNullElse(value, "").trim();
        if (text.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (String entry : text.split("[,;\\r\\n]+")) {
            String item = entry.trim();
            if (item.isBlank()) {
                continue;
            }
            int separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                throw new IllegalArgumentException("Invalid embedding model dimension mapping: '" + item
                        + "'. Use model=dimension.");
            }
            String model = normalizeModel(item.substring(0, separator));
            String dimension = item.substring(separator + 1);
            dimensions.put(model, parsePositiveDimension(dimension, "embedding model dimension"));
        }
        return Map.copyOf(dimensions);
    }

    private static OptionalInt parseOptionalDimension(String value, String name) {
        String text = Objects.requireNonNullElse(value, "").trim();
        if (text.isBlank()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(parsePositiveDimension(text, name));
    }

    private static int parsePositiveDimension(String value, String name) {
        try {
            int dimension = Integer.parseInt(Objects.requireNonNullElse(value, "").trim());
            if (dimension <= 0) {
                throw new IllegalArgumentException(name + " must be positive: " + value);
            }
            return dimension;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer: " + value, ex);
        }
    }
}
