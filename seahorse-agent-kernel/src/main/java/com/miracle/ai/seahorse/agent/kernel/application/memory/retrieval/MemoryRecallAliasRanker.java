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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MemoryRecallAliasRanker {

    private static final String FILTER_CANONICAL_ENTITY_ID = "memoryAliasCanonicalEntityId";
    private static final String FILTER_CANONICAL_NAME = "memoryAliasCanonicalName";
    private static final String FILTER_ENTITY_TYPE = "memoryAliasEntityType";
    private static final String METADATA_CANONICAL_ENTITY_ID = "canonicalEntityId";
    private static final String METADATA_CANONICAL_NAME = "canonicalName";
    private static final String METADATA_CANONICAL_ENTITY_TYPE = "canonicalEntityType";
    private static final String METADATA_ENTITY_TYPE = "entityType";
    private static final String METADATA_ALIAS_FILTER_MATCHED = "aliasFilterMatched";
    private static final String METADATA_ALIAS_ORIGINAL_RANK = "aliasOriginalRank";
    private static final String METADATA_ALIAS_MATCHED_FIELDS = "aliasMatchedFields";

    private MemoryRecallAliasRanker() {
    }

    static List<MemoryRecallCandidate> rank(List<MemoryRecallCandidate> candidates, MemoryRecallRequest request) {
        if (candidates == null || candidates.isEmpty() || request == null || request.filters().isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        AliasFilter filter = AliasFilter.from(request.filters());
        if (!filter.hasIdentity()) {
            return candidates;
        }
        List<RankedCandidate> ranked = new ArrayList<>();
        int sequence = 0;
        for (MemoryRecallCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            sequence++;
            AliasMatch match = match(candidate.metadata(), filter);
            MemoryRecallCandidate enriched = match.promoted()
                    ? enrich(candidate, match.matchedFields())
                    : candidate;
            ranked.add(new RankedCandidate(enriched, match.score(), sequence));
        }
        ranked.sort(Comparator.comparingInt(RankedCandidate::aliasScore).reversed()
                .thenComparingInt(RankedCandidate::originalSequence));
        List<MemoryRecallCandidate> result = new ArrayList<>();
        int rank = 0;
        for (RankedCandidate candidate : ranked) {
            rank++;
            result.add(candidate.candidate().withRankAndScore(rank, candidate.candidate().rawScore()));
        }
        return List.copyOf(result);
    }

    private static AliasMatch match(Map<String, Object> metadata, AliasFilter filter) {
        Map<String, Object> safeMetadata = Objects.requireNonNullElse(metadata, Map.of());
        List<String> matchedFields = new ArrayList<>();
        int score = 0;
        if (matches(safeMetadata.get(METADATA_CANONICAL_ENTITY_ID), filter.canonicalEntityId())) {
            matchedFields.add(METADATA_CANONICAL_ENTITY_ID);
            score++;
        }
        if (matches(safeMetadata.get(METADATA_CANONICAL_NAME), filter.canonicalName())) {
            matchedFields.add(METADATA_CANONICAL_NAME);
            score++;
        }
        if (score > 0 && entityTypeCompatible(safeMetadata, filter.entityType())) {
            if (entityTypeMatches(safeMetadata, filter.entityType())) {
                matchedFields.add(METADATA_ENTITY_TYPE);
            }
            return new AliasMatch(score, matchedFields);
        }
        return AliasMatch.none();
    }

    private static boolean entityTypeCompatible(Map<String, Object> metadata, String expected) {
        if (text(expected).isBlank()) {
            return true;
        }
        String actual = firstText(metadata.get(METADATA_ENTITY_TYPE), metadata.get(METADATA_CANONICAL_ENTITY_TYPE));
        return actual.isBlank() || matches(actual, expected);
    }

    private static boolean entityTypeMatches(Map<String, Object> metadata, String expected) {
        return matches(metadata.get(METADATA_ENTITY_TYPE), expected)
                || matches(metadata.get(METADATA_CANONICAL_ENTITY_TYPE), expected);
    }

    private static boolean matches(Object actual, String expected) {
        String normalizedExpected = text(expected);
        if (normalizedExpected.isBlank()) {
            return false;
        }
        String normalizedActual = text(actual);
        return !normalizedActual.isBlank() && normalizedActual.equalsIgnoreCase(normalizedExpected);
    }

    private static MemoryRecallCandidate enrich(MemoryRecallCandidate candidate, List<String> matchedFields) {
        Map<String, Object> metadata = new LinkedHashMap<>(candidate.metadata());
        // Alias filters are ranking hints from canonical resolution, not hard recall filters.
        metadata.put(METADATA_ALIAS_FILTER_MATCHED, true);
        metadata.put(METADATA_ALIAS_ORIGINAL_RANK, candidate.rank());
        metadata.put(METADATA_ALIAS_MATCHED_FIELDS, List.copyOf(matchedFields));
        return candidate.withMetadata(metadata);
    }

    private static String firstText(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : Objects.toString(value, "").trim();
    }

    private record AliasFilter(String canonicalEntityId, String canonicalName, String entityType) {

        static AliasFilter from(Map<String, Object> filters) {
            Map<String, Object> safeFilters = Objects.requireNonNullElse(filters, Map.of());
            return new AliasFilter(
                    text(safeFilters.get(FILTER_CANONICAL_ENTITY_ID)),
                    text(safeFilters.get(FILTER_CANONICAL_NAME)),
                    text(safeFilters.get(FILTER_ENTITY_TYPE)));
        }

        boolean hasIdentity() {
            return !canonicalEntityId.isBlank() || !canonicalName.isBlank();
        }
    }

    private record AliasMatch(int score, List<String> matchedFields) {

        static AliasMatch none() {
            return new AliasMatch(0, List.of());
        }

        boolean promoted() {
            return score > 0;
        }
    }

    private record RankedCandidate(MemoryRecallCandidate candidate, int aliasScore, int originalSequence) {
    }
}
