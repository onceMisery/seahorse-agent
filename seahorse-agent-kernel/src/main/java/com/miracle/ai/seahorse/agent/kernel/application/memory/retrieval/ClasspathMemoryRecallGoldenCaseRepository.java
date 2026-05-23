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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenCase;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseProfile;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseRepositoryPort;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Loads golden recall case profiles from JSON resources on the classpath.
 *
 * <p>Profiles live at {@code <root>/<profileName>.json} where {@code <root>} defaults to
 * {@code seahorse-agent/memory-recall-golden} but can be overridden so multiple bundles can
 * coexist. Each JSON document is expected to look like:
 *
 * <pre>
 * {
 *   "name": "smoke",
 *   "topK": 5,
 *   "cases": [
 *     {
 *       "caseId": "case-1",
 *       "userId": "user-1",
 *       "conversationId": "conv-1",
 *       "query": "...",
 *       "expectedMemoryIds": ["mem-1", "mem-2"]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>The repository is forgiving: a missing file yields {@link Optional#empty()}, and malformed
 * documents are reported with a wrapped {@link IOException} so operators can spot bad bundles
 * without crashing the harness during startup.
 */
public class ClasspathMemoryRecallGoldenCaseRepository implements MemoryRecallGoldenCaseRepositoryPort {

    public static final String DEFAULT_ROOT = "seahorse-agent/memory-recall-golden";
    private static final String INDEX_FILE = "_index.json";
    private static final String JSON_EXTENSION = ".json";

    private final ObjectMapper objectMapper;
    private final ClassLoader classLoader;
    private final String resourceRoot;

    public ClasspathMemoryRecallGoldenCaseRepository() {
        this(new ObjectMapper(), Thread.currentThread().getContextClassLoader(), DEFAULT_ROOT);
    }

    public ClasspathMemoryRecallGoldenCaseRepository(ObjectMapper objectMapper,
                                                     ClassLoader classLoader,
                                                     String resourceRoot) {
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.classLoader = Objects.requireNonNullElseGet(classLoader,
                () -> Thread.currentThread().getContextClassLoader());
        String trimmedRoot = Objects.requireNonNullElse(resourceRoot, DEFAULT_ROOT).trim();
        this.resourceRoot = trimmedRoot.isBlank() ? DEFAULT_ROOT : stripTrailingSlash(trimmedRoot);
    }

    @Override
    public Optional<MemoryRecallGoldenCaseProfile> findByName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return Optional.empty();
        }
        String resourcePath = resourceRoot + "/" + profileName + JSON_EXTENSION;
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(parseProfile(stream, profileName));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to read golden case profile resource: " + resourcePath, ex);
        }
    }

    @Override
    public List<String> listProfileNames() {
        Set<String> names = new LinkedHashSet<>();
        String indexPath = resourceRoot + "/" + INDEX_FILE;
        try (InputStream stream = classLoader.getResourceAsStream(indexPath)) {
            if (stream != null) {
                List<String> indexed = objectMapper.readValue(stream, new TypeReference<>() {
                });
                if (indexed != null) {
                    indexed.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(name -> !name.isBlank())
                            .forEach(names::add);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read golden case index: " + indexPath, ex);
        }
        return List.copyOf(names);
    }

    private MemoryRecallGoldenCaseProfile parseProfile(InputStream stream, String fallbackName) throws IOException {
        Map<String, Object> raw = objectMapper.readValue(stream, new TypeReference<>() {
        });
        if (raw == null) {
            return new MemoryRecallGoldenCaseProfile(fallbackName, 0, List.of());
        }
        Object rawName = raw.get("name");
        String name = rawName == null ? fallbackName : rawName.toString();
        int topK = toInt(raw.get("topK"));
        Object rawCases = raw.get("cases");
        List<MemoryRecallGoldenCase> cases = decodeCases(rawCases);
        return new MemoryRecallGoldenCaseProfile(name, topK, cases);
    }

    private List<MemoryRecallGoldenCase> decodeCases(Object rawCases) {
        if (!(rawCases instanceof List<?> list)) {
            return List.of();
        }
        List<MemoryRecallGoldenCase> decoded = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> typed = toStringKeyedMap(map);
            decoded.add(new MemoryRecallGoldenCase(
                    asString(typed.get("caseId")),
                    asString(typed.get("userId")),
                    asString(typed.get("conversationId")),
                    asString(typed.get("query")),
                    decodeStringList(typed.get("expectedMemoryIds"))));
        }
        return Collections.unmodifiableList(decoded);
    }

    private Map<String, Object> toStringKeyedMap(Map<?, ?> map) {
        Map<String, Object> typed = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                typed.put(key.toString(), value);
            }
        });
        return typed;
    }

    private List<String> decodeStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry != null) {
                values.add(entry.toString());
            }
        }
        return values;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
