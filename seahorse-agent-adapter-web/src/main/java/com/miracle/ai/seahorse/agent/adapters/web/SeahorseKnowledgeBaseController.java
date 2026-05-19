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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeBaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBasePageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeBaseCommand;
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

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生知识库管理 Web adapter。
 *
 * <p>路 知识库管理接口兼容，内部只依赖入站端口，不再调用旧
 * {@code KnowledgeBaseService}。
 */
@RestController
public class SeahorseKnowledgeBaseController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "";

    private final ObjectProvider<KnowledgeBaseInboundPort> knowledgeBasePortProvider;

    public SeahorseKnowledgeBaseController(ObjectProvider<KnowledgeBaseInboundPort> knowledgeBasePortProvider) {
        this.knowledgeBasePortProvider = knowledgeBasePortProvider;
    }

    @PostMapping("/knowledge-base")
    public Map<String, Object> create(@RequestBody KnowledgeBaseCreateRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        KnowledgeBaseCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String id = knowledgeBasePortProvider.getIfAvailable().create(new CreateKnowledgeBaseCommand(
                safeRequest.name(), safeRequest.embeddingModel(), safeRequest.collectionName(), operator(userId)));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping("/knowledge-base/{kb-id}")
    public Map<String, Object> update(@PathVariable("kb-id") String kbId,
                                      @RequestBody KnowledgeBaseUpdateRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        KnowledgeBaseUpdateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        knowledgeBasePortProvider.getIfAvailable().update(kbId, new UpdateKnowledgeBaseCommand(
                safeRequest.name(), safeRequest.embeddingModel(), operator(userId)));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/knowledge-base/{kb-id}")
    public Map<String, Object> delete(@PathVariable("kb-id") String kbId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        knowledgeBasePortProvider.getIfAvailable().delete(kbId, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping("/knowledge-base/{kb-id}")
    public Map<String, Object> queryById(@PathVariable("kb-id") String kbId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, knowledgeBasePortProvider.getIfAvailable().queryById(kbId));
    }

    @GetMapping("/knowledge-base")
    public Map<String, Object> page(@RequestParam(required = false, defaultValue = "1") long current,
                                    @RequestParam(required = false, defaultValue = "10") long size,
                                    @RequestParam(required = false) String name) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                knowledgeBasePortProvider.getIfAvailable().page(new KnowledgeBasePageCommand(current, size, name)));
    }

    @GetMapping("/knowledge-base/chunk-strategies")
    public Map<String, Object> listChunkStrategies() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, knowledgeBasePortProvider.getIfAvailable().listChunkStrategies());
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
