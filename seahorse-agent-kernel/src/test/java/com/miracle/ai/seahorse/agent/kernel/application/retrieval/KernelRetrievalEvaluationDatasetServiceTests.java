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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelRetrievalEvaluationDatasetServiceTests {

    @Test
    void shouldRejectDatasetWithoutCases() {
        KernelRetrievalEvaluationDatasetService service = new KernelRetrievalEvaluationDatasetService(
                new RecordingDatasetRepository(), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsertDataset("kb-1",
                        new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true, List.of())));

        assertEquals("cases must not be empty", error.getMessage());
    }

    @Test
    void shouldRejectCaseWithoutQuestion() {
        KernelRetrievalEvaluationDatasetService service = new KernelRetrievalEvaluationDatasetService(
                new RecordingDatasetRepository(), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsertDataset("kb-1",
                        new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true,
                                List.of(new RetrievalEvaluationCase("case-1", " ", List.of(),
                                        List.of(), List.of("chunk-1"), null, null)))));

        assertEquals("cases[0].question must not be blank", error.getMessage());
    }

    @Test
    void shouldRejectCaseWithoutExpectedTargets() {
        KernelRetrievalEvaluationDatasetService service = new KernelRetrievalEvaluationDatasetService(
                new RecordingDatasetRepository(), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsertDataset("kb-1",
                        new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true,
                                List.of(new RetrievalEvaluationCase("case-1", "What is Seahorse?", List.of(),
                                        List.of(), List.of(), null, null)))));

        assertEquals("cases[0] must define at least one expected kb, document, or chunk target", error.getMessage());
    }

    @Test
    void shouldRejectDuplicateCaseQuestions() {
        KernelRetrievalEvaluationDatasetService service = new KernelRetrievalEvaluationDatasetService(
                new RecordingDatasetRepository(), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsertDataset("kb-1",
                        new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true,
                                List.of(
                                        new RetrievalEvaluationCase("case-1", "What is Seahorse?", List.of(),
                                                List.of("doc-1"), List.of(), null, null),
                                        new RetrievalEvaluationCase("case-2", "  What is Seahorse?  ", List.of(),
                                                List.of("doc-2"), List.of(), null, null)))));

        assertEquals("cases[1].question duplicates cases[0].question", error.getMessage());
    }

    @Test
    void shouldRejectInvalidMetricThresholds() {
        KernelRetrievalEvaluationDatasetService service = new KernelRetrievalEvaluationDatasetService(
                new RecordingDatasetRepository(), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsertDataset("kb-1",
                        new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true,
                                List.of(new RetrievalEvaluationCase("case-1", "What is Seahorse?", List.of(),
                                        List.of("doc-1"), List.of(), null, null,
                                        List.of(), List.of(), 0.5D, 1.2D)))));

        assertEquals("cases[0].minPrecision must be between 0 and 1", error.getMessage());
    }

    @Test
    void shouldPersistDatasetWhenCasesAreEvaluable() {
        RecordingDatasetRepository repository = new RecordingDatasetRepository();
        KernelRetrievalEvaluationDatasetService service =
                new KernelRetrievalEvaluationDatasetService(repository, null);

        RetrievalEvaluationDataset dataset = service.upsertDataset("kb-1",
                new RetrievalEvaluationDatasetPayload("dataset-1", "daily", "", true,
                        List.of(new RetrievalEvaluationCase("case-1", "What is Seahorse?", List.of(),
                                List.of("doc-1"), List.of(), null, null))));

        assertEquals("dataset-1", dataset.datasetId());
        assertEquals("kb-1", repository.knowledgeBaseId);
        assertFalse(repository.payload.cases().isEmpty());
    }

    private static final class RecordingDatasetRepository implements RetrievalEvaluationDatasetRepositoryPort {
        private String knowledgeBaseId;
        private RetrievalEvaluationDatasetPayload payload;

        @Override
        public List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId,
                                                                    boolean includeDisabled) {
            return List.of();
        }

        @Override
        public Optional<RetrievalEvaluationDataset> findDataset(String knowledgeBaseId, String datasetId) {
            return Optional.empty();
        }

        @Override
        public RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId,
                                                       RetrievalEvaluationDatasetPayload payload) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.payload = payload;
            return new RetrievalEvaluationDataset(
                    payload.datasetId(),
                    knowledgeBaseId,
                    payload.name(),
                    payload.description(),
                    payload.enabled() == null || payload.enabled(),
                    payload.cases(),
                    Instant.EPOCH,
                    Instant.EPOCH);
        }

        @Override
        public boolean deleteDataset(String knowledgeBaseId, String datasetId) {
            return false;
        }
    }
}
