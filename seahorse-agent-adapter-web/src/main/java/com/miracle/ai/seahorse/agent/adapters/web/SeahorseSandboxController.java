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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxEgressPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeProfilePolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@RestController
public class SeahorseSandboxController {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final Set<String> SANDBOX_TOOL_IDS = Set.of(
            "sandbox_python",
            "sandbox_file_convert",
            "sandbox_browser");

    private final ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider;
    private final ObjectProvider<ObjectStoragePort> objectStoragePortProvider;
    private final ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider;
    private final ObjectProvider<SandboxPolicyPort> sandboxPolicyPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                      ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider,
                                      ObjectProvider<ObjectStoragePort> objectStoragePortProvider,
                                      ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider,
                                      ObjectProvider<SandboxPolicyPort> sandboxPolicyPortProvider) {
        this(sandboxRuntimePortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults),
                objectStoragePortProvider,
                quotaManagementPortProvider,
                sandboxPolicyPortProvider);
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                     AdvancedFeatureGate advancedFeatureGate) {
        this(sandboxRuntimePortProvider, advancedFeatureGate, null);
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                      AdvancedFeatureGate advancedFeatureGate,
                                      ObjectProvider<ObjectStoragePort> objectStoragePortProvider) {
        this(sandboxRuntimePortProvider, advancedFeatureGate, objectStoragePortProvider, null);
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                      AdvancedFeatureGate advancedFeatureGate,
                                      ObjectProvider<ObjectStoragePort> objectStoragePortProvider,
                                      ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider) {
        this(sandboxRuntimePortProvider, advancedFeatureGate, objectStoragePortProvider, quotaManagementPortProvider, null);
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                      AdvancedFeatureGate advancedFeatureGate,
                                      ObjectProvider<ObjectStoragePort> objectStoragePortProvider,
                                      ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider,
                                      ObjectProvider<SandboxPolicyPort> sandboxPolicyPortProvider) {
        this.sandboxRuntimePortProvider = sandboxRuntimePortProvider;
        this.objectStoragePortProvider = objectStoragePortProvider;
        this.quotaManagementPortProvider = quotaManagementPortProvider;
        this.sandboxPolicyPortProvider = sandboxPolicyPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/sandbox/sessions")
    public ApiResponse<Object> createSession(@RequestBody SandboxSessionCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxSessionCreateRequest safeRequest = request == null
                ? new SandboxSessionCreateRequest(null, null, null, false, List.of(), null, null)
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.createSession(new SandboxSessionCreateCommand(
                        safeRequest.tenantId(),
                        safeRequest.runId(),
                        safeRequest.runtimeType(),
                        safeRequest.networkRequested(),
                        safeRequest.requestedHosts(),
                        safeRequest.profileId(),
                        safeRequest.expiresAt())));
    }

    @PostMapping("/api/sandbox/sessions/{sessionId}/execute")
    public ApiResponse<Object> execute(@PathVariable String sessionId,
                                       @RequestBody SandboxExecutionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxExecutionRequest safeRequest = request == null
                ? new SandboxExecutionRequest(null, false, List.of())
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> toResponse(port.execute(new SandboxExecutionCommand(
                        sessionId,
                        safeRequest.input(),
                        safeRequest.networkRequested(),
                        safeRequest.requestedHosts()))));
    }

    @PostMapping("/api/sandbox/sessions/{sessionId}/close")
    public ApiResponse<Object> close(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.close(sessionId));
    }

    @GetMapping("/api/sandbox/sessions")
    public ApiResponse<Object> listSessions(@RequestParam String tenantId,
                                            @RequestParam(defaultValue = "20") int limit) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.listSessions(tenantId, limit));
    }

    @PostMapping("/api/sandbox/sessions/expired:sweep")
    public ApiResponse<Object> sweepExpiredSessions(@RequestParam(defaultValue = "default") String tenantId,
                                                    @RequestParam(defaultValue = "20") int limit) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.sweepExpiredSessions(tenantId, limit));
    }

    @PostMapping("/api/sandbox/runtime/orphans:sweep")
    public ApiResponse<Object> sweepOrphanedRuntimeResources() {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                SandboxRuntimeInboundPort::sweepOrphanedRuntimeResources);
    }

    @GetMapping("/api/sandbox/runtime/health")
    public ApiResponse<Object> inspectRuntimeHealth() {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                SandboxRuntimeInboundPort::inspectRuntimeHealth);
    }

    @GetMapping("/api/sandbox/runtime/nodes")
    public ApiResponse<Object> inspectRuntimeNodes() {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                SandboxRuntimeInboundPort::inspectRuntimeNodes);
    }

    @GetMapping("/api/sandbox/runtime/artifact-scanner-policy")
    public ApiResponse<Object> inspectArtifactScannerPolicy() {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                SandboxRuntimeInboundPort::inspectArtifactScannerPolicy);
    }

    @GetMapping("/api/sandbox/runtime/profiles")
    public ApiResponse<Object> listRuntimeProfiles(
            @RequestParam(defaultValue = DEFAULT_TENANT_ID) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> runtimeProfilesResponse(
                        safeTenantId,
                        port.listRuntimeProfilePolicies(safeTenantId),
                        sandboxPolicyPortProvider == null
                                ? null
                                : sandboxPolicyPortProvider.getIfAvailable()));
    }

    @GetMapping("/api/sandbox/runtime/egress-policy")
    public ApiResponse<Object> inspectSandboxEgressPolicy(
            @RequestParam(defaultValue = DEFAULT_TENANT_ID) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.inspectSandboxEgressPolicy(safeTenantId));
    }

    @PostMapping("/api/sandbox/runtime/egress-policy")
    public ApiResponse<Object> upsertSandboxEgressPolicy(
            @RequestBody SandboxEgressPolicyRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxEgressPolicyRequest safeRequest = request == null
                ? new SandboxEgressPolicyRequest(null, null, null, null)
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.upsertSandboxEgressPolicy(new SandboxEgressPolicyUpsertCommand(
                        safeRequest.policyId(),
                        safeRequest.tenantId(),
                        safeRequest.networkPolicy(),
                        safeRequest.allowlistedHosts())));
    }

    @PostMapping("/api/sandbox/runtime/profile-policies")
    public ApiResponse<Object> upsertRuntimeProfilePolicy(
            @RequestBody SandboxRuntimeProfilePolicyRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxRuntimeProfilePolicyRequest safeRequest = request == null
                ? new SandboxRuntimeProfilePolicyRequest(null, null, null, null, null, null, null)
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.upsertRuntimeProfilePolicy(new SandboxRuntimeProfilePolicyUpsertCommand(
                        safeRequest.policyId(),
                        safeRequest.tenantId(),
                        safeRequest.runtimeType(),
                        safeRequest.profileId(),
                        safeRequest.status(),
                        safeRequest.sessionTtlSeconds(),
                        safeRequest.networkAllowed())));
    }

    @PostMapping("/api/sandbox/runtime/tool-quota-policies")
    public ApiResponse<Object> upsertToolQuotaPolicy(@RequestBody SandboxToolQuotaPolicyRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        advancedFeatureGate.requireEnabled(AdvancedFeature.QUOTA_MANAGEMENT);
        SandboxToolQuotaPolicyRequest safeRequest = request == null
                ? new SandboxToolQuotaPolicyRequest(null, null, null, null, null, null, null, null)
                : request;
        String tenantId = requireText(safeRequest.tenantId(), "tenantId must not be blank");
        String toolId = normalizeToolId(safeRequest.toolId());
        if (!SANDBOX_TOOL_IDS.contains(toolId)) {
            throw new IllegalArgumentException("toolId must be a sandbox-backed tool");
        }
        return ApiResponses.requireService(quotaManagementPortProvider,
                port -> port.upsertPolicy(new QuotaPolicyUpsertCommand(
                        policyIdOrDefault(safeRequest.policyId(), tenantId, toolId),
                        tenantId,
                        QuotaScope.TOOL,
                        toolId,
                        safeRequest.status(),
                        safeRequest.tokenLimit(),
                        safeRequest.callLimit(),
                        safeRequest.costLimit(),
                        safeRequest.warnRatio(),
                        null,
                        null)));
    }

    @PostMapping("/api/sandbox/runtime/orphan-containers:reap")
    public ApiResponse<Object> reapOrphanedRuntimeContainers(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.reapOrphanedRuntimeContainers(dryRun));
    }

    @GetMapping("/api/sandbox/sessions/{sessionId}/executions")
    public ApiResponse<Object> listExecutions(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.listExecutions(sessionId));
    }

    @GetMapping("/api/sandbox/sessions/{sessionId}/artifacts")
    public ApiResponse<Object> listArtifacts(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.listArtifacts(sessionId).stream()
                .map(SeahorseSandboxController::toResponse)
                .toList());
    }

    @GetMapping("/api/sandbox/artifacts/{artifactId}")
    public ApiResponse<Object> describeArtifact(@PathVariable String artifactId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> toResponse(port.describeArtifact(artifactId)));
    }

    @GetMapping("/api/sandbox/artifacts/{artifactId}/download")
    public ResponseEntity<InputStreamResource> downloadArtifact(@PathVariable String artifactId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxRuntimeInboundPort sandboxPort = requireSandboxPort();
        ObjectStoragePort storagePort = requireStoragePort();
        SandboxArtifactDownloadDecision decision = sandboxPort.downloadArtifact(artifactId);
        InputStream stream = storagePort.openStream(decision.storageRef());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(decision.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + decision.filename() + "\"")
                .body(new InputStreamResource(stream));
    }

    private SandboxRuntimeInboundPort requireSandboxPort() {
        SandboxRuntimeInboundPort port = sandboxRuntimePortProvider == null
                ? null
                : sandboxRuntimePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private ObjectStoragePort requireStoragePort() {
        ObjectStoragePort port = objectStoragePortProvider == null
                ? null
                : objectStoragePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private static SandboxExecutionResultResponse toResponse(SandboxExecutionResult result) {
        return new SandboxExecutionResultResponse(
                result.execution(),
                result.artifacts().stream()
                        .map(SeahorseSandboxController::toResponse)
                        .toList(),
                result.reasonCode());
    }

    private static SandboxArtifactResponse toResponse(SandboxArtifact artifact) {
        return new SandboxArtifactResponse(
                artifact.artifactId(),
                artifact.sessionId(),
                artifact.executionId(),
                artifact.mediaType(),
                artifact.scanStatus(),
                artifact.sensitivity(),
                artifact.scanSummary(),
                artifact.redactionSummaryJson(),
                artifact.promptVisible(),
                artifact.createdAt());
    }

    private static SandboxArtifactDetailResponse toResponse(SandboxArtifactDetailDecision decision) {
        SandboxArtifact artifact = decision.artifact();
        return new SandboxArtifactDetailResponse(
                artifact.artifactId(),
                artifact.sessionId(),
                artifact.executionId(),
                artifact.mediaType(),
                decision.contentType(),
                decision.filename(),
                artifact.scanStatus(),
                artifact.sensitivity(),
                artifact.scanSummary(),
                artifact.redactionSummaryJson(),
                artifact.promptVisible(),
                decision.downloadable(),
                decision.downloadBlockedReason(),
                artifact.createdAt());
    }

    private static SandboxRuntimeProfilesResponse runtimeProfilesResponse(String tenantId,
                                                                          List<SandboxRuntimeProfilePolicy> policies,
                                                                          SandboxPolicyPort policyPort) {
        List<SandboxRuntimeProfilePolicy> safePolicies = policies == null ? List.of() : List.copyOf(policies);
        String safeTenantId = tenantId == null || tenantId.trim().isEmpty()
                ? DEFAULT_TENANT_ID
                : tenantId.trim();
        return new SandboxRuntimeProfilesResponse(
                List.of(
                        runtimeProfile(SandboxRuntimeType.CODE_INTERPRETER, safePolicies),
                        runtimeProfile(SandboxRuntimeType.FILE_CONVERSION, safePolicies),
                        runtimeProfile(SandboxRuntimeType.BROWSER_AUTOMATION, safePolicies),
                        runtimeProfile(SandboxRuntimeType.SHELL, safePolicies)),
                policyPort == null
                        ? SandboxNetworkPolicy.DENY_ALL.name()
                        : policyPort.networkPolicy(safeTenantId).name(),
                policyPort == null ? List.of() : policyPort.allowlistedHosts(safeTenantId),
                SandboxRuntimeProfilePolicy.DEFAULT_SESSION_TTL_SECONDS);
    }

    private static SandboxRuntimeProfileResponse runtimeProfile(SandboxRuntimeType runtimeType,
                                                                List<SandboxRuntimeProfilePolicy> policies) {
        boolean supportedByContainerRuntime = runtimeType == SandboxRuntimeType.CODE_INTERPRETER
                || runtimeType == SandboxRuntimeType.FILE_CONVERSION
                || runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION;
        SandboxRuntimeProfilePolicy policy = policies.stream()
                .filter(candidate -> candidate.runtimeType() == runtimeType)
                .findFirst()
                .orElseGet(() -> SandboxRuntimeProfilePolicy.defaultPolicy("default", runtimeType, Instant.EPOCH));
        String status = policy.status() == SandboxRuntimeProfilePolicyStatus.DISABLED
                ? "BLOCKED"
                : supportedByContainerRuntime ? "SUPPORTED" : "PLANNED";
        return new SandboxRuntimeProfileResponse(
                runtimeType,
                policy.profileId(),
                supportedByContainerRuntime,
                policy.networkAllowed(),
                status,
                policy.policyId(),
                policy.status(),
                policy.sessionTtlSeconds());
    }

    private static String policyIdOrDefault(String policyId, String tenantId, String toolId) {
        if (policyId != null && !policyId.trim().isEmpty()) {
            return policyId.trim();
        }
        return "sandbox-tool-quota-" + tenantId.trim() + "-" + toolId.trim();
    }

    private static String normalizeToolId(String toolId) {
        return requireText(toolId, "toolId must not be blank").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public record SandboxSessionCreateRequest(String tenantId,
                                              String runId,
                                              SandboxRuntimeType runtimeType,
                                              boolean networkRequested,
                                              List<String> requestedHosts,
                                              String profileId,
                                              Instant expiresAt) {

        public SandboxSessionCreateRequest {
            requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
            profileId = profileId == null || profileId.trim().isEmpty() ? null : profileId.trim();
        }
    }

    public record SandboxExecutionRequest(String input,
                                          boolean networkRequested,
                                          List<String> requestedHosts) {

        public SandboxExecutionRequest {
            input = input == null ? "" : input;
            requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
        }
    }

    public record SandboxExecutionResultResponse(SandboxExecution execution,
                                                 List<SandboxArtifactResponse> artifacts,
                                                 SandboxPolicyReasonCode reasonCode) {

        public SandboxExecutionResultResponse {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record SandboxArtifactResponse(String artifactId,
                                          String sessionId,
                                          String executionId,
                                          String mediaType,
                                          SandboxArtifactScanStatus scanStatus,
                                          ContextSensitivity sensitivity,
                                          String scanSummary,
                                          String redactionSummaryJson,
                                          boolean promptVisible,
                                          Instant createdAt) {
    }

    public record SandboxArtifactDetailResponse(String artifactId,
                                                String sessionId,
                                                String executionId,
                                                String mediaType,
                                                String contentType,
                                                String filename,
                                                SandboxArtifactScanStatus scanStatus,
                                                ContextSensitivity sensitivity,
                                                String scanSummary,
                                                String redactionSummaryJson,
                                                boolean promptVisible,
                                                boolean downloadable,
                                                String downloadBlockedReason,
                                                Instant createdAt) {
    }

    public record SandboxRuntimeProfilesResponse(List<SandboxRuntimeProfileResponse> profiles,
                                                  String defaultNetworkPolicy,
                                                  List<String> allowlistedHosts,
                                                  long defaultTtlSeconds) {

        public SandboxRuntimeProfilesResponse {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
            allowlistedHosts = allowlistedHosts == null ? List.of() : List.copyOf(allowlistedHosts);
        }
    }

    public record SandboxRuntimeProfileResponse(SandboxRuntimeType runtimeType,
                                                String profileId,
                                                boolean supportedByContainerRuntime,
                                                boolean networkAllowed,
                                                String status,
                                                String policyId,
                                                SandboxRuntimeProfilePolicyStatus policyStatus,
                                                long sessionTtlSeconds) {
    }

    public record SandboxRuntimeProfilePolicyRequest(String policyId,
                                                     String tenantId,
                                                     SandboxRuntimeType runtimeType,
                                                     String profileId,
                                                     SandboxRuntimeProfilePolicyStatus status,
                                                     Long sessionTtlSeconds,
                                                     Boolean networkAllowed) {
    }

    public record SandboxEgressPolicyRequest(String policyId,
                                             String tenantId,
                                             SandboxNetworkPolicy networkPolicy,
                                             List<String> allowlistedHosts) {

        public SandboxEgressPolicyRequest {
            allowlistedHosts = allowlistedHosts == null ? List.of() : List.copyOf(allowlistedHosts);
        }
    }

    public record SandboxToolQuotaPolicyRequest(String policyId,
                                                String tenantId,
                                                String toolId,
                                                QuotaPolicyStatus status,
                                                Long tokenLimit,
                                                Long callLimit,
                                                Double costLimit,
                                                Double warnRatio) {
    }
}
