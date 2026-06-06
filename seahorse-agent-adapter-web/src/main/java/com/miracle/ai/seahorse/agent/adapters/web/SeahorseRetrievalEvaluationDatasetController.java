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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Retrieval evaluation dataset web adapter.
 */
@RestController
public class SeahorseRetrievalEvaluationDatasetController {

    private final ObjectProvider<RetrievalEvaluationDatasetInboundPort> datasetPortProvider;

    public SeahorseRetrievalEvaluationDatasetController(
            ObjectProvider<RetrievalEvaluationDatasetInboundPort> datasetPortProvider) {
        this.datasetPortProvider = datasetPortProvider;
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets")
    public ApiResponse<Object> listDatasets(@PathVariable("kb-id") String kbId,
                                            @RequestParam(name = "includeDisabled", defaultValue = "false")
                                            boolean includeDisabled) {
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.listDatasets(kbId, includeDisabled));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public ApiResponse<Object> getDataset(@PathVariable("kb-id") String kbId,
                                          @PathVariable("dataset-id") String datasetId) {
        return ApiResponses.requireServiceOrError(datasetPortProvider, port -> port.getDataset(kbId, datasetId));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets")
    public ApiResponse<Object> createDataset(@PathVariable("kb-id") String kbId,
                                             @RequestBody RetrievalEvaluationDatasetPayload request) {
        return ApiResponses.requireServiceOrError(datasetPortProvider, port -> port.upsertDataset(kbId, request));
    }

    @PutMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public ApiResponse<Object> updateDataset(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId,
                                             @RequestBody RetrievalEvaluationDatasetPayload request) {
        RetrievalEvaluationDatasetPayload safeRequest = Objects.requireNonNull(request,
                "request must not be null");
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.upsertDataset(kbId, safeRequest.withDatasetId(datasetId)));
    }

    @DeleteMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public ApiResponse<Object> deleteDataset(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId) {
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> Map.of("deleted", port.deleteDataset(kbId, datasetId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/evaluate")
    public ApiResponse<Object> evaluateDataset(@PathVariable("kb-id") String kbId,
                                               @PathVariable("dataset-id") String datasetId,
                                               @RequestBody(required = false)
                                               RetrievalEvaluationDatasetRunRequest request) {
        RetrievalEvaluationDatasetRunRequest safeRequest = request == null
                ? new RetrievalEvaluationDatasetRunRequest("", 5, null)
                : request;
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.evaluateDataset(kbId, safeRequest.toCommand(datasetId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/compare")
    public ApiResponse<Object> compareDataset(@PathVariable("kb-id") String kbId,
                                              @PathVariable("dataset-id") String datasetId,
                                              @RequestBody(required = false)
                                              RetrievalEvaluationDatasetComparisonRequest request) {
        RetrievalEvaluationDatasetComparisonRequest safeRequest = request == null
                ? new RetrievalEvaluationDatasetComparisonRequest("", 5, List.of())
                : request;
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.compareDataset(kbId, safeRequest.toCommand(datasetId)));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons")
    public ApiResponse<Object> listComparisons(@PathVariable("kb-id") String kbId,
                                               @PathVariable("dataset-id") String datasetId,
                                               @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.listComparisons(kbId, datasetId, limit));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons/{comparison-id}")
    public ApiResponse<Object> getComparison(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId,
                                             @PathVariable("comparison-id") String comparisonId) {
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.getComparison(kbId, datasetId, comparisonId));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs")
    public ApiResponse<Object> listRuns(@PathVariable("kb-id") String kbId,
                                        @PathVariable("dataset-id") String datasetId,
                                        @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(datasetPortProvider,
                port -> port.listRuns(kbId, datasetId, limit));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs/{run-id}")
    public ApiResponse<Object> getRun(@PathVariable("kb-id") String kbId,
                                      @PathVariable("dataset-id") String datasetId,
                                      @PathVariable("run-id") String runId) {
        return ApiResponses.requireServiceOrError(datasetPortProvider, port -> port.getRun(kbId, datasetId, runId));
    }
}
