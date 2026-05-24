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
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Objects;

/**
 * Seahorse 原生入库 Pipeline 管理 Web adapter。
 */
@RestController
public class SeahorseIngestionPipelineController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String DEFAULT_OPERATOR = "";

    private final ObjectProvider<IngestionPipelineInboundPort> pipelinePortProvider;

    public SeahorseIngestionPipelineController(ObjectProvider<IngestionPipelineInboundPort> pipelinePortProvider) {
        this.pipelinePortProvider = pipelinePortProvider;
    }

    @PostMapping("/ingestion/pipelines")
    public ApiResponse<Object> create(@RequestBody IngestionPipelineRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return ApiResponses.requireServiceOrError(pipelinePortProvider,
                port -> port.create(toPayload(request, operator(userId))));
    }

    @PutMapping("/ingestion/pipelines/{id}")
    public ApiResponse<Object> update(@PathVariable String id,
                                      @RequestBody IngestionPipelineRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return ApiResponses.requireServiceOrError(pipelinePortProvider,
                port -> port.update(id, toPayload(request, operator(userId))));
    }

    @GetMapping("/ingestion/pipelines/{id}")
    public ApiResponse<Object> get(@PathVariable String id) {
        return ApiResponses.requireServiceOrError(pipelinePortProvider, port -> port.get(id));
    }

    @GetMapping("/ingestion/pipelines")
    public ApiResponse<Object> page(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                    @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponses.requireServiceOrError(pipelinePortProvider, port -> port.page(pageNo, pageSize, keyword));
    }

    @DeleteMapping("/ingestion/pipelines/{id}")
    public ApiResponse<Object> delete(@PathVariable String id,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return ApiResponses.requireServiceOrError(pipelinePortProvider, port -> {
            port.delete(id, operator(userId));
            return null;
        });
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
