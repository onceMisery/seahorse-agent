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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadFileContent;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadProcessOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生知识库文档 Web adapter。
 */
@RestController
public class SeahorseKnowledgeDocumentController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "";

    private final ObjectProvider<KnowledgeDocumentInboundPort> documentPortProvider;

    public SeahorseKnowledgeDocumentController(ObjectProvider<KnowledgeDocumentInboundPort> documentPortProvider) {
        this.documentPortProvider = documentPortProvider;
    }

    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable("kb-id") String kbId,
                                      @RequestPart("file") MultipartFile file,
                                      @ModelAttribute KnowledgeDocumentUploadRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId)
            throws IOException {
        KnowledgeDocumentUploadRequest safeRequest = request == null ? new KnowledgeDocumentUploadRequest() : request;
        KnowledgeDocumentRecord document = documentPortProvider.getIfAvailable().upload(new UploadKnowledgeDocumentCommand(
                kbId,
                new UploadFileContent(file.getInputStream(), file.getSize(),
                        file.getOriginalFilename(), file.getContentType()),
                operator(userId),
                new UploadProcessOptions(valueOrDefault(safeRequest.getProcessMode(), "pipeline"),
                        valueOrDefault(safeRequest.getPipelineId(), ""))));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, document);
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public Map<String, Object> startChunk(@PathVariable("doc-id") String docId,
                                          @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        documentPortProvider.getIfAvailable().startChunk(docId, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public Map<String, Object> delete(@PathVariable("doc-id") String docId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        documentPortProvider.getIfAvailable().delete(docId, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping("/knowledge-base/docs/{doc-id}")
    public Map<String, Object> queryById(@PathVariable("doc-id") String docId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, documentPortProvider.getIfAvailable().queryById(docId));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}")
    public Map<String, Object> update(@PathVariable("doc-id") String docId,
                                      @RequestBody KnowledgeDocumentUpdateRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        documentPortProvider.getIfAvailable().update(docId, toCommand(request, operator(userId)));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping("/knowledge-base/{kb-id}/docs")
    public Map<String, Object> page(@PathVariable("kb-id") String kbId,
                                    @ModelAttribute KnowledgeDocumentPageRequest request) {
        KnowledgeDocumentPageRequest safeRequest = request == null ? new KnowledgeDocumentPageRequest() : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, documentPortProvider.getIfAvailable().page(kbId,
                new KnowledgeDocumentPageCommand(safeRequest.currentOrDefault(), safeRequest.sizeOrDefault(),
                        safeRequest.getStatus(), safeRequest.getKeyword())));
    }

    @GetMapping("/knowledge-base/docs/search")
    public Map<String, Object> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "8") int limit) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, documentPortProvider.getIfAvailable().search(keyword, limit));
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/enable")
    public Map<String, Object> enable(@PathVariable("doc-id") String docId,
                                      @RequestParam("value") boolean enabled,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        documentPortProvider.getIfAvailable().enable(docId, enabled, operator(userId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunk-logs")
    public Map<String, Object> chunkLogs(@PathVariable("doc-id") String docId,
                                         @ModelAttribute KnowledgeDocumentPageRequest request) {
        KnowledgeDocumentPageRequest safeRequest = request == null ? new KnowledgeDocumentPageRequest() : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                documentPortProvider.getIfAvailable().chunkLogs(docId, safeRequest.currentOrDefault(), safeRequest.sizeOrDefault()));
    }

    private UpdateKnowledgeDocumentCommand toCommand(KnowledgeDocumentUpdateRequest request, String operator) {
        KnowledgeDocumentUpdateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        UpdateKnowledgeDocumentCommand command = new UpdateKnowledgeDocumentCommand();
        command.setDocName(safeRequest.getDocName());
        command.setProcessMode(safeRequest.getProcessMode());
        command.setChunkStrategy(safeRequest.getChunkStrategy());
        command.setChunkConfig(safeRequest.getChunkConfig());
        command.setPipelineId(safeRequest.getPipelineId());
        command.setSourceLocation(safeRequest.getSourceLocation());
        command.setScheduleEnabled(safeRequest.getScheduleEnabled());
        command.setScheduleCron(safeRequest.getScheduleCron());
        command.setOperator(operator);
        return command;
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
