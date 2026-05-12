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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeChunkCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeChunkCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Seahorse 原生知识库 Chunk 管理 Web adapter。
 */
@RestController
@ConditionalOnBean(KnowledgeChunkInboundPort.class)
public class SeahorseKnowledgeChunkController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "";

    private final KnowledgeChunkInboundPort chunkPort;

    public SeahorseKnowledgeChunkController(KnowledgeChunkInboundPort chunkPort) {
        this.chunkPort = Objects.requireNonNull(chunkPort, "chunkPort must not be null");
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Map<String, Object> page(@PathVariable("doc-id") String docId,
                                    @RequestParam(required = false, defaultValue = "1") long current,
                                    @RequestParam(required = false, defaultValue = "10") long size,
                                    @RequestParam(required = false) Boolean enabled) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                chunkPort.page(docId, new KnowledgeChunkPageCommand(current, size, enabled)));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Map<String, Object> create(@PathVariable("doc-id") String docId,
                                      @RequestBody KnowledgeChunkCreateRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        KnowledgeChunkCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, chunkPort.create(docId,
                new CreateKnowledgeChunkCommand(safeRequest.chunkId(),
                        safeRequest.content(), safeRequest.index(), operator(userId))));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Map<String, Object> update(@PathVariable("doc-id") String docId,
                                      @PathVariable("chunk-id") String chunkId,
                                      @RequestBody KnowledgeChunkUpdateRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        KnowledgeChunkUpdateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        chunkPort.update(docId, chunkId, new UpdateKnowledgeChunkCommand(safeRequest.content(), operator(userId)));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Map<String, Object> delete(@PathVariable("doc-id") String docId,
                                      @PathVariable("chunk-id") String chunkId) {
        chunkPort.delete(docId, chunkId);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable")
    public Map<String, Object> enable(@PathVariable("doc-id") String docId,
                                      @PathVariable("chunk-id") String chunkId,
                                      @RequestParam("value") boolean enabled,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        chunkPort.enable(docId, chunkId, enabled, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/batch-enable")
    public Map<String, Object> batchEnable(@PathVariable("doc-id") String docId,
                                           @RequestParam("value") boolean enabled,
                                           @RequestBody(required = false) KnowledgeChunkBatchRequest request,
                                           @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        List<String> chunkIds = request == null ? List.of() : request.chunkIds();
        chunkPort.batchEnable(docId, chunkIds, enabled, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
