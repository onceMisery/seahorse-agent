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
 * Per-chunk retrieval evaluation diagnostic for report drill-down.
 */
public record RetrievalEvaluationChunkDiagnostic(
        @JsonProperty("rank") int rank,
        @JsonProperty("chunkId") String chunkId,
        @JsonProperty("docId") String docId,
        @JsonProperty("kbId") String kbId,
        @JsonProperty("score") Double score,
        @JsonProperty("matchedExpectedTargets") List<String> matchedExpectedTargets,
        @JsonProperty("negativeHit") boolean negativeHit
) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationChunkDiagnostic {
        rank = Math.max(0, rank);
        chunkId = Objects.requireNonNullElse(chunkId, "");
        docId = Objects.requireNonNullElse(docId, "");
        kbId = Objects.requireNonNullElse(kbId, "");
        matchedExpectedTargets = List.copyOf(Objects.requireNonNullElse(matchedExpectedTargets, List.of()));
    }
}
