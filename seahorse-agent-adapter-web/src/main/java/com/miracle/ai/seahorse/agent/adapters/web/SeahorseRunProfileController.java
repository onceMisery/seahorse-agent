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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseRunProfileController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    @NonNull
    private final ObjectProvider<RunProfileInboundPort> runProfilePortProvider;
    @NonNull
    private final ObjectMapper objectMapper;

    public SeahorseRunProfileController(ObjectProvider<RunProfileInboundPort> runProfilePortProvider) {
        this(runProfilePortProvider, new ObjectMapper());
    }

    @Autowired
    public SeahorseRunProfileController(
            ObjectProvider<RunProfileInboundPort> runProfilePortProvider,
            ObjectMapper objectMapper) {
        this.runProfilePortProvider = Objects.requireNonNull(
                runProfilePortProvider,
                "runProfilePortProvider must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @GetMapping({"/run-profiles", "/api/run-profiles"})
    public Map<String, Object> list(@RequestParam(required = false) String userId,
                                    @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                    String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, runProfilePort().list(resolveUserId(userId, headerUserId)));
    }

    @GetMapping({"/run-profiles/{id}", "/api/run-profiles/{id}"})
    public Map<String, Object> get(@PathVariable Long id,
                                   @RequestParam(required = false) String userId,
                                   @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                   String headerUserId) {
        return runProfilePort()
                .findById(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(details -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, details))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({"/run-profiles", "/api/run-profiles"})
    public Map<String, Object> create(@RequestBody RunProfileRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        Long id = runProfilePort().save(command(null, resolveUserId(userId, headerUserId), request));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping({"/run-profiles/{id}", "/api/run-profiles/{id}"})
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody RunProfileRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        Long savedId = runProfilePort().save(command(id, resolveUserId(userId, headerUserId), request));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, savedId);
    }

    @PostMapping({"/run-profiles/{id}/activate", "/api/run-profiles/{id}/activate"})
    public Map<String, Object> activate(@PathVariable Long id,
                                        @RequestParam(required = false) String userId,
                                        @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                        String headerUserId) {
        runProfilePort().activate(resolveUserId(userId, headerUserId), id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping({"/run-profiles/{id}/resolve-preview", "/api/run-profiles/{id}/resolve-preview"})
    public Map<String, Object> resolvePreview(@PathVariable Long id,
                                               @RequestParam(required = false) String userId,
                                               @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                               String headerUserId) {
        return runProfilePort()
                .resolvePreview(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(preview -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, preview))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({
            "/conversations/{conversationId}/run-profile/{id}/apply",
            "/api/conversations/{conversationId}/run-profile/{id}/apply"
    })
    public Map<String, Object> applyToConversation(@PathVariable String conversationId,
                                                   @PathVariable Long id,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                                   String headerUserId) {
        return Map.of(
                KEY_CODE,
                SUCCESS_CODE,
                KEY_DATA,
                runProfilePort().applyToConversation(resolveUserId(userId, headerUserId), conversationId, id));
    }

    @GetMapping({
            "/conversations/{conversationId}/run-profile",
            "/api/conversations/{conversationId}/run-profile"
    })
    public Map<String, Object> getAppliedToConversation(@PathVariable String conversationId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID,
                                                                required = false)
                                                        String headerUserId) {
        return runProfilePort()
                .findAppliedToConversation(resolveUserId(userId, headerUserId), conversationId)
                .<Map<String, Object>>map(details -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, details))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @DeleteMapping({"/run-profiles/{id}", "/api/run-profiles/{id}"})
    public Map<String, Object> delete(@PathVariable Long id,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        runProfilePort().delete(resolveUserId(userId, headerUserId), id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private RunProfileCommand command(Long id, String userId, RunProfileRequest request) {
        RunProfileRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return RunProfileCommand.builder()
                .id(id)
                .userId(userId)
                .name(safeRequest.getName())
                .description(safeRequest.getDescription())
                .roleCardId(safeRequest.getRoleCardId())
                .executorEngine(safeRequest.getExecutorEngine())
                .executorConfigJson(jsonOrNull(safeRequest.getExecutorConfig()))
                .modelConfigJson(jsonOrNull(safeRequest.getModelConfig()))
                .memoryScopeJson(jsonOrNull(safeRequest.getMemoryScope()))
                .guardrailConfigJson(jsonOrNull(safeRequest.getGuardrailConfig()))
                .toolBindings(toolBindings(safeRequest.getToolBindings()))
                .build();
    }

    private List<RunProfileToolBindingCommand> toolBindings(List<RunProfileToolBindingRequest> requests) {
        return Objects.requireNonNullElse(requests, List.<RunProfileToolBindingRequest>of())
                .stream()
                .filter(Objects::nonNull)
                .map(request -> RunProfileToolBindingCommand.builder()
                        .toolId(request.getToolId())
                        .provider(request.getProvider())
                        .enabled(request.isEnabled())
                        .build())
                .toList();
    }

    private String jsonOrNull(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid run profile config", ex);
        }
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }

    private RunProfileInboundPort runProfilePort() {
        RunProfileInboundPort port = runProfilePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("RunProfileInboundPort is not configured");
        }
        return port;
    }
}
