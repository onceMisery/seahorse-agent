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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAgentDefinitionEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAuditEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessEvalEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessQuotaEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessResourceAclEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessRollbackEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessToolRiskEvidencePort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

final class ConservativeReadinessEvidenceAdapters {

    private static final String EVIDENCE_PREFIX = "readiness";
    private static final String AGENT_DEFINITION_EVIDENCE = "agent-definition";
    private static final String TOOL_RISK_EVIDENCE = "tool-risk";
    private static final String RESOURCE_ACL_EVIDENCE = "resource-acl";
    private static final String EVAL_EVIDENCE = "eval";
    private static final String QUOTA_EVIDENCE = "quota";
    private static final String AUDIT_EVIDENCE = "audit";
    private static final String ROLLBACK_EVIDENCE = "rollback";
    private static final String FOUND_EVIDENCE = "found";
    private static final String MISSING_EVIDENCE = "missing";
    private static final String PROVIDER_NOT_CONFIGURED_EVIDENCE = "provider-not-configured";
    private static final String OWNER_READY_MESSAGE = "Agent owner evidence is available";
    private static final String OWNER_MISSING_MESSAGE = "Agent owner evidence is missing";
    private static final String VERSION_READY_MESSAGE = "Published version evidence is available";
    private static final String VERSION_MISSING_MESSAGE = "Published version evidence is missing";
    private static final String VERSION_DISABLED_MESSAGE = "Agent version is disabled";
    private static final String DISABLE_SWITCH_READY_MESSAGE = "Agent disable switch is available";
    private static final String DISABLE_SWITCH_MISSING_MESSAGE = "Agent disable switch evidence is missing";
    private static final String TOOL_RISK_MESSAGE = "Tool risk review evidence provider is not configured";
    private static final String RESOURCE_ACL_MESSAGE = "Resource ACL evidence provider is not configured";
    private static final String EVAL_MESSAGE = "Eval summary evidence provider is not configured";
    private static final String QUOTA_MESSAGE = "Quota evidence provider is not configured";
    private static final String AUDIT_MESSAGE = "Audit evidence provider is not configured";
    private static final String ROLLBACK_MESSAGE = "Rollback target evidence provider is not configured";

    private ConservativeReadinessEvidenceAdapters() {
    }

    static ReadinessAgentDefinitionEvidencePort agentDefinition(AgentDefinitionRepositoryPort repository) {
        AgentDefinitionRepositoryPort safeRepository = Objects.requireNonNull(repository,
                "repository must not be null");
        return (tenantId, agentId, versionId, checkedAt) -> {
            Instant safeCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
            return safeRepository.findById(agentId)
                    .filter(definition -> definition.tenantId().equals(tenantId))
                    .map(definition -> definitionResults(safeRepository, definition, versionId, safeCheckedAt))
                    .orElseGet(() -> missingDefinitionResults(agentId, safeCheckedAt));
        };
    }

