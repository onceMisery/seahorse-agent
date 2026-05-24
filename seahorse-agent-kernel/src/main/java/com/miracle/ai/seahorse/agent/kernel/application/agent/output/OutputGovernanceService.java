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
import java.util.Optional;

/**
 * 输出治理应用服务。
 *
 * <p>负责协调一个或多个 {@link OutputValidatorPort}，汇总决策，记录运行并发布观测事件。
 * 此服务是 kernel application 内部能力，{@code KernelAgentLoop} 只调用 {@link #governFinalAnswer}，
 * 不持有具体的 schema / DDL / Markdown / Mermaid 规则。
 *
 * <p>Slice 1a 仅接入 JSON validator；Slice 1b 起追加 DDL validator；
 * Slice 1c 起可选启用 {@link SelfHealingOutputRepairService}，在 BLOCK 后做一次修复尝试。
 */
public final class OutputGovernanceService {

    public static final String OBSERVATION_VALIDATION_EVENT = "agent-output-validation";
    public static final String OBSERVATION_VALIDATION_FAILED_EVENT = "agent-output-validation-failed";
    public static final String OBSERVATION_SELF_HEAL_EVENT = "agent-output-self-heal";

    public static final String OBSERVATION_ATTR_ARTIFACT_TYPE = "artifactType";
    public static final String OBSERVATION_ATTR_DECISION = "decision";
    public static final String OBSERVATION_ATTR_VALIDATOR = "validator";
    public static final String OBSERVATION_ATTR_ISSUE_CODE = "topIssueCode";
    public static final String OBSERVATION_ATTR_AGENT_ID = "agentId";
    public static final String OBSERVATION_ATTR_TENANT_ID = "tenantId";
    public static final String OBSERVATION_ATTR_ATTEMPT = "attempt";
    public static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    public static final String OBSERVATION_ATTR_ISSUE_COUNT = "issueCount";

    public static final String SELF_HEAL_OUTCOME_HEALED = "healed";
    public static final String SELF_HEAL_OUTCOME_FAILED = "failed";
    public static final String SELF_HEAL_OUTCOME_SKIPPED = "skipped";

    private static final String VALIDATOR_NONE = "none";
    private static final String FALLBACK_BLOCK_MESSAGE =
            "Output blocked by governance: structural validation failed.";

    private final List<OutputValidatorPort> validators;
    private final OutputValidationRecordPort recordPort;
    private final ObservationPort observationPort;
    private final String blockFallbackMessage;
    private final SelfHealingOutputRepairService selfHealingService;

    public OutputGovernanceService(List<OutputValidatorPort> validators,
                                   OutputValidationRecordPort recordPort,
                                   ObservationPort observationPort) {
        this(validators, recordPort, observationPort, FALLBACK_BLOCK_MESSAGE, null);
    }

    public OutputGovernanceService(List<OutputValidatorPort> validators,
                                   OutputValidationRecordPort recordPort,
                                   ObservationPort observationPort,
                                   String blockFallbackMessage) {
        this(validators, recordPort, observationPort, blockFallbackMessage, null);
    }

    public OutputGovernanceService(List<OutputValidatorPort> validators,
                                   OutputValidationRecordPort recordPort,
                                   ObservationPort observationPort,
                                   String blockFallbackMessage,
                                   SelfHealingOutputRepairService selfHealingService) {
        this.validators = validators == null ? List.of() : List.copyOf(validators);
        this.recordPort = Objects.requireNonNullElseGet(recordPort, OutputValidationRecordPort::noop);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
        this.blockFallbackMessage = blockFallbackMessage == null || blockFallbackMessage.isBlank()
                ? FALLBACK_BLOCK_MESSAGE
                : blockFallbackMessage;
        this.selfHealingService = selfHealingService;
    }

