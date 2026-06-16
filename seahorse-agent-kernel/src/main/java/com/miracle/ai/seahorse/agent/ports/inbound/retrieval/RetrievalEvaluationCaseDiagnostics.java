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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Case-level diagnostics used by evaluation and comparison reports.
 */
public record RetrievalEvaluationCaseDiagnostics(
        @JsonProperty("expectedChunkIds") List<String> expectedChunkIds,
        @JsonProperty("expectedDocIds") List<String> expectedDocIds,
        @JsonProperty("expectedKbIds") List<String> expectedKbIds,
        @JsonProperty("missingExpectedChunkIds") List<String> missingExpectedChunkIds,
        @JsonProperty("missingExpectedDocIds") List<String> missingExpectedDocIds,
        @JsonProperty("missingExpectedKbIds") List<String> missingExpectedKbIds,
        @JsonProperty("retrievedChunks") List<RetrievalEvaluationChunkDiagnostic> retrievedChunks,
        @JsonProperty("traceId") String traceId
) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationCaseDiagnostics {
        expectedChunkIds = copy(expectedChunkIds);
        expectedDocIds = copy(expectedDocIds);
        expectedKbIds = copy(expectedKbIds);
        missingExpectedChunkIds = copy(missingExpectedChunkIds);
        missingExpectedDocIds = copy(missingExpectedDocIds);
        missingExpectedKbIds = copy(missingExpectedKbIds);
        retrievedChunks = List.copyOf(Objects.requireNonNullElse(retrievedChunks, List.of()));
        traceId = Objects.requireNonNullElse(traceId, "");
    }

    public RetrievalEvaluationCaseDiagnostics(List<String> expectedChunkIds,
                                              List<String> expectedDocIds,
                                              List<String> expectedKbIds,
                                              List<String> missingExpectedChunkIds,
                                              List<String> missingExpectedDocIds,
                                              List<String> missingExpectedKbIds,
                                              List<RetrievalEvaluationChunkDiagnostic> retrievedChunks) {
        this(expectedChunkIds, expectedDocIds, expectedKbIds, missingExpectedChunkIds, missingExpectedDocIds,
                missingExpectedKbIds, retrievedChunks, "");
    }

    public static RetrievalEvaluationCaseDiagnostics empty() {
        return new RetrievalEvaluationCaseDiagnostics(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }

    private static List<String> copy(List<String> values) {
        return List.copyOf(Objects.requireNonNullElse(values, List.of()));
    }
}
