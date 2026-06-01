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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportDryRunReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportItemStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclLookup;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclNaturalKey;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportDryRunCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRulePage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelResourceAclManagementServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldCreateAndPageResourceAclRulesBehindAdminBoundary() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), FIXED_CLOCK);

        ResourceAclRule created = service.create(command(" tenant-1 ", " doc-1 "));
        ResourceAclRulePage page = service.page(new ResourceAclQuery(
                "tenant-1",
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAclRuleStatus.ENABLED,
                1L,
                10L));

        assertTrue(created.ruleId().startsWith("racl_"));
        assertEquals("tenant-1", created.tenantId());
        assertEquals("doc-1", created.resourceId());
        assertEquals("admin-1", created.createdBy());
        assertEquals(NOW, created.createdAt());
        assertEquals(1L, page.total());
        assertEquals("tenant-1", repository.lastQuery.tenantId());
    }

    @Test
    void shouldRejectNonAdminCreate() {
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(new MemoryResourceAclRepository(), normalUser(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.create(command("tenant-1", "doc-1")));

        assertEquals("权限不足", error.getMessage());
    }

    @Test
    void shouldDisableRuleIdempotently() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), FIXED_CLOCK);
        ResourceAclRule created = service.create(command("tenant-1", "doc-1"));

        ResourceAclRule disabled = service.disable(created.ruleId());
        ResourceAclRule disabledAgain = service.disable(created.ruleId());

        assertEquals(ResourceAclRuleStatus.DISABLED, disabled.status());
        assertEquals(disabled, disabledAgain);
    }

    @Test
    void shouldDryRunImportWithoutSavingRules() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        repository.existingNaturalKeyRules.add(rule("existing-allow", "doc-existing", AccessDecisionEffect.ALLOW));
        repository.existingNaturalKeyRules.add(rule("existing-deny", "doc-conflict", AccessDecisionEffect.DENY));
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), FIXED_CLOCK);

        ResourceAclImportDryRunReport report = service.dryRunImport(new ResourceAclImportDryRunCommand(List.of(
                importItem("doc-valid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-valid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-conflict-batch", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-conflict-batch", AccessDecisionEffect.DENY, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-existing", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-conflict", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                importItem("doc-expired", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE,
                        NOW.minusSeconds(1)),
                importItem("doc-type", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.RESOURCE_TYPE, null))));

        assertEquals(List.of(
                        ResourceAclImportItemStatus.VALID,
                        ResourceAclImportItemStatus.DUPLICATE_IN_BATCH,
                        ResourceAclImportItemStatus.VALID,
                        ResourceAclImportItemStatus.CONFLICT,
                        ResourceAclImportItemStatus.DUPLICATE_EXISTING,
                        ResourceAclImportItemStatus.CONFLICT,
                        ResourceAclImportItemStatus.INVALID,
                        ResourceAclImportItemStatus.UNSUPPORTED_SCOPE),
                report.items().stream().map(item -> item.status()).toList());
        assertEquals(0, repository.saveCount);
        assertEquals(4, repository.naturalKeyLookupCount);
    }

    @Test
    void shouldRejectNonAdminDryRunImport() {
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(new MemoryResourceAclRepository(), normalUser(), FIXED_CLOCK);

        assertThrows(IllegalStateException.class,
                () -> service.dryRunImport(new ResourceAclImportDryRunCommand(List.of(
                        importItem("doc-1", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null)))));
    }

    @Test
    void shouldFailClosedImportWhenAnyItemIsInvalid() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), FIXED_CLOCK);

        ResourceAclImportResult result = service.importRules(new ResourceAclImportCommand(
                List.of(
                        importItem("doc-valid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                        importItem("doc-invalid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.RESOURCE_TYPE, null)),
                ResourceAclImportMode.FAIL_ON_INVALID));

        assertEquals(ResourceAclImportMode.FAIL_ON_INVALID, result.mode());
        assertEquals(1, result.dryRunReport().validCount());
        assertEquals(1, result.dryRunReport().unsupportedScopeCount());
        assertEquals(0, result.createdCount());
        assertEquals(2, result.skippedCount());
        assertTrue(result.failed());
        assertEquals(0, repository.saveCount);
    }

    @Test
    void shouldCommitOnlyValidItemsWhenModeIsValidOnly() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), FIXED_CLOCK);

        ResourceAclImportResult result = service.importRules(new ResourceAclImportCommand(
                List.of(
                        importItem("doc-valid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                        importItem("doc-valid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null),
                        importItem("doc-invalid", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.RESOURCE_TYPE, null)),
                ResourceAclImportMode.VALID_ONLY));

        assertEquals(ResourceAclImportMode.VALID_ONLY, result.mode());
        assertEquals(1, result.createdCount());
        assertEquals(2, result.skippedCount());
        assertFalse(result.failed());
        assertEquals(1, repository.saveCount);
        assertEquals("doc-valid", repository.rules.get(0).resourceId());
        assertEquals(Integer.valueOf(1), result.reasonCounts().get(ResourceAclImportReasonCode.VALID_RULE));
        assertEquals(Integer.valueOf(1),
                result.reasonCounts().get(ResourceAclImportReasonCode.NATURAL_KEY_DUPLICATE));
        assertEquals(Integer.valueOf(1), result.reasonCounts().get(ResourceAclImportReasonCode.UNSUPPORTED_SCOPE));
    }

    @Test
    void shouldWriteAuditForCreateImportAndDisable() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelResourceAclManagementService service =
                new KernelResourceAclManagementService(repository, adminUser(), auditLedger, FIXED_CLOCK);

        ResourceAclRule created = service.create(command("tenant-1", "doc-create"));
        service.importRules(new ResourceAclImportCommand(
                List.of(importItem("doc-import", AccessDecisionEffect.ALLOW, ResourceAclRuleScope.EXACT_RESOURCE, null)),
                ResourceAclImportMode.FAIL_ON_INVALID));
        service.disable(created.ruleId());

        assertEquals(List.of(
                        AuditEventType.RESOURCE_ACL_CHANGED,
                        AuditEventType.RESOURCE_ACL_CHANGED,
                        AuditEventType.RESOURCE_ACL_CHANGED),
                auditRepository.events.stream().map(AuditEvent::eventType).toList());
        assertTrue(auditRepository.events.stream()
                .map(AuditEvent::redactedPayload)
                .noneMatch(payload -> payload.contains("items") || payload.contains("doc-import,doc-create")));
    }

    private static ResourceAclCreateCommand command(String tenantId, String resourceId) {
        return new ResourceAclCreateCommand(
                tenantId,
                ContextResourceType.DOCUMENT.value(),
                resourceId,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                AccessDecisionEffect.ALLOW,
                100,
                null);
    }

    private static ResourceAclImportItem importItem(String resourceId,
                                                    AccessDecisionEffect effect,
                                                    ResourceAclRuleScope scope,
                                                    Instant expiresAt) {
        return new ResourceAclImportItem(
                "tenant-1",
                scope,
                ContextResourceType.DOCUMENT.value(),
                resourceId,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                100,
                expiresAt);
    }

    private static ResourceAclRule rule(String ruleId, String resourceId, AccessDecisionEffect effect) {
        return new ResourceAclRule(
                ruleId,
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                resourceId,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                ResourceAclRuleStatus.ENABLED,
                100,
                null,
                "admin-1",
                NOW,
                NOW);
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser(1L, "root", "admin", null));
    }

    private static CurrentUserPort normalUser() {
        return () -> Optional.of(new CurrentUser(2L, "alice", "user", null));
    }

    private static final class MemoryResourceAclRepository implements ResourceAclRepositoryPort {

        private final List<ResourceAclRule> rules = new ArrayList<>();
        private final List<ResourceAclRule> existingNaturalKeyRules = new ArrayList<>();
        private ResourceAclQuery lastQuery;
        private int saveCount;
        private int naturalKeyLookupCount;

        @Override
        public ResourceAclRule save(ResourceAclRule rule) {
            saveCount++;
            rules.removeIf(existing -> existing.ruleId().equals(rule.ruleId()));
            rules.add(rule);
            return rule;
        }

        @Override
        public Optional<ResourceAclRule> findById(String ruleId) {
            return rules.stream().filter(rule -> rule.ruleId().equals(ruleId)).findFirst();
        }

        @Override
        public ResourceAclRulePage page(ResourceAclQuery query) {
            lastQuery = query;
            return new ResourceAclRulePage(rules, rules.size(), query.size(), query.current(), 1L);
        }

        @Override
        public List<ResourceAclRule> findEffective(ResourceAclLookup lookup) {
            return rules.stream()
                    .filter(rule -> rule.matches(lookup, lookup.now()))
                    .sorted(ResourceAclRule.effectiveOrder())
                    .toList();
        }

        @Override
        public List<ResourceAclRule> findByNaturalKey(ResourceAclNaturalKey naturalKey, Instant now) {
            naturalKeyLookupCount++;
            return existingNaturalKeyRules.stream()
                    .filter(rule -> naturalKey.matches(rule))
                    .filter(rule -> rule.isEffectiveAt(now))
                    .toList();
        }
    }

    private static final class RecordingAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return events.stream().filter(event -> event.auditId().equals(auditId)).findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(events, events.size(), 10L, 1L, events.isEmpty() ? 0L : 1L);
        }
    }
}