    static ReadinessToolRiskEvidencePort toolRisk() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.TOOL_RISK,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.TOOL_RISK_UNREVIEWED,
                TOOL_RISK_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                TOOL_RISK_MESSAGE,
                checkedAt);
    }

    static ReadinessResourceAclEvidencePort resourceAcl() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.RESOURCE_ACL,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.RESOURCE_ACL_MISSING,
                RESOURCE_ACL_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                RESOURCE_ACL_MESSAGE,
                checkedAt);
    }

    static ReadinessEvalEvidencePort eval() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.EVAL,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.EVAL_MISSING,
                EVAL_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                EVAL_MESSAGE,
                checkedAt);
    }

    static ReadinessQuotaEvidencePort quota() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.QUOTA,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.QUOTA_MISSING,
                QUOTA_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                QUOTA_MESSAGE,
                checkedAt);
    }

    static ReadinessAuditEvidencePort audit() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.AUDIT,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.AUDIT_MISSING,
                AUDIT_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                AUDIT_MESSAGE,
                checkedAt);
    }

    static ReadinessRollbackEvidencePort rollback() {
        return (tenantId, agentId, versionId, checkedAt) -> result(
                EnterprisePilotReadinessCheckCode.ROLLBACK,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.ROLLBACK_TARGET_MISSING,
                ROLLBACK_EVIDENCE,
                PROVIDER_NOT_CONFIGURED_EVIDENCE,
                ROLLBACK_MESSAGE,
                checkedAt);
    }

    private static List<EnterprisePilotReadinessCheckResult> definitionResults(
            AgentDefinitionRepositoryPort repository,
            AgentDefinition definition,
            String versionId,
            Instant checkedAt) {
        return List.of(
                ownerResult(definition, checkedAt),
                publishedVersionResult(repository, definition, versionId, checkedAt),
                disableSwitchResult(definition, checkedAt));
    }

    private static List<EnterprisePilotReadinessCheckResult> missingDefinitionResults(String agentId,
                                                                                      Instant checkedAt) {
        return List.of(
                result(
                        EnterprisePilotReadinessCheckCode.OWNER,
                        EnterprisePilotReadinessStatus.FAIL,
                        EnterprisePilotReadinessReasonCode.OWNER_MISSING,
                        AGENT_DEFINITION_EVIDENCE,
                        MISSING_EVIDENCE,
                        OWNER_MISSING_MESSAGE,
                        checkedAt),
                result(
                        EnterprisePilotReadinessCheckCode.PUBLISHED_VERSION,
                        EnterprisePilotReadinessStatus.FAIL,
                        EnterprisePilotReadinessReasonCode.PUBLISHED_VERSION_MISSING,
                        AGENT_DEFINITION_EVIDENCE,
                        MISSING_EVIDENCE,
                        VERSION_MISSING_MESSAGE,
                        checkedAt),
                result(
                        EnterprisePilotReadinessCheckCode.DISABLE_SWITCH,
                        EnterprisePilotReadinessStatus.FAIL,
                        EnterprisePilotReadinessReasonCode.DISABLE_SWITCH_MISSING,
                        AGENT_DEFINITION_EVIDENCE,
                        MISSING_EVIDENCE,
                        DISABLE_SWITCH_MISSING_MESSAGE,
                        checkedAt));
    }

    private static EnterprisePilotReadinessCheckResult ownerResult(AgentDefinition definition,
                                                                   Instant checkedAt) {
        if (hasText(definition.ownerUserId())) {
            return result(
                    EnterprisePilotReadinessCheckCode.OWNER,
                    EnterprisePilotReadinessStatus.PASS,
                    EnterprisePilotReadinessReasonCode.READY,
                    AGENT_DEFINITION_EVIDENCE,
                    FOUND_EVIDENCE,
                    OWNER_READY_MESSAGE,
                    checkedAt);
        }
        return result(
                EnterprisePilotReadinessCheckCode.OWNER,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.OWNER_MISSING,
                AGENT_DEFINITION_EVIDENCE,
                MISSING_EVIDENCE,
                OWNER_MISSING_MESSAGE,
                checkedAt);
    }

    private static EnterprisePilotReadinessCheckResult publishedVersionResult(
            AgentDefinitionRepositoryPort repository,
            AgentDefinition definition,
            String versionId,
            Instant checkedAt) {
        if (definition.disabled()) {
            return result(
                    EnterprisePilotReadinessCheckCode.PUBLISHED_VERSION,
                    EnterprisePilotReadinessStatus.FAIL,
                    EnterprisePilotReadinessReasonCode.VERSION_DISABLED,
                    AGENT_DEFINITION_EVIDENCE,
                    FOUND_EVIDENCE,
                    VERSION_DISABLED_MESSAGE,
                    checkedAt);
        }
        if (hasText(versionId) && repository.findVersion(definition.agentId(), versionId).isPresent()) {
            return result(
                    EnterprisePilotReadinessCheckCode.PUBLISHED_VERSION,
                    EnterprisePilotReadinessStatus.PASS,
                    EnterprisePilotReadinessReasonCode.READY,
                    AGENT_DEFINITION_EVIDENCE,
                    FOUND_EVIDENCE,
                    VERSION_READY_MESSAGE,
                    checkedAt);
        }
        return result(
                EnterprisePilotReadinessCheckCode.PUBLISHED_VERSION,
                EnterprisePilotReadinessStatus.FAIL,
                EnterprisePilotReadinessReasonCode.PUBLISHED_VERSION_MISSING,
                AGENT_DEFINITION_EVIDENCE,
                MISSING_EVIDENCE,
                VERSION_MISSING_MESSAGE,
                checkedAt);
    }

    private static EnterprisePilotReadinessCheckResult disableSwitchResult(AgentDefinition definition,
                                                                           Instant checkedAt) {
        if (definition.disabled()) {
            return result(
                    EnterprisePilotReadinessCheckCode.DISABLE_SWITCH,
                    EnterprisePilotReadinessStatus.FAIL,
                    EnterprisePilotReadinessReasonCode.VERSION_DISABLED,
                    AGENT_DEFINITION_EVIDENCE,
                    FOUND_EVIDENCE,
                    VERSION_DISABLED_MESSAGE,
                    checkedAt);
        }
        return result(
                EnterprisePilotReadinessCheckCode.DISABLE_SWITCH,
                EnterprisePilotReadinessStatus.PASS,
                EnterprisePilotReadinessReasonCode.READY,
                AGENT_DEFINITION_EVIDENCE,
                FOUND_EVIDENCE,
                DISABLE_SWITCH_READY_MESSAGE,
                checkedAt);
    }

    private static EnterprisePilotReadinessCheckResult result(EnterprisePilotReadinessCheckCode code,
                                                              EnterprisePilotReadinessStatus status,
                                                              EnterprisePilotReadinessReasonCode reasonCode,
                                                              String evidenceType,
                                                              String evidenceState,
                                                              String message,
                                                              Instant checkedAt) {
        return new EnterprisePilotReadinessCheckResult(
                code,
                status,
                reasonCode,
                evidenceRef(evidenceType, evidenceState),
                message,
                Objects.requireNonNull(checkedAt, "checkedAt must not be null"));
    }

    private static String evidenceRef(String evidenceType, String evidenceState) {
        return EVIDENCE_PREFIX + ":" + evidenceType + ":" + evidenceState;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
