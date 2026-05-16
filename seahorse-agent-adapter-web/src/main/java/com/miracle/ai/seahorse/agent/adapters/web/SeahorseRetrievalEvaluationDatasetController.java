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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 检索评测集管理 Web adapter。
 */
@RestController
@ConditionalOnBean(RetrievalEvaluationDatasetInboundPort.class)
public class SeahorseRetrievalEvaluationDatasetController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final RetrievalEvaluationDatasetInboundPort datasetPort;

    public SeahorseRetrievalEvaluationDatasetController(RetrievalEvaluationDatasetInboundPort datasetPort) {
        this.datasetPort = Objects.requireNonNull(datasetPort, "datasetPort must not be null");
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets")
    public Map<String, Object> listDatasets(@PathVariable("kb-id") String kbId,
                                            @RequestParam(name = "includeDisabled", defaultValue = "false")
                                            boolean includeDisabled) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, datasetPort.listDatasets(kbId, includeDisabled));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public Map<String, Object> getDataset(@PathVariable("kb-id") String kbId,
                                          @PathVariable("dataset-id") String datasetId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, datasetPort.getDataset(kbId, datasetId));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets")
    public Map<String, Object> createDataset(@PathVariable("kb-id") String kbId,
                                             @RequestBody RetrievalEvaluationDatasetPayload request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, datasetPort.upsertDataset(kbId, request));
    }

    @PutMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public Map<String, Object> updateDataset(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId,
                                             @RequestBody RetrievalEvaluationDatasetPayload request) {
        RetrievalEvaluationDatasetPayload safeRequest = Objects.requireNonNull(request,
                "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                datasetPort.upsertDataset(kbId, safeRequest.withDatasetId(datasetId)));
    }

    @DeleteMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}")
    public Map<String, Object> deleteDataset(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                Map.of("deleted", datasetPort.deleteDataset(kbId, datasetId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/evaluate")
    public Map<String, Object> evaluateDataset(@PathVariable("kb-id") String kbId,
                                               @PathVariable("dataset-id") String datasetId,
                                               @RequestBody(required = false)
                                               RetrievalEvaluationDatasetRunRequest request) {
        RetrievalEvaluationDatasetRunRequest safeRequest = request == null
                ? new RetrievalEvaluationDatasetRunRequest("", 5, null)
                : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                datasetPort.evaluateDataset(kbId, safeRequest.toCommand(datasetId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/compare")
    public Map<String, Object> compareDataset(@PathVariable("kb-id") String kbId,
                                              @PathVariable("dataset-id") String datasetId,
                                              @RequestBody(required = false)
                                              RetrievalEvaluationDatasetComparisonRequest request) {
        RetrievalEvaluationDatasetComparisonRequest safeRequest = request == null
                ? new RetrievalEvaluationDatasetComparisonRequest("", 5, java.util.List.of())
                : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                datasetPort.compareDataset(kbId, safeRequest.toCommand(datasetId)));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons")
    public Map<String, Object> listComparisons(@PathVariable("kb-id") String kbId,
                                               @PathVariable("dataset-id") String datasetId,
                                               @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                datasetPort.listComparisons(kbId, datasetId, limit));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons/{comparison-id}")
    public Map<String, Object> getComparison(@PathVariable("kb-id") String kbId,
                                             @PathVariable("dataset-id") String datasetId,
                                             @PathVariable("comparison-id") String comparisonId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                datasetPort.getComparison(kbId, datasetId, comparisonId));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs")
    public Map<String, Object> listRuns(@PathVariable("kb-id") String kbId,
                                        @PathVariable("dataset-id") String datasetId,
                                        @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, datasetPort.listRuns(kbId, datasetId, limit));
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs/{run-id}")
    public Map<String, Object> getRun(@PathVariable("kb-id") String kbId,
                                      @PathVariable("dataset-id") String datasetId,
                                      @PathVariable("run-id") String runId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, datasetPort.getRun(kbId, datasetId, runId));
    }
}
