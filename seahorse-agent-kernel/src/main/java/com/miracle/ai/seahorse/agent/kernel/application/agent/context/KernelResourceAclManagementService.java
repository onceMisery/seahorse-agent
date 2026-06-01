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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclAuthorizationRoles;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportDryRunItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportDryRunReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportItemStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclNaturalKey;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportDryRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRulePage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelResourceAclManagementService implements ResourceAclManagementInboundPort {

    private static final String RULE_ID_PREFIX = "racl_";
    private static final String AUDIT_ID_PREFIX = "audit_";
    private static final String RESOURCE_TYPE_ACL_RULE = "RESOURCE_ACL_RULE";
    private static final String RESOURCE_TYPE_ACL_IMPORT = "RESOURCE_ACL_IMPORT";
    private static final String RESOURCE_ID_IMPORT = "resource-acl-import";
    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_DISABLE = "DISABLE";
    private static final String AUDIT_OPERATION_IMPORT = "IMPORT";

    private final ResourceAclRepositoryPort repository;
    private final CurrentUserPort currentUserPort;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;

    public KernelResourceAclManagementService(ResourceAclRepositoryPort repository,
                                              CurrentUserPort currentUserPort,
                                              Clock clock) {
        this(repository, currentUserPort, null, clock);
    }

    public KernelResourceAclManagementService(ResourceAclRepositoryPort repository,
                                              CurrentUserPort currentUserPort,
                                              KernelAuditLedgerService auditLedger,
                                              Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ResourceAclRule create(ResourceAclCreateCommand command) {
        CurrentUser currentUser = requireAdmin();
        ResourceAclCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        ResourceAclRule created = repository.save(new ResourceAclRule(
                RULE_ID_PREFIX + SnowflakeIds.nextIdString(),
                safeCommand.tenantId(),
                ResourceAclRuleScope.EXACT_RESOURCE,
                safeCommand.resourceType(),
                safeCommand.resourceId(),
                safeCommand.subjectType(),
                safeCommand.subjectId(),
                safeCommand.action(),
                safeCommand.effect(),
                ResourceAclRuleStatus.ENABLED,
                safeCommand.priority(),
                safeCommand.expiresAt(),
                String.valueOf(currentUser.userId()),
                now,
                now));
        appendRuleAudit(currentUser, created, AUDIT_OPERATION_CREATE);
        return created;
    }

    @Override
    public ResourceAclRule disable(String ruleId) {
        requireAdmin();
        ResourceAclRule current = repository.findById(requireText(ruleId, "ruleId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Resource ACL rule not found"));
        ResourceAclRule disabled = current.disable(clock.instant());
        if (disabled == current) {
            return current;
        }
        ResourceAclRule saved = repository.save(disabled);
        appendRuleAudit(currentUserPort.requireCurrentUser(), saved, AUDIT_OPERATION_DISABLE);
        return saved;
    }

    @Override
    public ResourceAclRulePage page(ResourceAclQuery query) {
        requireAdmin();
        ResourceAclQuery safeQuery = query == null
                ? new ResourceAclQuery(null, null, null, null, null, null, 1L, 10L)
                : query;
        return repository.page(safeQuery);
    }

    @Override
    public ResourceAclImportDryRunReport dryRunImport(ResourceAclImportDryRunCommand command) {
        requireAdmin();
        ResourceAclImportDryRunCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        Map<ResourceAclNaturalKey, AccessDecisionEffect> batchKeys = new HashMap<>();
        List<ResourceAclImportDryRunItem> results = new ArrayList<>();
        List<ResourceAclImportItem> items = safeCommand.items();
        for (int index = 0; index < items.size(); index++) {
            results.add(dryRunItem(index, Objects.requireNonNull(items.get(index), "item must not be null"),
                    now, batchKeys));
        }
        return new ResourceAclImportDryRunReport(results);
    }

    @Override
    public ResourceAclImportResult importRules(ResourceAclImportCommand command) {
        CurrentUser currentUser = requireAdmin();
        ResourceAclImportCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        ResourceAclImportDryRunReport report = dryRunImport(new ResourceAclImportDryRunCommand(safeCommand.items()));
        Map<ResourceAclImportReasonCode, Integer> reasonCounts = reasonCounts(report);
        if (safeCommand.mode() == ResourceAclImportMode.FAIL_ON_INVALID && hasSkippedItems(report)) {
            return new ResourceAclImportResult(safeCommand.mode(), report, List.of(), reasonCounts, true);
        }

        Instant now = clock.instant();
        List<ResourceAclRule> createdRules = new ArrayList<>();
        for (ResourceAclImportDryRunItem item : report.items()) {
            if (item.status() == ResourceAclImportItemStatus.VALID) {
                createdRules.add(repository.save(toRule(item.item(), String.valueOf(currentUser.userId()), now)));
            }
        }
        ResourceAclImportResult result = new ResourceAclImportResult(
                safeCommand.mode(),
                report,
                createdRules.stream().map(ResourceAclRule::ruleId).toList(),
                reasonCounts,
                false);
        appendImportAudit(currentUser, result);
        return result;
    }

    private ResourceAclImportDryRunItem dryRunItem(int index,
                                                   ResourceAclImportItem item,
                                                   Instant now,
                                                   Map<ResourceAclNaturalKey, AccessDecisionEffect> batchKeys) {
        DryRunValidation validation = validateImportItem(item, now);
        if (validation.status() != ResourceAclImportItemStatus.VALID) {
            return result(index, item, validation.naturalKey(), validation.status(), validation.reasonCode());
        }

        ResourceAclNaturalKey naturalKey = validation.naturalKey();
        AccessDecisionEffect batchEffect = batchKeys.get(naturalKey);
        if (batchEffect != null) {
            ResourceAclImportItemStatus status = batchEffect == item.effect()
                    ? ResourceAclImportItemStatus.DUPLICATE_IN_BATCH
                    : ResourceAclImportItemStatus.CONFLICT;
            ResourceAclImportReasonCode reasonCode = batchEffect == item.effect()
                    ? ResourceAclImportReasonCode.NATURAL_KEY_DUPLICATE
                    : ResourceAclImportReasonCode.DENY_ALLOW_CONFLICT;
            return result(index, item, naturalKey, status, reasonCode);
        }
        batchKeys.put(naturalKey, item.effect());

        List<ResourceAclRule> existingRules = repository.findByNaturalKey(naturalKey, now);
        if (!existingRules.isEmpty()) {
            boolean conflictingEffect = existingRules.stream().anyMatch(rule -> rule.effect() != item.effect());
            return conflictingEffect
                    ? result(index, item, naturalKey, ResourceAclImportItemStatus.CONFLICT,
                    ResourceAclImportReasonCode.DENY_ALLOW_CONFLICT)
                    : result(index, item, naturalKey, ResourceAclImportItemStatus.DUPLICATE_EXISTING,
                    ResourceAclImportReasonCode.EXISTING_RULE_DUPLICATE);
        }
        return result(index, item, naturalKey, ResourceAclImportItemStatus.VALID,
                ResourceAclImportReasonCode.VALID_RULE);
    }

    private DryRunValidation validateImportItem(ResourceAclImportItem item, Instant now) {
        ResourceAclNaturalKey naturalKey = naturalKeyOrNull(item);
        if (!hasText(item.tenantId())) {
            return invalid(naturalKey, ResourceAclImportReasonCode.MISSING_TENANT);
        }
        if (item.scope() != ResourceAclRuleScope.EXACT_RESOURCE) {
            return new DryRunValidation(naturalKey, ResourceAclImportItemStatus.UNSUPPORTED_SCOPE,
                    ResourceAclImportReasonCode.UNSUPPORTED_SCOPE);
        }
        if (!hasText(item.resourceType()) || !hasText(item.resourceId())) {
            return invalid(naturalKey, ResourceAclImportReasonCode.MISSING_RESOURCE);
        }
        if (item.subjectType() == null || !hasText(item.subjectId())) {
            return invalid(naturalKey, ResourceAclImportReasonCode.MISSING_SUBJECT);
        }
        if (item.action() == null) {
            return invalid(naturalKey, ResourceAclImportReasonCode.MISSING_ACTION);
        }
        if (item.effect() == null || item.effect() == AccessDecisionEffect.MASK) {
            return invalid(naturalKey, ResourceAclImportReasonCode.INVALID_EFFECT);
        }
        if (item.expiresAt() != null && !item.expiresAt().isAfter(now)) {
            return invalid(naturalKey, ResourceAclImportReasonCode.EXPIRED_INPUT);
        }
        return new DryRunValidation(item.naturalKey(), ResourceAclImportItemStatus.VALID,
                ResourceAclImportReasonCode.VALID_RULE);
    }

    private DryRunValidation invalid(ResourceAclNaturalKey naturalKey, ResourceAclImportReasonCode reasonCode) {
        return new DryRunValidation(naturalKey, ResourceAclImportItemStatus.INVALID, reasonCode);
    }

    private ResourceAclNaturalKey naturalKeyOrNull(ResourceAclImportItem item) {
        if (!hasText(item.tenantId())
                || item.scope() == null
                || !hasText(item.resourceType())
                || !hasText(item.resourceId())
                || item.subjectType() == null
                || !hasText(item.subjectId())
                || item.action() == null) {
            return null;
        }
        return item.naturalKey();
    }

    private ResourceAclImportDryRunItem result(int index,
                                               ResourceAclImportItem item,
                                               ResourceAclNaturalKey naturalKey,
                                               ResourceAclImportItemStatus status,
                                               ResourceAclImportReasonCode reasonCode) {
        return new ResourceAclImportDryRunItem(index, item, naturalKey, status, reasonCode);
    }

    private CurrentUser requireAdmin() {
        currentUserPort.requireRole(ResourceAclAuthorizationRoles.ADMIN);
        return currentUserPort.requireCurrentUser();
    }

    private ResourceAclRule toRule(ResourceAclImportItem item, String createdBy, Instant now) {
        return new ResourceAclRule(
                RULE_ID_PREFIX + SnowflakeIds.nextIdString(),
                item.tenantId(),
                item.scope(),
                item.resourceType(),
                item.resourceId(),
                item.subjectType(),
                item.subjectId(),
                item.action(),
                item.effect(),
                ResourceAclRuleStatus.ENABLED,
                item.priority(),
                item.expiresAt(),
                createdBy,
                now,
                now);
    }

    private boolean hasSkippedItems(ResourceAclImportDryRunReport report) {
        return report.items().stream().anyMatch(item -> item.status() != ResourceAclImportItemStatus.VALID);
    }

    private Map<ResourceAclImportReasonCode, Integer> reasonCounts(ResourceAclImportDryRunReport report) {
        Map<ResourceAclImportReasonCode, Integer> counts = new java.util.EnumMap<>(ResourceAclImportReasonCode.class);
        for (ResourceAclImportDryRunItem item : report.items()) {
            counts.merge(item.reasonCode(), 1, Integer::sum);
        }
        return counts;
    }

    private void appendRuleAudit(CurrentUser user, ResourceAclRule rule, String operation) {
        appendAudit(
                user,
                rule.tenantId(),
                RESOURCE_TYPE_ACL_RULE,
                rule.ruleId(),
                """
                        {"operation":"%s","ruleId":"%s","tenantId":"%s","resourceType":"%s","resourceId":"%s","subjectType":"%s","subjectId":"%s","action":"%s","effect":"%s","status":"%s"}
                        """.formatted(
                        operation,
                        json(rule.ruleId()),
                        json(rule.tenantId()),
                        json(rule.resourceType()),
                        json(rule.resourceId()),
                        rule.subjectType().name(),
                        json(rule.subjectId()),
                        rule.action().name(),
                        rule.effect().name(),
                        rule.status().name()));
    }

    private void appendImportAudit(CurrentUser user, ResourceAclImportResult result) {
        appendAudit(
                user,
                importTenantId(result),
                RESOURCE_TYPE_ACL_IMPORT,
                RESOURCE_ID_IMPORT,
                """
                        {"operation":"%s","mode":"%s","createdCount":%d,"skippedCount":%d,"failed":%s,"createdRuleIds":%s}
                        """.formatted(
                        AUDIT_OPERATION_IMPORT,
                        result.mode().name(),
                        result.createdCount(),
                        result.skippedCount(),
                        result.failed(),
                        jsonArray(result.createdRuleIds())));
    }

    private String importTenantId(ResourceAclImportResult result) {
        return result.dryRunReport().items().stream()
                .map(ResourceAclImportDryRunItem::item)
                .map(ResourceAclImportItem::tenantId)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private void appendAudit(CurrentUser user,
                             String tenantId,
                             String resourceType,
                             String resourceId,
                             String payload) {
        if (auditLedger == null) {
            return;
        }
        String safeTenantId = trimToNull(tenantId);
        if (safeTenantId == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                AUDIT_ID_PREFIX + SnowflakeIds.nextIdString(),
                safeTenantId,
                AuditEventType.RESOURCE_ACL_CHANGED,
                AuditActorType.USER,
                String.valueOf(user.userId()),
                null,
                null,
                resourceType,
                resourceId,
                payload,
                now));
    }

    private String jsonArray(List<String> values) {
        return "[" + values.stream()
                .map(value -> "\"" + json(value) + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record DryRunValidation(ResourceAclNaturalKey naturalKey,
                                    ResourceAclImportItemStatus status,
                                    ResourceAclImportReasonCode reasonCode) {
    }
}
