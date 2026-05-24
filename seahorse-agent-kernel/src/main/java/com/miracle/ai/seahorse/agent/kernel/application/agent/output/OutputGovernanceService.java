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

package com.miracle.ai.seahorse.agent.kernel.application.agent.output;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 输出治理应用服务。
 *
 * <p>负责协调一个或多个 {@link OutputValidatorPort}，汇总决策，记录运行并发布观测事件。
 * 此服务是 kernel application 内部能力，{@code KernelAgentLoop} 只调用 {@link #governFinalAnswer}，
 * 不持有具体的 schema / DDL / Markdown / Mermaid 规则。
 *
 * <p>Slice 1a 仅接入 JSON validator；Slice 1b 起追加 DDL/Markdown/Mermaid validators；
 * Slice 1c 起接入 self-heal 路径。
 */
public final class OutputGovernanceService {

    public static final String OBSERVATION_VALIDATION_EVENT = "agent-output-validation";
    public static final String OBSERVATION_VALIDATION_FAILED_EVENT = "agent-output-validation-failed";

    public static final String OBSERVATION_ATTR_ARTIFACT_TYPE = "artifactType";
    public static final String OBSERVATION_ATTR_DECISION = "decision";
    public static final String OBSERVATION_ATTR_VALIDATOR = "validator";
    public static final String OBSERVATION_ATTR_ISSUE_CODE = "topIssueCode";
    public static final String OBSERVATION_ATTR_AGENT_ID = "agentId";
    public static final String OBSERVATION_ATTR_TENANT_ID = "tenantId";

    private static final String VALIDATOR_NONE = "none";
    private static final String FALLBACK_BLOCK_MESSAGE =
            "Output blocked by governance: structural validation failed.";

    private final List<OutputValidatorPort> validators;
    private final OutputValidationRecordPort recordPort;
    private final ObservationPort observationPort;
    private final String blockFallbackMessage;

    public OutputGovernanceService(List<OutputValidatorPort> validators,
                                   OutputValidationRecordPort recordPort,
                                   ObservationPort observationPort) {
        this(validators, recordPort, observationPort, FALLBACK_BLOCK_MESSAGE);
    }

    public OutputGovernanceService(List<OutputValidatorPort> validators,
                                   OutputValidationRecordPort recordPort,
                                   ObservationPort observationPort,
                                   String blockFallbackMessage) {
        this.validators = validators == null ? List.of() : List.copyOf(validators);
        this.recordPort = Objects.requireNonNullElseGet(recordPort, OutputValidationRecordPort::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.blockFallbackMessage = blockFallbackMessage == null || blockFallbackMessage.isBlank()
                ? FALLBACK_BLOCK_MESSAGE
                : blockFallbackMessage;
    }

    /**
     * 治理 final answer。
     *
     * <p>如果没有任何 validator 支持当前请求（例如 artifactType=PLAIN_TEXT 或未配置 validator），
     * 服务直接返回 {@link OutputValidationDecision#PASS}，确保 1a 引入对未配置场景行为不变。
     */
    public OutputGovernanceResult governFinalAnswer(OutputValidationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<OutputValidatorPort> applicable = pickApplicableValidators(request);
        if (applicable.isEmpty()) {
            OutputGovernanceResult passResult = new OutputGovernanceResult(
                    OutputValidationDecision.PASS,
                    request.content(),
                    request.content(),
                    List.of(),
                    VALIDATOR_NONE);
            emitValidationEvent(request, passResult, null);
            recordPort.record(request, passResult);
            return passResult;
        }

        List<OutputValidationIssue> aggregatedIssues = new ArrayList<>();
        OutputValidationDecision aggregatedDecision = OutputValidationDecision.PASS;
        String normalizedContent = null;
        String triggeringValidator = VALIDATOR_NONE;

        for (OutputValidatorPort validator : applicable) {
            OutputValidationResult result = safeValidate(validator, request);
            aggregatedIssues.addAll(result.issues());
            if (result.normalizedContent() != null && normalizedContent == null) {
                normalizedContent = result.normalizedContent();
            }
            if (rank(result.decision()) > rank(aggregatedDecision)) {
                aggregatedDecision = result.decision();
                triggeringValidator = validator.name();
            }
            if (aggregatedDecision == OutputValidationDecision.BLOCK) {
                // Slice 1a: 第一个 BLOCK 即停止后续 validator，避免冗余成本。
                break;
            }
        }

        String governedContent = switch (aggregatedDecision) {
            case BLOCK -> blockFallbackMessage;
            case WARN, PASS -> Objects.requireNonNullElse(normalizedContent, request.content());
        };

        OutputGovernanceResult finalResult = new OutputGovernanceResult(
                aggregatedDecision,
                governedContent,
                request.content(),
                aggregatedIssues,
                aggregatedDecision == OutputValidationDecision.PASS && triggeringValidator.equals(VALIDATOR_NONE)
                        ? applicable.get(0).name()
                        : triggeringValidator);
        emitValidationEvent(request, finalResult, aggregatedIssues.isEmpty() ? null : aggregatedIssues.get(0));
        recordPort.record(request, finalResult);
        return finalResult;
    }

    private List<OutputValidatorPort> pickApplicableValidators(OutputValidationRequest request) {
        if (validators.isEmpty()) {
            return Collections.emptyList();
        }
        List<OutputValidatorPort> applicable = new ArrayList<>();
        for (OutputValidatorPort validator : validators) {
            try {
                if (validator.supports(request)) {
                    applicable.add(validator);
                }
            } catch (RuntimeException ex) {
                // validator 自身故障不应阻断整个治理，跳过即可。
                emitValidationEvent(
                        request,
                        new OutputGovernanceResult(
                                OutputValidationDecision.WARN,
                                request.content(),
                                request.content(),
                                List.of(new OutputValidationIssue(
                                        "VALIDATOR_SUPPORTS_FAILED",
                                        "",
                                        ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage(),
                                        OutputValidationDecision.WARN)),
                                validator.name()),
                        null);
            }
        }
        return applicable;
    }

    private OutputValidationResult safeValidate(OutputValidatorPort validator, OutputValidationRequest request) {
        try {
            OutputValidationResult result = validator.validate(request);
            return result == null ? OutputValidationResult.pass() : result;
        } catch (RuntimeException ex) {
            OutputValidationIssue issue = new OutputValidationIssue(
                    "VALIDATOR_RUNTIME_FAILURE",
                    validator.name(),
                    ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage(),
                    OutputValidationDecision.WARN);
            return OutputValidationResult.warn(List.of(issue));
        }
    }

    private void emitValidationEvent(OutputValidationRequest request,
                                     OutputGovernanceResult result,
                                     OutputValidationIssue topIssue) {
        Map<String, String> attributes = baseAttributes(request, result);
        observationPort.recordEvent(new ObservationEvent(
                OBSERVATION_VALIDATION_EVENT,
                Instant.now(),
                ObservationEvent.DEFAULT_AMOUNT,
                attributes));
        if (result.decision() == OutputValidationDecision.BLOCK && topIssue != null) {
            Map<String, String> failedAttributes = new LinkedHashMap<>(attributes);
            failedAttributes.put(OBSERVATION_ATTR_ISSUE_CODE, topIssue.code());
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_VALIDATION_FAILED_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    failedAttributes));
        }
    }

    private Map<String, String> baseAttributes(OutputValidationRequest request, OutputGovernanceResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        OutputArtifactType artifactType = request.artifactType();
        attributes.put(OBSERVATION_ATTR_ARTIFACT_TYPE, artifactType == null ? "" : artifactType.name());
        attributes.put(OBSERVATION_ATTR_DECISION, result.decision().name());
        attributes.put(OBSERVATION_ATTR_VALIDATOR, result.validatorName());
        if (request.agentId() != null && !request.agentId().isBlank()) {
            attributes.put(OBSERVATION_ATTR_AGENT_ID, request.agentId());
        }
        if (request.tenantId() != null && !request.tenantId().isBlank()) {
            attributes.put(OBSERVATION_ATTR_TENANT_ID, request.tenantId());
        }
        return attributes;
    }

    private static int rank(OutputValidationDecision decision) {
        return switch (decision) {
            case PASS -> 0;
            case WARN -> 1;
            case BLOCK -> 2;
        };
    }
}