    /**
     * 治理 final answer。
     *
     * <p>如果没有任何 validator 支持当前请求（例如 artifactType=PLAIN_TEXT 或未配置 validator），
     * 服务直接返回 {@link OutputValidationDecision#PASS}，确保引入对未配置场景行为不变。
     *
     * <p>若初次校验 BLOCK 并且配置了 {@link SelfHealingOutputRepairService}，则发起一次修复并
     * 重新校验：通过即 HEALED，否则 FAILED_AFTER_HEAL。修复始终最多一次。
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

        ValidatorRunOutcome initial = runValidators(request, applicable);
        if (initial.decision() != OutputValidationDecision.BLOCK || selfHealingService == null) {
            OutputGovernanceResult finalResult = buildGovernanceResult(request, initial, applicable);
            emitValidationEvent(request, finalResult,
                    initial.issues().isEmpty() ? null : initial.issues().get(0));
            recordPort.record(request, finalResult);
            return finalResult;
        }

        Optional<String> repaired = selfHealingService.repairOnce(request, initial.issues());
        if (repaired.isEmpty()) {
            OutputGovernanceResult failedResult = buildBlockedAfterHealResult(
                    request, initial, applicable, request.content());
            emitValidationEvent(request, blockResultFor(initial, applicable, request),
                    initial.issues().get(0));
            emitSelfHealEvent(request, SELF_HEAL_OUTCOME_FAILED, initial.issues().size());
            recordPort.record(request, failedResult);
            return failedResult;
        }

        OutputValidationRequest repairedRequest = withContent(request, repaired.get());
        ValidatorRunOutcome retry = runValidators(repairedRequest, applicable);
        if (retry.decision() == OutputValidationDecision.PASS
                || retry.decision() == OutputValidationDecision.WARN) {
            OutputGovernanceResult healedResult = new OutputGovernanceResult(
                    OutputValidationDecision.HEALED,
                    Objects.requireNonNullElse(retry.normalizedContent(), repaired.get()),
                    request.content(),
                    retry.issues(),
                    selfHealingService.repairModelName());
            emitValidationEvent(request, healedResult,
                    retry.issues().isEmpty() ? null : retry.issues().get(0));
            emitSelfHealEvent(request, SELF_HEAL_OUTCOME_HEALED, initial.issues().size());
            recordPort.record(request, healedResult);
            return healedResult;
        }

        OutputGovernanceResult failedAfterHeal = buildBlockedAfterHealResult(
                request, retry, applicable, repaired.get());
        emitValidationEvent(request, blockResultFor(retry, applicable, request),
                retry.issues().isEmpty() ? null : retry.issues().get(0));
        emitSelfHealEvent(request, SELF_HEAL_OUTCOME_FAILED, retry.issues().size());
        recordPort.record(request, failedAfterHeal);
        return failedAfterHeal;
    }

    private OutputGovernanceResult buildGovernanceResult(OutputValidationRequest request,
                                                          ValidatorRunOutcome outcome,
                                                          List<OutputValidatorPort> applicable) {
        String governedContent = switch (outcome.decision()) {
            case BLOCK, FAILED_AFTER_HEAL -> blockFallbackMessage;
            case PASS, WARN, HEALED -> Objects.requireNonNullElse(outcome.normalizedContent(), request.content());
        };
        String validatorName = outcome.decision() == OutputValidationDecision.PASS
                && outcome.triggeringValidator().equals(VALIDATOR_NONE)
                ? applicable.get(0).name()
                : outcome.triggeringValidator();
        return new OutputGovernanceResult(
                outcome.decision(),
                governedContent,
                request.content(),
                outcome.issues(),
                validatorName);
    }

    private OutputGovernanceResult buildBlockedAfterHealResult(OutputValidationRequest request,
                                                                ValidatorRunOutcome outcome,
                                                                List<OutputValidatorPort> applicable,
                                                                String attemptedContent) {
        String validatorName = outcome.triggeringValidator().equals(VALIDATOR_NONE)
                ? applicable.get(0).name()
                : outcome.triggeringValidator();
        return new OutputGovernanceResult(
                OutputValidationDecision.FAILED_AFTER_HEAL,
                blockFallbackMessage,
                attemptedContent,
                outcome.issues(),
                validatorName);
    }

    private OutputGovernanceResult blockResultFor(ValidatorRunOutcome outcome,
                                                   List<OutputValidatorPort> applicable,
                                                   OutputValidationRequest request) {
        String validatorName = outcome.triggeringValidator().equals(VALIDATOR_NONE)
                ? applicable.get(0).name()
                : outcome.triggeringValidator();
        return new OutputGovernanceResult(
                OutputValidationDecision.BLOCK,
                blockFallbackMessage,
                request.content(),
                outcome.issues(),
                validatorName);
    }

    private ValidatorRunOutcome runValidators(OutputValidationRequest request,
                                               List<OutputValidatorPort> applicable) {
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
                break;
            }
        }
        return new ValidatorRunOutcome(aggregatedDecision, aggregatedIssues, normalizedContent, triggeringValidator);
    }

    private OutputValidationRequest withContent(OutputValidationRequest request, String content) {
        return new OutputValidationRequest(
                request.runId(),
                request.agentId(),
                request.tenantId(),
                request.userId(),
                request.artifactType(),
                request.schemaJson(),
                content,
                request.attributes());
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
        if (isFailureDecision(result.decision()) && topIssue != null) {
            Map<String, String> failedAttributes = new LinkedHashMap<>(attributes);
            failedAttributes.put(OBSERVATION_ATTR_ISSUE_CODE, topIssue.code());
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_VALIDATION_FAILED_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    failedAttributes));
        }
    }

    private void emitSelfHealEvent(OutputValidationRequest request, String outcome, int issueCount) {
        Map<String, String> attributes = new LinkedHashMap<>();
        OutputArtifactType artifactType = request.artifactType();
        attributes.put(OBSERVATION_ATTR_ARTIFACT_TYPE, artifactType == null ? "" : artifactType.name());
        attributes.put(OBSERVATION_ATTR_ATTEMPT, "1");
        attributes.put(OBSERVATION_ATTR_OUTCOME, outcome);
        attributes.put(OBSERVATION_ATTR_ISSUE_COUNT, Integer.toString(issueCount));
        if (request.agentId() != null && !request.agentId().isBlank()) {
            attributes.put(OBSERVATION_ATTR_AGENT_ID, request.agentId());
        }
        if (request.tenantId() != null && !request.tenantId().isBlank()) {
            attributes.put(OBSERVATION_ATTR_TENANT_ID, request.tenantId());
        }
        observationPort.recordEvent(new ObservationEvent(
                OBSERVATION_SELF_HEAL_EVENT,
                Instant.now(),
                ObservationEvent.DEFAULT_AMOUNT,
                attributes));
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

    private static boolean isFailureDecision(OutputValidationDecision decision) {
        return decision == OutputValidationDecision.BLOCK
                || decision == OutputValidationDecision.FAILED_AFTER_HEAL;
    }

    private static int rank(OutputValidationDecision decision) {
        return switch (decision) {
            case PASS -> 0;
            case WARN -> 1;
            // HEALED 仅由 governance 汇总后产生，validator 不会返回此值；为穷尽 switch 而映射为 WARN 等级。
            case HEALED -> 1;
            case BLOCK -> 2;
            // FAILED_AFTER_HEAL 仅由 governance 汇总后产生，validator 不会返回此值；映射为最严重等级。
            case FAILED_AFTER_HEAL -> 3;
        };
    }

    private record ValidatorRunOutcome(OutputValidationDecision decision,
                                        List<OutputValidationIssue> issues,
                                        String normalizedContent,
                                        String triggeringValidator) {
    }
}
