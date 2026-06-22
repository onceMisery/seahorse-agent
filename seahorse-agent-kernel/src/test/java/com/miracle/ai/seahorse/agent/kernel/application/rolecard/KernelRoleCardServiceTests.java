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

package com.miracle.ai.seahorse.agent.kernel.application.rolecard;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ResolvedRoleCard;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.ResourceNotFoundException;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardGuardrailPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelRoleCardServiceTests {

    @Test
    void shouldSaveRoleCardAndRunGuardrailForHigherPermission() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        RecordingGuardrail guardrail = new RecordingGuardrail();
        KernelRoleCardService service = new KernelRoleCardService(repository, guardrail);

        Long id = service.save(new RoleCardCommand(
                null, "7", "Coach", "Ask short questions.", "avatar.png", true));

        assertEquals(1L, id);
        assertEquals(List.of("Ask short questions."), guardrail.checkedDefinitions);
        assertEquals(1, repository.records.get(id).getHigherPerm());
        assertEquals("PRIVATE", repository.records.get(id).getShareScope());
        assertEquals("PENDING", repository.records.get(id).getApprovalStatus());
        assertEquals(0, repository.records.get(id).getPublished());
    }

    @Test
    void shouldRejectHighPermissionRoleCardWhenSharedOrPublishedWithoutApproval() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        assertThrows(IllegalStateException.class, () -> service.save(new RoleCardCommand(
                null, "7", "Team Root", "Use high privilege tools.", null,
                true, "TEAM", "PENDING", false)));
        assertThrows(IllegalStateException.class, () -> service.save(new RoleCardCommand(
                null, "7", "Published Root", "Use high privilege tools.", null,
                true, "PRIVATE", "PENDING", true)));
    }

    @Test
    void shouldAllowApprovedHighPermissionRoleCardToBeSharedAndPublished() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        Long id = service.save(new RoleCardCommand(
                null, "7", "Approved Root", "Use approved high privilege tools.", null,
                true, "TEAM", "APPROVED", true));

        assertEquals("TEAM", repository.records.get(id).getShareScope());
        assertEquals("APPROVED", repository.records.get(id).getApprovalStatus());
        assertEquals(1, repository.records.get(id).getPublished());
    }

    @Test
    void shouldActivateOneRoleCardAtATime() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        RoleCardRecord first = repository.record("7", "A", "alpha");
        first.setEnabled(1);
        RoleCardRecord second = repository.record("7", "B", "beta");
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        service.activate("7", second.getId());

        assertEquals(0, repository.records.get(first.getId()).getEnabled());
        assertEquals(1, repository.records.get(second.getId()).getEnabled());
    }

    @Test
    void shouldRejectUpdateWhenRoleCardDoesNotExist() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        assertThrows(ResourceNotFoundException.class, () -> service.save(new RoleCardCommand(
                404L, "7", "Missing", "Do not create on update.", null, false)));
    }

    @Test
    void shouldRejectUpdateAndDeleteForReadonlySystemRoleCard() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        RoleCardRecord preset = repository.record("7", "Research Analyst", "Analyze requirements.");
        preset.setAssetSource("SYSTEM");
        preset.setPresetKey("role.research-analyst");
        preset.setReadonly(1);
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        assertThrows(IllegalStateException.class, () -> service.save(new RoleCardCommand(
                preset.getId(), "7", "Changed", "Changed definition.", null, false)));
        assertThrows(IllegalStateException.class, () -> service.delete("7", preset.getId()));

        service.activate("7", preset.getId());
        assertEquals(1, repository.records.get(preset.getId()).getEnabled());
    }

    @Test
    void shouldResolveExplicitRoleCardBeforeEnabledRoleCard() {
        InMemoryRoleCardRepository repository = new InMemoryRoleCardRepository();
        RoleCardRecord enabled = repository.record("7", "Enabled", "enabled definition");
        enabled.setEnabled(1);
        RoleCardRecord explicit = repository.record("7", "Explicit", "explicit definition");
        KernelRoleCardService service = new KernelRoleCardService(repository, RoleCardGuardrailPort.noop());

        ResolvedRoleCard resolved = service.resolve("7", explicit.getId()).orElseThrow();

        assertEquals(String.valueOf(explicit.getId()), resolved.roleCardId());
        assertEquals("Explicit", resolved.name());
        assertEquals("explicit definition", resolved.definition());
        assertTrue(resolved.definition().contains("explicit"));
    }

    private static final class RecordingGuardrail implements RoleCardGuardrailPort {
        private final List<String> checkedDefinitions = new ArrayList<>();

        @Override
        public void assertSafe(String definition) {
            checkedDefinitions.add(definition);
        }
    }

    private static final class InMemoryRoleCardRepository implements RoleCardRepositoryPort {
        private final Map<Long, RoleCardRecord> records = new LinkedHashMap<>();
        private long nextId = 1L;

        private RoleCardRecord record(String userId, String name, String definition) {
            RoleCardRecord record = new RoleCardRecord();
            record.setId(nextId++);
            record.setUserId(userId);
            record.setName(name);
            record.setDefinition(definition);
            record.setHigherPerm(0);
            record.setEnabled(0);
            records.put(record.getId(), record);
            return record;
        }

        @Override
        public List<RoleCardRecord> listByUser(String userId) {
            return records.values().stream()
                    .filter(record -> userId.equals(record.getUserId()))
                    .toList();
        }

        @Override
        public Optional<RoleCardRecord> findById(String userId, Long id) {
            RoleCardRecord record = records.get(id);
            return record != null && userId.equals(record.getUserId()) ? Optional.of(record) : Optional.empty();
        }

        @Override
        public Optional<RoleCardRecord> findEnabled(String userId) {
            return records.values().stream()
                    .filter(record -> userId.equals(record.getUserId()) && Integer.valueOf(1).equals(record.getEnabled()))
                    .findFirst();
        }

        @Override
        public Long save(RoleCardRecord record) {
            if (record.getId() == null) {
                record.setId(nextId++);
            }
            records.put(record.getId(), record);
            return record.getId();
        }

        @Override
        public void disableAll(String userId) {
            listByUser(userId).forEach(record -> record.setEnabled(0));
        }

        @Override
        public void setEnabled(String userId, Long id, boolean enabled) {
            findById(userId, id).ifPresent(record -> record.setEnabled(enabled ? 1 : 0));
        }

        @Override
        public void delete(String userId, Long id) {
            findById(userId, id).ifPresent(record -> records.remove(record.getId()));
        }
    }
}
