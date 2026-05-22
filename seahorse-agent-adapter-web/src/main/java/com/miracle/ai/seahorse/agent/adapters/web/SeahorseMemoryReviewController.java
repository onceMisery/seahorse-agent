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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseMemoryReviewController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";
    private static final String DEFAULT_OPERATOR = "system";

    private final ObjectProvider<MemoryReviewInboundPort> reviewPortProvider;

    public SeahorseMemoryReviewController(ObjectProvider<MemoryReviewInboundPort> reviewPortProvider) {
        this.reviewPortProvider = reviewPortProvider;
    }

    @GetMapping("/memory-review/items")
    public Map<String, Object> page(@RequestParam(defaultValue = "default") String tenantId,
                                    @RequestParam(required = false) String userId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String targetKind,
                                    @RequestParam(required = false) String targetKey,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.page(tenantId, userId, reviewStatus(status), targetKind, targetKey, current, size));
    }

    @GetMapping("/memory-review/items/{item-id}")
    public Map<String, Object> queryById(@PathVariable("item-id") String itemId) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.queryById(itemId));
    }

    @GetMapping("/memory-review/items/{item-id}/feedback-samples")
    public Map<String, Object> feedbackSamples(@PathVariable("item-id") String itemId,
                                               @RequestParam(defaultValue = "20") int limit) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.listFeedbackSamples(itemId, limit));
    }

    @GetMapping("/memory-review/feedback-samples")
    public Map<String, Object> feedbackSamples(@RequestParam(defaultValue = "default") String tenantId,
                                               @RequestParam(required = false) String userId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String targetKind,
                                               @RequestParam(required = false) String targetKey,
                                               @RequestParam(defaultValue = "100") int limit) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.listFeedbackSamples(tenantId, userId, reviewStatus(status), targetKind, targetKey, limit));
    }

    @PostMapping("/memory-review/items/{item-id}/approve")
    public Map<String, Object> approve(@PathVariable("item-id") String itemId,
                                       @RequestBody(required = false) MemoryReviewDecisionRequest request,
                                       @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.approve(itemId, toCommand(request, userId)));
    }

    @PostMapping("/memory-review/items/{item-id}/modify")
    public Map<String, Object> modify(@PathVariable("item-id") String itemId,
                                      @RequestBody(required = false) MemoryReviewDecisionRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.modify(itemId, toCommand(request, userId)));
    }

    @PostMapping("/memory-review/items/{item-id}/reject")
    public Map<String, Object> reject(@PathVariable("item-id") String itemId,
                                      @RequestBody(required = false) MemoryReviewDecisionRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        MemoryReviewInboundPort port = reviewPortProvider.getIfAvailable();
        if (port == null) {
            return unavailable();
        }
        return ok(port.reject(itemId, toCommand(request, userId)));
    }

    private MemoryReviewDecisionCommand toCommand(MemoryReviewDecisionRequest request, String userId) {
        MemoryReviewDecisionRequest safeRequest = request == null ? new MemoryReviewDecisionRequest() : request;
        return new MemoryReviewDecisionCommand(
                operator(userId),
                Objects.requireNonNullElse(safeRequest.getComment(), ""),
                Objects.requireNonNullElse(safeRequest.getCorrectedContent(), ""),
                Objects.requireNonNullElse(safeRequest.getCorrectedMetadata(), Map.of()));
    }

    private MemoryReviewStatus reviewStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return MemoryReviewStatus.valueOf(status.trim().toUpperCase());
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }

    private Map<String, Object> unavailable() {
        return Map.of(KEY_CODE, ERROR_CODE, "message", "Service not available");
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
