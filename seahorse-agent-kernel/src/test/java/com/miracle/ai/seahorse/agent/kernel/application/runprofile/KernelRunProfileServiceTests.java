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
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

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
        public void applyToConversation(String userId, String conversationId, Long profileId) {
            appliedProfiles.put(userId + ":" + conversationId, profileId);
        }

        @Override
        public Optional<Long> findAppliedProfileId(String userId, String conversationId) {
            return Optional.ofNullable(appliedProfiles.get(userId + ":" + conversationId));
        }
    }
}
