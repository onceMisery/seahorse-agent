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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.UserMemoryPrivacySetting;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.UserMemoryPrivacyInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseUserMemoryController {

    private static final String USER_MEMORY_LAYER = "long_term";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_CONVERSATION_ID = "conversationId";
    private static final String KEY_MESSAGE_ID = "messageId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String DEFAULT_SENSITIVITY = "UNKNOWN";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ObjectProvider<MemoryManagementInboundPort> managementPortProvider;
    private final ObjectProvider<UserMemoryPrivacyInboundPort> privacyPortProvider;

    public SeahorseUserMemoryController(ObjectProvider<MemoryManagementInboundPort> managementPortProvider,
                                        ObjectProvider<UserMemoryPrivacyInboundPort> privacyPortProvider) {
        this.managementPortProvider = managementPortProvider;
        this.privacyPortProvider = privacyPortProvider;
    }

    @GetMapping({"/me/memories", "/api/me/memories"})
    public ApiResponse<UserMemoryCenterResponse> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        int safeLimit = safeLimit(limit);
        MemoryPage page = managementPort().listMemories(resolvedUserId, USER_MEMORY_LAYER, null, safeLimit);
        UserMemoryPrivacySetting setting = privacyPort().current(resolvedUserId);
        return ApiResponse.ok(new UserMemoryCenterResponse(
                setting.userId(),
                setting.privacyMode(),
                page.records().stream()
                        .filter(record -> belongsTo(record, resolvedUserId))
                        .map(UserMemoryResponse::from)
                        .toList()));
    }

    @DeleteMapping({"/me/memories/{memoryId}", "/api/me/memories/{memoryId}"})
    public ApiResponse<Map<String, Boolean>> delete(
            @PathVariable String memoryId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        MemoryRecord record = managementPort().findMemory(USER_MEMORY_LAYER, memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found"));
        if (!belongsTo(record, resolvedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "memory does not belong to current user");
        }
        return ApiResponse.ok(Map.of("deleted", managementPort().deleteMemory(USER_MEMORY_LAYER, memoryId)));
    }

    @PostMapping({"/me/memory-settings/privacy-mode", "/api/me/memory-settings/privacy-mode"})
    public ApiResponse<UserMemoryPrivacyResponse> updatePrivacyMode(
            @RequestBody(required = false) UserMemoryPrivacyModeRequest request,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        boolean enabled = request != null && Boolean.TRUE.equals(request.enabled());
        return ApiResponse.ok(UserMemoryPrivacyResponse.from(privacyPort().setPrivacyMode(resolvedUserId, enabled)));
    }

    private MemoryManagementInboundPort managementPort() {
        MemoryManagementInboundPort port = managementPortProvider == null ? null : managementPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private UserMemoryPrivacyInboundPort privacyPort() {
        UserMemoryPrivacyInboundPort port = privacyPortProvider == null ? null : privacyPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static boolean belongsTo(MemoryRecord record, String userId) {
        if (record == null || userId == null || userId.isBlank()) {
            return false;
        }
        Object owner = record.metadata().get(KEY_USER_ID);
        return userId.equals(Objects.toString(owner, ""));
    }

    public record UserMemoryPrivacyModeRequest(Boolean enabled) {
    }

    public record UserMemoryCenterResponse(String userId,
                                           boolean privacyMode,
                                           List<UserMemoryResponse> memories) {
    }

    public record UserMemoryPrivacyResponse(String userId,
                                            boolean privacyMode,
                                            Instant updatedAt) {

        static UserMemoryPrivacyResponse from(UserMemoryPrivacySetting setting) {
            return new UserMemoryPrivacyResponse(setting.userId(), setting.privacyMode(), setting.updatedAt());
        }
    }

    public record UserMemoryResponse(String memoryId,
                                     String memoryType,
                                     String displayText,
                                     String sourceConversationId,
                                     String sourceMessageId,
                                     String status,
                                     String sensitivity,
                                     Instant updatedAt) {

        static UserMemoryResponse from(MemoryRecord record) {
            Map<String, Object> metadata = record.metadata();
            return new UserMemoryResponse(
                    record.id(),
                    record.type(),
                    record.content(),
                    Objects.toString(metadata.get(KEY_CONVERSATION_ID), null),
                    Objects.toString(metadata.get(KEY_MESSAGE_ID), null),
                    Objects.toString(metadata.get(KEY_STATUS), DEFAULT_STATUS),
                    Objects.toString(metadata.get(KEY_SENSITIVITY), DEFAULT_SENSITIVITY),
                    record.updatedAt());
        }
    }
}
