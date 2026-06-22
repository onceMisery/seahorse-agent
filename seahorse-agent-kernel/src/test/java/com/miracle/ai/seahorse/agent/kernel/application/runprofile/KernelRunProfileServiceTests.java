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

package com.miracle.ai.seahorse.agent.kernel.application.runprofile;

import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileAuditSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileRiskSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelRunProfileServiceTests {

    @Test
    void shouldSaveRunProfileAndReplaceToolBindings() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository);

        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Research AgentScope")
                .description("Long running research")
                .roleCardId(9L)
                .executorEngine("agentscope")
                .executorConfigJson("{\"studioTraceEnabled\":true}")
                .modelConfigJson("{\"model\":\"gpt-4.1-mini\"}")
                .memoryScopeJson("{\"longTerm\":true}")
                .guardrailConfigJson("{\"highRiskToolApproval\":true}")
                .toolBindings(List.of(RunProfileToolBindingCommand.builder()
                        .toolId("filesystem.read_file")
                        .provider("MCP")
                        .enabled(true)
                        .build()))
                .build());

        RunProfileRecord saved = repository.records.get(id);
        assertEquals("100", saved.getUserId());
        assertEquals("Research AgentScope", saved.getName());
        assertEquals(9L, saved.getRoleCardId());
        assertEquals("agentscope", saved.getExecutorEngine());
        assertEquals(0, saved.getEnabled());
        assertIterableEquals(List.of("filesystem.read_file"), repository.toolsByProfile.get(id)
                .stream()
                .map(RunProfileToolBindingRecord::getToolId)
                .toList());
    }

    @Test
    void shouldActivateSingleDefaultRunProfileForUser() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository);
        Long first = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Kernel")
                .executorEngine("kernel")
                .build());
        Long second = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("AgentScope")
                .executorEngine("agentscope")
                .build());

        service.activate("100", second);

        assertEquals(0, repository.records.get(first).getEnabled());
        assertEquals(1, repository.records.get(second).getEnabled());
    }

    @Test
    void shouldResolvePreviewWithEnabledToolGroups() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository);
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Research AgentScope")
                .roleCardId(9L)
                .executorEngine("agentscope")
                .executorConfigJson("{\"studioTraceEnabled\":true}")
                .modelConfigJson("{\"model\":\"gpt-4.1-mini\"}")
                .memoryScopeJson("{\"longTerm\":true}")
                .guardrailConfigJson("{\"highRiskToolApproval\":true}")
                .toolBindings(List.of(
                        RunProfileToolBindingCommand.builder()
                                .toolId("get_current_datetime")
                                .provider("BUILT_IN")
                                .enabled(true)
                                .build(),
                        RunProfileToolBindingCommand.builder()
                                .toolId("filesystem.read_file")
                                .provider("MCP")
                                .enabled(true)
                                .build(),
                        RunProfileToolBindingCommand.builder()
                                .toolId("seahorse-researcher")
                                .provider("A2A")
                                .enabled(true)
                                .build(),
                        RunProfileToolBindingCommand.builder()
                                .toolId("disabled.tool")
                                .provider("MCP")
                                .enabled(false)
                                .build()))
                .build());

        RunProfileResolvedPreview preview = service.resolvePreview("100", id).orElseThrow();

        assertEquals(id, preview.getRunProfileId());
        assertEquals(9L, preview.getRoleCardId());
        assertEquals("agentscope", preview.getExecutorEngine());
        assertEquals("{\"studioTraceEnabled\":true}", preview.getExecutorConfigJson());
        assertEquals(true, preview.isExplicitToolAllowlist());
        assertIterableEquals(List.of("get_current_datetime"), preview.getToolIds());
        assertIterableEquals(List.of("filesystem.read_file"), preview.getMcpToolIds());
        assertIterableEquals(List.of("seahorse-researcher"), preview.getA2aAgentIds());
    }

    @Test
    void shouldApplyRunProfileToConversationAndResolveAppliedProfile() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository);
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Conversation AgentScope")
                .roleCardId(9L)
                .executorEngine("agentscope")
                .build());

        RunProfileResolvedPreview preview = service.applyToConversation("100", "200", id);

        assertEquals(id, preview.getRunProfileId());
        assertEquals(id, repository.appliedProfiles.get("100:200"));
        assertEquals("agentscope", service.findAppliedToConversation("100", "200")
                .orElseThrow()
                .getProfile()
                .getExecutorEngine());
    }

    @Test
    void shouldRejectUpdateAndDeleteForReadonlySystemRunProfile() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository);
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Default Plan")
                .executorEngine("kernel")
                .build());
        RunProfileRecord preset = repository.records.get(id);
        preset.setAssetSource("SYSTEM");
        preset.setPresetKey("plan.default");
        preset.setReadonly(1);

        assertThrows(IllegalStateException.class, () -> service.save(RunProfileCommand.builder()
                .id(id)
                .userId("100")
                .name("Changed Plan")
                .executorEngine("kernel")
                .build()));
        assertThrows(IllegalStateException.class, () -> service.delete("100", id));

        service.activate("100", id);
        assertEquals(1, repository.records.get(id).getEnabled());
        assertEquals(id, service.applyToConversation("100", "200", id).getRunProfileId());
    }

    @Test
    void shouldRejectRunProfileWhenExecutorEngineIsUnavailable() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("kernel"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(
                RunProfileCommand.builder()
                        .userId("100")
                        .name("Unavailable AgentScope")
                        .executorEngine("agentscope")
                        .build()));

        assertEquals("executorEngine is not available: agentscope", error.getMessage());
    }

    @Test
    void shouldListSupportedExecutorEnginesInStableOrder() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("agentscope", "kernel"));

        assertIterableEquals(List.of("kernel", "agentscope"), service.supportedExecutorEngines());
    }

    @Test
    void shouldSummarizeRunProfileRiskSignals() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("kernel", "agentscope"));
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Governed AgentScope")
                .roleCardId(9L)
                .executorEngine("agentscope")
                .memoryScopeJson("{\"longTerm\":true}")
                .guardrailConfigJson("{\"highRiskToolApproval\":false}")
                .toolBindings(List.of(
                        RunProfileToolBindingCommand.builder()
                                .toolId("filesystem.read_file")
                                .provider("MCP")
                                .enabled(true)
                                .build(),
                        RunProfileToolBindingCommand.builder()
                                .toolId("seahorse-researcher")
                                .provider("A2A")
                                .enabled(true)
                                .build(),
                        RunProfileToolBindingCommand.builder()
                                .toolId("disabled-openapi")
                                .provider("OPENAPI")
                                .enabled(false)
                                .build()))
                .build());

        RunProfileRiskSummary summary = service.riskSummary("100", id).orElseThrow();

        assertEquals(id, summary.getRunProfileId());
        assertEquals("HIGH", summary.getRiskLevel());
        assertIterableEquals(List.of(
                        "EXECUTOR_AGENTSCOPE",
                        "ROLE_CARD_BOUND",
                        "TOOL_MCP",
                        "TOOL_A2A",
                        "MEMORY_LONG_TERM",
                        "APPROVAL_NOT_ENFORCED"),
                summary.getRiskCodes());
        assertEquals(6, summary.getRiskItems().size());
    }

    @Test
    void shouldBlockProductionGateWhenAgentScopeProfileMissesRequiredGovernance() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("kernel", "agentscope"));
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Ungoverned AgentScope")
                .executorEngine("agentscope")
                .executorConfigJson("{\"studioTraceEnabled\":false}")
                .guardrailConfigJson("{\"highRiskToolApproval\":false}")
                .toolBindings(List.of(RunProfileToolBindingCommand.builder()
                        .toolId("filesystem.read_file")
                        .provider("MCP")
                        .enabled(true)
                        .build()))
                .build());

        RunProfileProductionGateCheck check = service.productionGateCheck("100", id).orElseThrow();

        assertEquals(id, check.getRunProfileId());
        assertEquals(false, check.isPassed());
        assertIterableEquals(List.of(
                        "APPROVAL_NOT_ENFORCED",
                        "AGENTSCOPE_NACOS_NAMESPACE_MISSING",
                        "AGENTSCOPE_NACOS_GROUP_MISSING",
                        "AGENTSCOPE_STUDIO_TRACE_DISABLED"),
                check.getBlockingCodes());
    }

    @Test
    void shouldPassProductionGateWhenHighRiskToolsAndAgentScopeAreGoverned() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("kernel", "agentscope"));
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Governed AgentScope")
                .executorEngine("agentscope")
                .executorConfigJson("""
                        {"nacosNamespace":"public","nacosGroup":"DEFAULT_GROUP","studioTraceEnabled":true}
                        """)
                .guardrailConfigJson("{\"highRiskToolApproval\":true}")
                .toolBindings(List.of(RunProfileToolBindingCommand.builder()
                        .toolId("filesystem.read_file")
                        .provider("MCP")
                        .enabled(true)
                        .build()))
                .build());

        RunProfileProductionGateCheck check = service.productionGateCheck("100", id).orElseThrow();

        assertEquals(true, check.isPassed());
        assertEquals(List.of(), check.getBlockingCodes());
        assertEquals("HIGH", check.getRiskLevel());
    }

    @Test
    void shouldMoveRunProfileThroughApprovalAndExposeAuditSummary() {
        InMemoryRunProfileRepository repository = new InMemoryRunProfileRepository();
        KernelRunProfileService service = new KernelRunProfileService(repository, Set.of("kernel", "agentscope"));
        Long id = service.save(RunProfileCommand.builder()
                .userId("100")
                .name("Governed AgentScope")
                .executorEngine("agentscope")
                .guardrailConfigJson("{\"highRiskToolApproval\":false}")
                .toolBindings(List.of(RunProfileToolBindingCommand.builder()
                        .toolId("filesystem.read_file")
                        .provider("MCP")
                        .enabled(true)
                        .build()))
                .build());

        service.submitApproval("100", id, "request production share");
        assertEquals("PENDING_APPROVAL", repository.records.get(id).getApprovalStatus());
        assertEquals("request production share", repository.records.get(id).getApprovalComment());

        service.approve("100", id, "admin", "approved for team");
        assertEquals("APPROVED", repository.records.get(id).getApprovalStatus());
        assertEquals("admin", repository.records.get(id).getApprovalOperator());

        service.reject("100", id, "security", "needs narrower tools");
        assertEquals("REJECTED", repository.records.get(id).getApprovalStatus());
        assertEquals("needs narrower tools", repository.records.get(id).getApprovalComment());

        RunProfileAuditSummary summary = service.auditSummary("100", id).orElseThrow();

        assertEquals(id, summary.getRunProfileId());
        assertEquals("REJECTED", summary.getApprovalStatus());
        assertEquals("HIGH", summary.getRiskLevel());
        assertEquals(1, summary.getEnabledToolCount());
        assertEquals(1, summary.getHighRiskToolCount());
        assertIterableEquals(List.of("filesystem.read_file"), summary.getHighRiskToolIds());
    }

    private static final class InMemoryRunProfileRepository implements RunProfileRepositoryPort {
        private final LinkedHashMap<Long, RunProfileRecord> records = new LinkedHashMap<>();
        private final LinkedHashMap<Long, List<RunProfileToolBindingRecord>> toolsByProfile = new LinkedHashMap<>();
        private final LinkedHashMap<String, Long> appliedProfiles = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public List<RunProfileRecord> listByUser(String userId) {
            return records.values().stream()
                    .filter(record -> userId.equals(record.getUserId()))
                    .filter(record -> !Integer.valueOf(1).equals(record.getDeleted()))
                    .toList();
        }

        @Override
        public Optional<RunProfileRecord> findById(String userId, Long id) {
            RunProfileRecord record = records.get(id);
            if (record == null || !userId.equals(record.getUserId())
                    || Integer.valueOf(1).equals(record.getDeleted())) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public Long save(RunProfileRecord record) {
            if (record.getId() == null) {
                record.setId(nextId++);
            }
            records.put(record.getId(), record);
            return record.getId();
        }

        @Override
        public void replaceTools(Long profileId, List<RunProfileToolBindingRecord> tools) {
            toolsByProfile.put(profileId, new ArrayList<>(tools));
        }

        @Override
        public List<RunProfileToolBindingRecord> listTools(Long profileId) {
            return List.copyOf(toolsByProfile.getOrDefault(profileId, List.of()));
        }

        @Override
        public void disableAll(String userId) {
            records.values().stream()
                    .filter(record -> userId.equals(record.getUserId()))
                    .forEach(record -> record.setEnabled(0));
        }

        @Override
        public void setEnabled(String userId, Long id, boolean enabled) {
            findById(userId, id).ifPresent(record -> record.setEnabled(enabled ? 1 : 0));
        }

        @Override
        public void delete(String userId, Long id) {
            findById(userId, id).ifPresent(record -> record.setDeleted(1));
        }

        @Override
        public void updateApprovalStatus(
                String userId,
                Long id,
                String approvalStatus,
                String approvalOperator,
                String approvalComment) {
            findById(userId, id).ifPresent(record -> {
                record.setApprovalStatus(approvalStatus);
                record.setApprovalOperator(approvalOperator);
                record.setApprovalComment(approvalComment);
                record.setApprovalTime(Instant.now());
            });
        }

        @Override
        public void applyToConversation(String userId, String conversationId, Long profileId) {
            appliedProfiles.put(userId + ":" + conversationId, profileId);
        }

        @Override
        public Optional<Long> findAppliedProfileId(String userId, String conversationId) {
            return Optional.ofNullable(appliedProfiles.get(userId + ":" + conversationId));
        }
    }
}
