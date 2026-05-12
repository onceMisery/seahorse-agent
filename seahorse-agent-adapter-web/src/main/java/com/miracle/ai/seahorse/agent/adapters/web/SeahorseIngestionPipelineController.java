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

import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelinePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineNodePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生入库 Pipeline 管理 Web adapter。
 */
@RestController
@ConditionalOnBean(IngestionPipelineInboundPort.class)
public class SeahorseIngestionPipelineController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "";

    private final IngestionPipelineInboundPort pipelinePort;

    public SeahorseIngestionPipelineController(IngestionPipelineInboundPort pipelinePort) {
        this.pipelinePort = Objects.requireNonNull(pipelinePort, "pipelinePort must not be null");
    }

    @PostMapping("/ingestion/pipelines")
    public Map<String, Object> create(@RequestBody IngestionPipelineRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, pipelinePort.create(toPayload(request, operator(userId))));
    }

    @PutMapping("/ingestion/pipelines/{id}")
    public Map<String, Object> update(@PathVariable String id,
                                      @RequestBody IngestionPipelineRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, pipelinePort.update(id, toPayload(request, operator(userId))));
    }

    @GetMapping("/ingestion/pipelines/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, pipelinePort.get(id));
    }

    @GetMapping("/ingestion/pipelines")
    public Map<String, Object> page(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                    @RequestParam(value = "keyword", required = false) String keyword) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, pipelinePort.page(pageNo, pageSize, keyword));
    }

    @DeleteMapping("/ingestion/pipelines/{id}")
    public Map<String, Object> delete(@PathVariable String id,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        pipelinePort.delete(id, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private IngestionPipelinePayload toPayload(IngestionPipelineRequest request, String operator) {
        IngestionPipelineRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        List<IngestionPipelineNodePayload> nodes = safeRequest.getNodes() == null
                ? List.of()
                : safeRequest.getNodes().stream().map(this::toNodePayload).toList();
        return new IngestionPipelinePayload(
                safeRequest.getName(), safeRequest.getDescription(), nodes, operator);
    }

    private IngestionPipelineNodePayload toNodePayload(IngestionPipelineNodeRequest node) {
        IngestionPipelineNodeRequest safeNode = Objects.requireNonNull(node, "node must not be null");
        return new IngestionPipelineNodePayload(
                safeNode.getNodeId(),
                safeNode.getNodeType(),
                safeNode.getNextNodeId(),
                safeNode.getSettings(),
                safeNode.getCondition());
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
