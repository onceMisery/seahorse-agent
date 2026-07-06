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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialJsonFieldClassifier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResults;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
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

import java.util.LinkedHashMap;
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
        return Map.of(
                KEY_CODE,
                SUCCESS_CODE,
                KEY_DATA,
                safeProfiles(runProfilePort().list(resolveUserId(userId, headerUserId))));
    }

    @GetMapping({"/run-profiles/executor-engines", "/api/run-profiles/executor-engines"})
    public Map<String, Object> supportedExecutorEngines() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, runProfilePort().supportedExecutorEngines());
    }

    @GetMapping({"/run-profiles/{id:-?\\d+}", "/api/run-profiles/{id:-?\\d+}"})
    public Map<String, Object> get(@PathVariable Long id,
                                   @RequestParam(required = false) String userId,
                                   @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                   String headerUserId) {
        return runProfilePort()
                .findById(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(details -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, safeDetails(details)))
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

    @PutMapping({"/run-profiles/{id:-?\\d+}", "/api/run-profiles/{id:-?\\d+}"})
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody RunProfileRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        Long savedId = runProfilePort().save(command(id, resolveUserId(userId, headerUserId), request));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, savedId);
    }

    @PostMapping({"/run-profiles/{id:-?\\d+}/activate", "/api/run-profiles/{id:-?\\d+}/activate"})
    public Map<String, Object> activate(@PathVariable Long id,
                                        @RequestParam(required = false) String userId,
                                        @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                        String headerUserId) {
        runProfilePort().activate(resolveUserId(userId, headerUserId), id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping({"/run-profiles/{id:-?\\d+}/resolve-preview", "/api/run-profiles/{id:-?\\d+}/resolve-preview"})
    public Map<String, Object> resolvePreview(@PathVariable Long id,
                                               @RequestParam(required = false) String userId,
                                               @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                               String headerUserId) {
        return runProfilePort()
                .resolvePreview(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(preview -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, safePreview(preview)))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @GetMapping({"/run-profiles/{id:-?\\d+}/risk-summary", "/api/run-profiles/{id:-?\\d+}/risk-summary"})
    public Map<String, Object> riskSummary(@PathVariable Long id,
                                           @RequestParam(required = false) String userId,
                                           @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                           String headerUserId) {
        return runProfilePort()
                .riskSummary(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(summary -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, summary))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({
            "/run-profiles/{id:-?\\d+}/production-gate/check",
            "/api/run-profiles/{id:-?\\d+}/production-gate/check"
    })
    public Map<String, Object> productionGateCheck(@PathVariable Long id,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID,
                                                           required = false)
                                                   String headerUserId) {
        return runProfilePort()
                .productionGateCheck(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(check -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, check))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({
            "/run-profiles/{id:-?\\d+}/production-gate/gate-result",
            "/api/run-profiles/{id:-?\\d+}/production-gate/gate-result"
    })
    public Map<String, Object> productionGateResult(@PathVariable Long id,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID,
                                                            required = false)
                                                    String headerUserId) {
        return runProfilePort()
                .productionGateCheck(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(check -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                        GateResults.fromRunProfileCheck(check)))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({"/run-profiles/{id:-?\\d+}/submit-approval", "/api/run-profiles/{id:-?\\d+}/submit-approval"})
    public Map<String, Object> submitApproval(@PathVariable Long id,
                                              @RequestBody(required = false) RunProfileGovernanceRequest request,
                                              @RequestParam(required = false) String userId,
                                              @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID,
                                                      required = false)
                                              String headerUserId) {
        runProfilePort().submitApproval(resolveUserId(userId, headerUserId), id, comment(request));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping({"/run-profiles/{id:-?\\d+}/approve", "/api/run-profiles/{id:-?\\d+}/approve"})
    public Map<String, Object> approve(@PathVariable Long id,
                                       @RequestBody(required = false) RunProfileGovernanceRequest request,
                                       @RequestParam(required = false) String userId,
                                       @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                       String headerUserId) {
        String safeUserId = resolveUserId(userId, headerUserId);
        runProfilePort().approve(safeUserId, id, operator(request, safeUserId), comment(request));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping({"/run-profiles/{id:-?\\d+}/reject", "/api/run-profiles/{id:-?\\d+}/reject"})
    public Map<String, Object> reject(@PathVariable Long id,
                                      @RequestBody(required = false) RunProfileGovernanceRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        String safeUserId = resolveUserId(userId, headerUserId);
        runProfilePort().reject(safeUserId, id, operator(request, safeUserId), comment(request));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping({"/run-profiles/{id:-?\\d+}/audit-summary", "/api/run-profiles/{id:-?\\d+}/audit-summary"})
    public Map<String, Object> auditSummary(@PathVariable Long id,
                                            @RequestParam(required = false) String userId,
                                            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                            String headerUserId) {
        return runProfilePort()
                .auditSummary(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(summary -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, summary))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @PostMapping({
            "/conversations/{conversationId}/run-profile/{id:-?\\d+}/apply",
            "/api/conversations/{conversationId}/run-profile/{id:-?\\d+}/apply"
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
                safePreview(runProfilePort().applyToConversation(resolveUserId(userId, headerUserId), conversationId, id)));
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
                .<Map<String, Object>>map(details -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, safeDetails(details)))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @DeleteMapping({"/run-profiles/{id:-?\\d+}", "/api/run-profiles/{id:-?\\d+}"})
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

    private List<RunProfileRecord> safeProfiles(List<RunProfileRecord> profiles) {
        return Objects.requireNonNullElse(profiles, List.<RunProfileRecord>of())
                .stream()
                .map(this::safeProfile)
                .toList();
    }

    private RunProfileDetails safeDetails(RunProfileDetails details) {
        if (details == null) {
            return null;
        }
        return RunProfileDetails.builder()
                .profile(safeProfile(details.getProfile()))
                .toolBindings(details.getToolBindings())
                .build();
    }

    private RunProfileRecord safeProfile(RunProfileRecord record) {
        if (record == null) {
            return null;
        }
        RunProfileRecord safe = new RunProfileRecord();
        safe.setId(record.getId());
        safe.setTenantId(record.getTenantId());
        safe.setUserId(record.getUserId());
        safe.setName(safeText(record.getName()));
        safe.setDescription(safeText(record.getDescription()));
        safe.setRoleCardId(record.getRoleCardId());
        safe.setExecutorEngine(record.getExecutorEngine());
        safe.setExecutorConfigJson(safeJsonText(record.getExecutorConfigJson()));
        safe.setModelConfigJson(safeJsonText(record.getModelConfigJson()));
        safe.setMemoryScopeJson(safeJsonText(record.getMemoryScopeJson()));
        safe.setGuardrailConfigJson(safeJsonText(record.getGuardrailConfigJson()));
        safe.setApprovalStatus(record.getApprovalStatus());
        safe.setApprovalOperator(safeText(record.getApprovalOperator()));
        safe.setApprovalComment(safeText(record.getApprovalComment()));
        safe.setApprovalTime(record.getApprovalTime());
        safe.setAssetSource(record.getAssetSource());
        safe.setPresetKey(record.getPresetKey());
        safe.setPresetVersion(record.getPresetVersion());
        safe.setReadonly(record.getReadonly());
        safe.setEnabled(record.getEnabled());
        safe.setCreateTime(record.getCreateTime());
        safe.setUpdateTime(record.getUpdateTime());
        safe.setDeleted(record.getDeleted());
        return safe;
    }

    private RunProfileResolvedPreview safePreview(RunProfileResolvedPreview preview) {
        if (preview == null) {
            return null;
        }
        return RunProfileResolvedPreview.builder()
                .runProfileId(preview.getRunProfileId())
                .roleCardId(preview.getRoleCardId())
                .executorEngine(preview.getExecutorEngine())
                .executorConfigJson(safeJsonText(preview.getExecutorConfigJson()))
                .modelConfigJson(safeJsonText(preview.getModelConfigJson()))
                .memoryScopeJson(safeJsonText(preview.getMemoryScopeJson()))
                .guardrailConfigJson(safeJsonText(preview.getGuardrailConfigJson()))
                .explicitToolAllowlist(preview.isExplicitToolAllowlist())
                .toolIds(preview.getToolIds())
                .mcpToolIds(preview.getMcpToolIds())
                .a2aAgentIds(preview.getA2aAgentIds())
                .build();
    }

    private String safeJsonText(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return objectMapper.writeValueAsString(safeJsonValue(null, parsed));
        } catch (JsonProcessingException ignored) {
            return safeText(text);
        }
    }

    private Object safeJsonValue(String key, Object value) {
        if (key != null && CredentialJsonFieldClassifier.isSensitiveOutputField(key)) {
            return CredentialTextRedactor.REDACTED_VALUE;
        }
        if (value instanceof String text) {
            return safeText(text);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                String safeKey = nestedKey == null ? null : String.valueOf(nestedKey);
                safe.put(safeKey, safeJsonValue(safeKey, nestedValue));
            });
            return safe;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> safeJsonValue(null, item))
                    .toList();
        }
        return value;
    }

    private String safeText(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private String comment(RunProfileGovernanceRequest request) {
        return request == null ? null : request.getComment();
    }

    private String operator(RunProfileGovernanceRequest request, String defaultOperator) {
        if (request == null || request.getOperator() == null || request.getOperator().isBlank()) {
            return defaultOperator;
        }
        return request.getOperator().trim();
    }

    private RunProfileInboundPort runProfilePort() {
        RunProfileInboundPort port = runProfilePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("RunProfileInboundPort is not configured");
        }
        return port;
    }
}
