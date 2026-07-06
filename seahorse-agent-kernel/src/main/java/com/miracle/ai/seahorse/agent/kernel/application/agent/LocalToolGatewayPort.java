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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolArtifactPublicationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolOutputRedactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LocalToolGatewayPort implements ToolGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(LocalToolGatewayPort.class);
    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final int MAX_PREVIEW_ARGUMENT_KEY_LENGTH = 64;
    private static final String APPROVAL_ID_PREFIX = "approval:";
    private static final String LEGACY_RUN_ID_PREFIX = "legacy-run:";
    private static final String LEGACY_USER_ID = "legacy-user";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> SANDBOX_FILE_FORMATS = List.of(
            "csv",
            "tsv",
            "json",
            "txt",
            "html",
            "markdown",
            "md",
            "docx",
            "pdf");
    private static final List<String> SANDBOX_FILE_CONTENT_ENCODINGS = List.of("plain", "base64");
    private static final List<String> SANDBOX_BROWSER_ARGUMENT_KEYS = List.of(
            "html",
            "url",
            "allowedHosts",
            "cookies",
            "sessionState",
            "captureSessionState",
            "action",
            "screenshot",
            "har",
            "video",
            "viewportWidth",
            "viewportHeight");

    private final ToolRegistryPort toolRegistry;
    private final ToolPolicyPort toolPolicy;
    private final ToolInvocationAuditPort auditPort;
    private final ToolApprovalRequestRepositoryPort approvalRequestRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ToolOutputRedactionPort outputRedactionPort;
    private final ToolArtifactPublicationPort artifactPublicationPort;
    private final Clock clock;

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry) {
        this(toolRegistry, ToolPolicyPort.defaults());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry, ToolPolicyPort toolPolicy) {
        this(toolRegistry, toolPolicy, ToolInvocationAuditPort.noop());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort) {
        this(toolRegistry, toolPolicy, auditPort, Clock.systemUTC());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                Clock clock) {
        this(toolRegistry, toolPolicy, auditPort, ToolApprovalRequestRepositoryPort.noop(), clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                outputRedactionPort,
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                Clock clock) {
        this(toolRegistry, toolPolicy, auditPort, approvalRequestRepository, ApprovalRequestQueryPort.empty(), clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                approvalRequestRepository,
                approvalQueryPort,
                ToolOutputRedactionPort.noop(),
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                approvalRequestRepository,
                approvalQueryPort,
                outputRedactionPort,
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                ToolArtifactPublicationPort artifactPublicationPort,
                                Clock clock) {
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
        this.auditPort = Objects.requireNonNullElseGet(auditPort, ToolInvocationAuditPort::noop);
        this.approvalRequestRepository = Objects.requireNonNullElseGet(
                approvalRequestRepository,
                ToolApprovalRequestRepositoryPort::noop);
        this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
        this.outputRedactionPort = Objects.requireNonNullElseGet(outputRedactionPort, ToolOutputRedactionPort::noop);
        this.artifactPublicationPort = Objects.requireNonNullElseGet(
                artifactPublicationPort,
                ToolArtifactPublicationPort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String invocationId = nextInvocationId();
        String effectiveRunId = auditRunId(safeRequest.runId(), invocationId);
        String effectiveUserId = auditUserId(safeRequest.userId());
        Instant startedAt = clock.instant();
        auditPort.recordRequested(new ToolInvocationAuditRecord(
                invocationId,
                effectiveRunId,
                safeRequest.stepId(),
                safeRequest.agentId(),
                safeRequest.versionId(),
                safeRequest.rolloutId(),
                safeRequest.tenantId(),
                effectiveUserId,
                safeRequest.toolId(),
                safeRequest.idempotencyKey(),
                ToolInvocationStatus.REQUESTED,
                summarizeArguments(safeRequest),
                startedAt));
        Optional<ToolPort> toolPort = toolRegistry.find(safeRequest.toolId());

        // 策略裁决必须发生在真实工具执行之前；非 ALLOW 结果不得触达 ToolPort。
        PolicyDecision decision = Objects.requireNonNullElseGet(
                toolPolicy.decide(ToolPolicyRequest.from(safeRequest, toolPort.isPresent())),
                () -> PolicyDecision.deny("builtin-policy-null", ToolPolicyReasonCodes.POLICY_DECISION_MISSING,
                        "Tool policy did not return a decision"));
        boolean approvalSatisfied = approvalSatisfied(safeRequest, decision);
        ToolInvocationStatus decisionStatus = approvalSatisfied ? ToolInvocationStatus.ALLOWED : decisionStatus(decision);
        auditPort.recordDecision(new ToolInvocationAuditDecision(invocationId, decision.decisionId(), decisionStatus));
        if (!decision.allowsExecution() && !approvalSatisfied) {
            String approvalId = null;
            if (decision.effect() == PolicyDecision.Effect.APPROVAL_REQUIRED) {
                approvalId = createApprovalRequest(
                        safeRequest,
                        decision,
                        invocationId,
                        effectiveRunId,
                        effectiveUserId,
                        startedAt);
            }
            ToolInvocationResult result = ToolInvocationResult.failed(decision.reasonCode(), approvalId);
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    decisionStatus,
                    summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            return result;
        }

        try {
            ToolPort executableTool = toolPort
                    .orElseGet(() -> ToolPort.notFound(safeRequest.toolId()));
            ToolInvocationResult rawResult = executableTool instanceof ToolInvocationRequestAwarePort awareTool
                    ? awareTool.invoke(safeRequest)
                    : executableTool.invoke(safeRequest.toolCallId(), safeRequest.toolId(), safeRequest.arguments());
            if (rawResult.success()) {
                publishArtifacts(safeRequest, rawResult);
            }
            ToolInvocationResult result = outputRedactionPort.redact(safeRequest, rawResult);
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    result.success() ? ToolInvocationStatus.SUCCEEDED : ToolInvocationStatus.FAILED,
                    summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            return result;
        } catch (Exception ex) {
            ToolInvocationResult result = ToolInvocationResult.failed(
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    ToolInvocationStatus.FAILED,
                    summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            return result;
        }
    }

    private void publishArtifacts(ToolInvocationRequest request, ToolInvocationResult result) {
        try {
            artifactPublicationPort.publish(request, result);
        } catch (RuntimeException ex) {
            // Artifact publication is a side effect; the tool observation remains authoritative.
            log.warn("Artifact publication failed for tool={}, error={}",
                     request.toolId(), ex.getMessage(), ex);
        }
    }

    private String nextInvocationId() {
        return SnowflakeIds.nextIdString();
    }

    private String createApprovalRequest(ToolInvocationRequest request,
                                         PolicyDecision decision,
                                         String invocationId,
                                         String effectiveRunId,
                                         String effectiveUserId,
                                         Instant requestedAt) {
        // 审批请求保存的是可展示的参数预览，不保存完整敏感入参；真正恢复执行由后续 durable runtime 切片接管。
        String approvalId = approvalId(invocationId);
        approvalRequestRepository.save(new ApprovalRequest(
                approvalId,
                effectiveRunId,
                request.stepId(),
                invocationId,
                request.tenantId(),
                effectiveUserId,
                request.agentId(),
                request.rolloutId(),
                request.toolId(),
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                approvalSummary(request, decision),
                argumentsPreviewJson(request),
                ApprovalRequestStatus.PENDING,
                requestedAt,
                null,
                null,
                null,
                null));
        return approvalId;
    }

    private String approvalId(String invocationId) {
        return APPROVAL_ID_PREFIX + invocationId;
    }

    private String approvalSummary(ToolInvocationRequest request, PolicyDecision decision) {
        return truncate("Tool " + request.toolId() + " requires approval: " + decision.reasonCode());
    }

    private String argumentsPreviewJson(ToolInvocationRequest request) {
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "argumentKeys", safeArgumentKeys(request.arguments()),
                    "argumentCount", request.arguments().size(),
                    "argumentValueCount", mapValueCount(request.arguments()),
                    "argumentValueTotalLength", mapValueTotalLength(request.arguments()),
                    "argumentValueMaxLength", mapValueMaxLength(request.arguments()),
                    "resourceRefKeys", safeResourceRefKeys(request.resourceRefs()),
                    "resourceRefCount", request.resourceRefs().size(),
                    "resourceRefHash", sha256(canonicalResourceRefs(request.resourceRefs())))));
        } catch (JsonProcessingException ex) {
            return truncate("keys=" + safeArgumentKeys(request.arguments())
                    + ", size=" + request.arguments().size()
                    + ", argumentValueCount=" + mapValueCount(request.arguments())
                    + ", argumentValueTotalLength=" + mapValueTotalLength(request.arguments())
                    + ", argumentValueMaxLength=" + mapValueMaxLength(request.arguments())
                    + ", resourceRefCount=" + request.resourceRefs().size());
        }
    }

    private String auditRunId(String runId, String invocationId) {
        if (runId != null && !runId.isBlank()) {
            return runId;
        }
        // 兼容直接调用 KernelAgentLoop 的 legacy 路径，避免持久审计因为缺少 runId 中断工具执行。
        return LEGACY_RUN_ID_PREFIX + invocationId;
    }

    private String auditUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        // 兼容没有登录上下文的 legacy 调用，企业运行时应始终传入真实 userId。
        return LEGACY_USER_ID;
    }

    private ToolInvocationStatus decisionStatus(PolicyDecision decision) {
        return switch (decision.effect()) {
            case ALLOW -> ToolInvocationStatus.ALLOWED;
            case APPROVAL_REQUIRED -> ToolInvocationStatus.APPROVAL_REQUIRED;
            default -> ToolInvocationStatus.DENIED;
        };
    }

    private boolean approvalSatisfied(ToolInvocationRequest request, PolicyDecision decision) {
        if (decision.effect() != PolicyDecision.Effect.APPROVAL_REQUIRED) {
            return false;
        }
        return approvalQueryPort.findLatestByRunIdAndStepId(request.runId(), request.stepId())
                .filter(approval -> approval.status() == ApprovalRequestStatus.APPROVED
                        || approval.status() == ApprovalRequestStatus.MODIFIED)
                .isPresent();
    }

    private String summarizeArguments(ToolInvocationRequest request) {
        if ("sandbox_browser".equals(request.toolId())) {
            return summarizeSandboxBrowserArguments(request);
        }
        if ("sandbox_python".equals(request.toolId())) {
            return summarizeSandboxPythonArguments(request);
        }
        if ("sandbox_file_convert".equals(request.toolId())) {
            return summarizeSandboxFileConvertArguments(request);
        }
        if ("invoke_remote_a2a_agent".equals(request.toolId())) {
            return summarizeRemoteA2aArguments(request);
        }
        if (request.toolId() != null && request.toolId().startsWith("openapi_")) {
            return summarizeOpenApiArguments(request);
        }
        return summarizeGenericArguments(request);
    }

    private String summarizeGenericArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=" + request.toolId()
                    + ", argumentKeys=" + safeArgumentKeys(arguments)
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeOpenApiArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> path = mapValue(arguments.get("path"));
        Map<String, Object> query = mapValue(arguments.get("query"));
        Map<String, Object> parameters = mapValue(arguments.get("parameters"));
        Map<String, Object> headers = mergeMaps(arguments.get("header"), arguments.get("headers"));
        Object body = arguments.containsKey("requestBody") ? arguments.get("requestBody") : arguments.get("body");
        Map<String, Object> bodyMap = mapValue(body);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("provider", "OPENAPI");
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("pathKeys", safeArgumentKeys(path));
        summary.put("pathCount", path.size());
        summary.put("pathValueCount", mapValueCount(path));
        summary.put("pathValueTotalLength", mapValueTotalLength(path));
        summary.put("pathValueMaxLength", mapValueMaxLength(path));
        summary.put("queryKeys", safeArgumentKeys(query));
        summary.put("queryCount", query.size());
        summary.put("queryValueCount", mapValueCount(query));
        summary.put("queryValueTotalLength", mapValueTotalLength(query));
        summary.put("queryValueMaxLength", mapValueMaxLength(query));
        summary.put("parameterKeys", safeArgumentKeys(parameters));
        summary.put("parameterCount", parameters.size());
        summary.put("parameterValueCount", mapValueCount(parameters));
        summary.put("parameterValueTotalLength", mapValueTotalLength(parameters));
        summary.put("parameterValueMaxLength", mapValueMaxLength(parameters));
        summary.put("headerKeys", safeArgumentKeys(headers));
        summary.put("headerCount", headers.size());
        summary.put("headerValueCount", mapValueCount(headers));
        summary.put("headerValueTotalLength", mapValueTotalLength(headers));
        summary.put("headerValueMaxLength", mapValueMaxLength(headers));
        summary.put("requestBodyPresent", body != null);
        summary.put("requestBodyType", valueType(body));
        if (body instanceof String text) {
            summary.put("requestBodyLength", text.length());
        } else if (!bodyMap.isEmpty()) {
            summary.put("requestBodyKeys", safeArgumentKeys(bodyMap));
            summary.put("requestBodyFieldCount", bodyMap.size());
            summary.put("requestBodyValueCount", mapValueCount(bodyMap));
            summary.put("requestBodyValueTotalLength", mapValueTotalLength(bodyMap));
            summary.put("requestBodyValueMaxLength", mapValueMaxLength(bodyMap));
        }
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=" + request.toolId()
                    + ", provider=OPENAPI"
                    + ", argumentKeys=" + safeArgumentKeys(arguments)
                    + ", argumentCount=" + arguments.size()
                    + ", pathKeys=" + safeArgumentKeys(path)
                    + ", queryKeys=" + safeArgumentKeys(query)
                    + ", parameterKeys=" + safeArgumentKeys(parameters)
                    + ", headerKeys=" + safeArgumentKeys(headers)
                    + ", requestBodyPresent=" + (body != null)
                    + ", requestBodyType=" + valueType(body));
        }
    }

    private String summarizeSandboxPythonArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        List<String> requestedHosts = argumentStringList(arguments.get("requestedHosts"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "CODE_INTERPRETER");
        summary.put("codeLength", argumentString(arguments, "code").length());
        summary.put("networkRequested", booleanArgument(arguments, "networkRequested"));
        summary.put("requestedHostsPresent", !requestedHosts.isEmpty());
        summary.put("requestedHostCount", requestedHosts.size());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_python, runtimeType=CODE_INTERPRETER"
                    + ", codeLength=" + argumentString(arguments, "code").length()
                    + ", networkRequested=" + booleanArgument(arguments, "networkRequested")
                    + ", requestedHostsPresent=" + !requestedHosts.isEmpty()
                    + ", requestedHostCount=" + requestedHosts.size()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeSandboxFileConvertArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String sourceFormat = argumentString(arguments, "sourceFormat");
        String targetFormat = argumentString(arguments, "targetFormat");
        String contentEncoding = argumentString(arguments, "contentEncoding", "plain");
        String safeSourceFormat = safeKnownValue(sourceFormat, SANDBOX_FILE_FORMATS);
        String safeTargetFormat = safeKnownValue(targetFormat, SANDBOX_FILE_FORMATS);
        String safeContentEncoding = safeKnownValue(contentEncoding, SANDBOX_FILE_CONTENT_ENCODINGS);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "FILE_CONVERSION");
        summary.put("sourceFormat", safeSourceFormat);
        summary.put("sourceFormatPresent", hasText(sourceFormat));
        summary.put("sourceFormatLength", sourceFormat.length());
        summary.put("targetFormat", safeTargetFormat);
        summary.put("targetFormatPresent", hasText(targetFormat));
        summary.put("targetFormatLength", targetFormat.length());
        summary.put("contentEncoding", safeContentEncoding);
        summary.put("contentEncodingPresent", hasText(contentEncoding));
        summary.put("contentEncodingLength", contentEncoding.length());
        summary.put("contentLength", argumentString(arguments, "content").length());
        summary.put("binaryInput", "base64".equals(safeContentEncoding));
        summary.put("networkRequested", false);
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_file_convert, runtimeType=FILE_CONVERSION"
                    + ", sourceFormat=" + safeSourceFormat
                    + ", sourceFormatPresent=" + hasText(sourceFormat)
                    + ", sourceFormatLength=" + sourceFormat.length()
                    + ", targetFormat=" + safeTargetFormat
                    + ", targetFormatPresent=" + hasText(targetFormat)
                    + ", targetFormatLength=" + targetFormat.length()
                    + ", contentEncoding=" + safeContentEncoding
                    + ", contentEncodingPresent=" + hasText(contentEncoding)
                    + ", contentEncodingLength=" + contentEncoding.length()
                    + ", contentLength=" + argumentString(arguments, "content").length()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeRemoteA2aArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> metadata = mapValue(arguments.get("metadata"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        String agentName = argumentString(arguments, "agentName");
        String metadataVersion = argumentString(metadata, "version");
        summary.put("agentNamePresent", hasText(agentName));
        summary.put("agentNameLength", agentName.length());
        summary.put("promptLength", argumentString(arguments, "prompt").length());
        summary.put("metadataKeys", safeArgumentKeys(metadata));
        summary.put("metadataCount", metadata.size());
        summary.put("metadataValueCount", mapValueCount(metadata));
        summary.put("metadataValueTotalLength", mapValueTotalLength(metadata));
        summary.put("metadataValueMaxLength", mapValueMaxLength(metadata));
        summary.put("versionPresent", hasText(metadataVersion));
        summary.put("versionLength", metadataVersion.length());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=invoke_remote_a2a_agent"
                    + ", agentNamePresent=" + hasText(agentName)
                    + ", agentNameLength=" + agentName.length()
                    + ", promptLength=" + argumentString(arguments, "prompt").length()
                    + ", metadataKeys=" + safeArgumentKeys(metadata)
                    + ", metadataCount=" + metadata.size()
                    + ", versionPresent=" + hasText(metadataVersion)
                    + ", versionLength=" + metadataVersion.length()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeSandboxBrowserArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String url = argumentString(arguments, "url");
        String html = argumentString(arguments, "html");
        boolean urlMode = hasText(url);
        List<String> allowedHosts = argumentStringList(arguments.get("allowedHosts"));
        int cookieCount = listSize(arguments.get("cookies"));
        Map<String, Object> sessionState = mapValue(arguments.get("sessionState"));
        int sessionCookieCount = listSize(sessionState.get("cookies"));
        int sessionOriginCount = listSize(sessionState.get("origins"));
        int sessionLocalStorageItemCount = sessionStateLocalStorageItemCount(sessionState.get("origins"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("mode", urlMode ? "url" : "inline");
        summary.put("action", safeSandboxBrowserAction(arguments));
        summary.put("networkRequested", urlMode);
        summary.put("urlPresent", urlMode);
        summary.put("urlLength", url.length());
        summary.put("urlQueryPresent", hasUrlQuery(url));
        summary.put("urlQueryLength", urlQueryLength(url));
        summary.put("htmlPresent", hasText(html));
        summary.put("htmlLength", html.length());
        summary.put("allowedHostCount", allowedHosts.size());
        summary.put("allowedHostsPresent", !allowedHosts.isEmpty());
        summary.put("cookieCount", cookieCount);
        summary.put("sessionStateReplayRequested", !sessionState.isEmpty());
        summary.put("sessionStateCookieCount", sessionCookieCount);
        summary.put("sessionStateOriginCount", sessionOriginCount);
        summary.put("sessionStateLocalStorageItemCount", sessionLocalStorageItemCount);
        summary.put("captureSessionState", booleanArgument(arguments, "captureSessionState"));
        summary.put("screenshot", booleanArgument(arguments, "screenshot", true));
        summary.put("har", booleanArgument(arguments, "har"));
        summary.put("video", booleanArgument(arguments, "video"));
        summary.put("viewportWidthPresent", arguments.containsKey("viewportWidth"));
        summary.put("viewportWidth", positiveIntArgument(arguments, "viewportWidth"));
        summary.put("viewportHeightPresent", arguments.containsKey("viewportHeight"));
        summary.put("viewportHeight", positiveIntArgument(arguments, "viewportHeight"));
        summary.put("argumentKeys", safeSandboxBrowserArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_browser, mode=" + (urlMode ? "url" : "inline")
                    + ", allowedHostCount=" + allowedHosts.size()
                    + ", cookieCount=" + cookieCount
                    + ", sessionStateReplayRequested=" + !sessionState.isEmpty()
                    + ", sessionStateCookieCount=" + sessionCookieCount
                    + ", sessionStateOriginCount=" + sessionOriginCount
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private List<String> safeSandboxBrowserArgumentKeys(Map<String, Object> arguments) {
        return SANDBOX_BROWSER_ARGUMENT_KEYS.stream()
                .filter(arguments::containsKey)
                .toList();
    }

    private boolean hasUrlQuery(String value) {
        return urlQueryLength(value) > 0;
    }

    private int urlQueryLength(String value) {
        if (!hasText(value)) {
            return 0;
        }
        try {
            String rawQuery = new URI(value).getRawQuery();
            return rawQuery == null ? 0 : rawQuery.length();
        } catch (URISyntaxException ex) {
            int queryStart = value.indexOf('?');
            if (queryStart < 0 || queryStart == value.length() - 1) {
                return 0;
            }
            int fragmentStart = value.indexOf('#', queryStart + 1);
            return (fragmentStart < 0 ? value.length() : fragmentStart) - queryStart - 1;
        }
    }

    private List<String> safeArgumentKeys(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }
        return arguments.keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::isSafePreviewArgumentKey)
                .sorted()
                .toList();
    }

    private List<String> safeResourceRefKeys(Map<String, String> resourceRefs) {
        if (resourceRefs == null || resourceRefs.isEmpty()) {
            return List.of();
        }
        return resourceRefs.keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::isSafePreviewArgumentKey)
                .sorted()
                .toList();
    }

    private boolean isSafePreviewArgumentKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_PREVIEW_ARGUMENT_KEY_LENGTH) {
            return false;
        }
        String lower = key.toLowerCase();
        if (lower.contains("secret") || lower.contains("token") || lower.contains("password")) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            boolean safe = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-'
                    || ch == '.';
            if (!safe) {
                return false;
            }
        }
        return true;
    }

    private String safeSandboxBrowserAction(Map<String, Object> arguments) {
        String action = argumentString(arguments, "action", "snapshot");
        if ("snapshot".equals(action) || "extract_text".equals(action) || "extract-text".equals(action)) {
            return action;
        }
        return "unsupported";
    }

    private String safeKnownValue(String value, List<String> allowedValues) {
        if (!hasText(value)) {
            return "absent";
        }
        String normalized = value.trim().toLowerCase();
        if (allowedValues.contains(normalized)) {
            return normalized;
        }
        return "unsupported";
    }

    private String canonicalResourceRefs(Map<String, String> resourceRefs) throws JsonProcessingException {
        Map<String, String> canonical = new LinkedHashMap<>();
        Objects.requireNonNullElse(resourceRefs, Map.<String, String>of()).entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> canonical.put(entry.getKey(), entry.getValue()));
        return OBJECT_MAPPER.writeValueAsString(canonical);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String argumentString(Map<String, Object> arguments, String name) {
        return argumentString(arguments, name, "");
    }

    private String argumentString(Map<String, Object> arguments, String name, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString().trim();
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name) {
        return booleanArgument(arguments, name, false);
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private int positiveIntArgument(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int listSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    private int sessionStateLocalStorageItemCount(Object value) {
        if (!(value instanceof Collection<?> origins)) {
            return 0;
        }
        int count = 0;
        for (Object origin : origins) {
            if (origin instanceof Map<?, ?> originMap) {
                count += listSize(originMap.get("localStorage"));
            }
        }
        return count;
    }

    private int mapValueCount(Map<String, Object> values) {
        return values == null ? 0 : values.size();
    }

    private int mapValueTotalLength(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(value -> value.toString().length())
                .sum();
    }

    private int mapValueMaxLength(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(value -> value.toString().length())
                .max()
                .orElse(0);
    }

    private List<String> argumentStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.toString().trim())
                    .filter(LocalToolGatewayPort::hasText)
                    .toList();
        }
        if (value instanceof String text && hasText(text)) {
            List<String> items = new ArrayList<>();
            for (String item : text.split(",")) {
                if (hasText(item)) {
                    items.add(item.trim());
                }
            }
            return items;
        }
        return List.of();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(key.toString(), item);
            }
        });
        return result;
    }

    private Map<String, Object> mergeMaps(Object first, Object second) {
        Map<String, Object> result = new LinkedHashMap<>(mapValue(first));
        result.putAll(mapValue(second));
        return result;
    }

    private String valueType(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof Collection<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String summarizeResult(ToolInvocationResult result, String auditError) {
        if (result == null) {
            return null;
        }
        if (!result.success()) {
            return summarizeFailedResult(result, auditError);
        }
        String content = Objects.requireNonNullElse(result.content(), "");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contentPresent", result.content() != null);
        summary.put("contentLength", content.length());
        summary.put("contentJsonType", result.content() == null ? "none" : resultContentJsonType(content));
        JsonTopLevelShape jsonTopLevelShape = resultContentJsonTopLevelShape(content);
        if (jsonTopLevelShape != null) {
            summary.put("contentJsonTopLevelFieldCount", jsonTopLevelShape.fieldCount());
            summary.put("contentJsonTopLevelElementCount", jsonTopLevelShape.elementCount());
        }
        JsonValueShape jsonValueShape = resultContentJsonValueShape(content);
        if (jsonValueShape != null) {
            summary.put("contentJsonValueCount", jsonValueShape.count());
            summary.put("contentJsonValueTotalLength", jsonValueShape.totalLength());
            summary.put("contentJsonValueMaxLength", jsonValueShape.maxLength());
        }
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("contentPresent=true"
                    + ", contentLength=" + content.length()
                    + ", contentJsonType=" + resultContentJsonType(content));
        }
    }

    private JsonTopLevelShape resultContentJsonTopLevelShape(String content) {
        if (!hasText(content)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node.isObject()) {
                return new JsonTopLevelShape(node.size(), 0);
            }
            if (node.isArray()) {
                return new JsonTopLevelShape(0, node.size());
            }
            return new JsonTopLevelShape(0, 0);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record JsonTopLevelShape(int fieldCount, int elementCount) {
    }

    private String summarizeFailedResult(ToolInvocationResult result, String auditError) {
        String error = Objects.requireNonNullElse(auditError, "");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contentPresent", false);
        summary.put("errorPresent", hasText(error));
        summary.put("errorLength", error.length());
        summary.put("approvalIdPresent", hasText(result.approvalId()));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("contentPresent=false"
                    + ", errorPresent=" + hasText(error)
                    + ", errorLength=" + error.length()
                    + ", approvalIdPresent=" + hasText(result.approvalId()));
        }
    }

    private JsonValueShape resultContentJsonValueShape(String content) {
        if (!hasText(content)) {
            return null;
        }
        try {
            return jsonValueShape(OBJECT_MAPPER.readTree(content));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private JsonValueShape jsonValueShape(JsonNode node) {
        if (node == null || node.isNull()) {
            return new JsonValueShape(1, 4, 4);
        }
        if (node.isObject()) {
            int count = 0;
            int totalLength = 0;
            int maxLength = 0;
            for (JsonNode child : node) {
                JsonValueShape shape = jsonValueShape(child);
                count += shape.count();
                totalLength += shape.totalLength();
                maxLength = Math.max(maxLength, shape.maxLength());
            }
            return new JsonValueShape(count, totalLength, maxLength);
        }
        if (node.isArray()) {
            int count = 0;
            int totalLength = 0;
            int maxLength = 0;
            for (JsonNode child : node) {
                JsonValueShape shape = jsonValueShape(child);
                count += shape.count();
                totalLength += shape.totalLength();
                maxLength = Math.max(maxLength, shape.maxLength());
            }
            return new JsonValueShape(count, totalLength, maxLength);
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return new JsonValueShape(1, value.length(), value.length());
    }

    private record JsonValueShape(int count, int totalLength, int maxLength) {
    }

    private String resultContentJsonType(String content) {
        if (!hasText(content)) {
            return "empty";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node.isObject()) {
                return "object";
            }
            if (node.isArray()) {
                return "array";
            }
            if (node.isTextual()) {
                return "string";
            }
            if (node.isNumber()) {
                return "number";
            }
            if (node.isBoolean()) {
                return "boolean";
            }
            if (node.isNull()) {
                return "null";
            }
            return "json";
        } catch (JsonProcessingException ex) {
            return "text";
        }
    }

    private String auditErrorMessage(String errorMessage) {
        if (!hasText(errorMessage)) {
            return errorMessage;
        }
        return truncate(CredentialTextRedactor.redact(errorMessage));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
