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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据人工复核管理 Web adapter。
 *
 * <p>控制器只调用入站端口；审核状态、写回文档元数据和转隔离由 kernel 服务统一处理。
 */
@RestController
@ConditionalOnBean(MetadataReviewInboundPort.class)
public class SeahorseMetadataReviewController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "system";

    private final MetadataReviewInboundPort reviewPort;

    public SeahorseMetadataReviewController(MetadataReviewInboundPort reviewPort) {
        this.reviewPort = Objects.requireNonNull(reviewPort, "reviewPort must not be null");
    }

    @GetMapping("/metadata-review/items")
    public Map<String, Object> page(@RequestParam String tenantId,
                                    @RequestParam(required = false) String kbId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String reasonCode,
                                    @RequestParam(required = false) String documentId,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.page(tenantId, kbId, reviewStatus(status), reasonCode, documentId, current, size));
    }

    @GetMapping("/metadata-review/items/{item-id}")
    public Map<String, Object> queryById(@PathVariable("item-id") String itemId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, reviewPort.queryById(itemId));
    }

    @GetMapping("/metadata-review/items/{item-id}/audits")
    public Map<String, Object> listAudits(@PathVariable("item-id") String itemId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, reviewPort.listAudits(itemId));
    }

    @PostMapping("/metadata-review/items/{item-id}/approve")
    public Map<String, Object> approve(@PathVariable("item-id") String itemId,
                                       @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                       @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.approve(itemId, toCommand(request, userId)));
    }

    @PostMapping("/metadata-review/items/{item-id}/correct")
    public Map<String, Object> correct(@PathVariable("item-id") String itemId,
                                       @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                       @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.correct(itemId, toCommand(request, userId)));
    }

    @PostMapping("/metadata-review/items/{item-id}/ignore-field")
    public Map<String, Object> ignoreField(@PathVariable("item-id") String itemId,
                                           @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                           @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.ignoreField(itemId, toCommand(request, userId)));
    }

    @PostMapping("/metadata-review/items/{item-id}/re-extract")
    public Map<String, Object> reExtract(@PathVariable("item-id") String itemId,
                                         @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                         @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.reExtract(itemId, toCommand(request, userId)));
    }

    @PostMapping("/metadata-review/items/{item-id}/reject")
    public Map<String, Object> reject(@PathVariable("item-id") String itemId,
                                      @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.reject(itemId, toCommand(request, userId)));
    }

    @PostMapping("/metadata-review/items/{item-id}/quarantine")
    public Map<String, Object> quarantine(@PathVariable("item-id") String itemId,
                                          @RequestBody(required = false) MetadataReviewDecisionRequest request,
                                          @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                reviewPort.quarantine(itemId, toCommand(request, userId)));
    }

    private MetadataReviewDecisionCommand toCommand(MetadataReviewDecisionRequest request, String userId) {
        MetadataReviewDecisionRequest safeRequest = request == null ? new MetadataReviewDecisionRequest() : request;
        return new MetadataReviewDecisionCommand(
                operator(userId),
                Objects.requireNonNullElse(safeRequest.getComment(), ""),
                Objects.requireNonNullElse(safeRequest.getCorrectedMetadata(), Map.of()),
                Objects.requireNonNullElse(safeRequest.getIgnoredFields(), List.of()),
                Objects.requireNonNullElse(safeRequest.getExtractorVersion(), ""),
                Objects.requireNonNullElse(safeRequest.getPipelineId(), ""),
                Objects.requireNonNullElse(safeRequest.getLlmExtractorVersion(), ""),
                Objects.requireNonNullElse(safeRequest.getLlmPromptVersion(), ""));
    }

    private MetadataReviewStatus reviewStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return MetadataReviewStatus.valueOf(status.trim().toUpperCase());
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
