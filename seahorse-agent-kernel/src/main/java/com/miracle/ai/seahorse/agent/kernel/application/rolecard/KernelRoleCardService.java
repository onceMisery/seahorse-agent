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
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardGuardrailPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRepositoryPort;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Kernel service for user runtime role cards.
 */
@RequiredArgsConstructor
public class KernelRoleCardService implements RoleCardInboundPort {

    private static final String SHARE_SCOPE_PRIVATE = "PRIVATE";
    private static final String APPROVAL_STATUS_PENDING = "PENDING";
    private static final String APPROVAL_STATUS_APPROVED = "APPROVED";

    @NonNull
    private final RoleCardRepositoryPort repositoryPort;
    @NonNull
    private final RoleCardGuardrailPort guardrailPort;

    @Override
    public List<RoleCardRecord> list(String userId) {
        return repositoryPort.listByUser(requireText(userId, "userId must not be blank"));
    }

    @Override
    public Long save(RoleCardCommand command) {
        RoleCardCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String userId = requireText(safeCommand.userId(), "userId must not be blank");
        String name = requireText(safeCommand.name(), "name must not be blank");
        String definition = requireText(safeCommand.definition(), "definition must not be blank");
        String shareScope = normalizeEnumValue(safeCommand.shareScope(), SHARE_SCOPE_PRIVATE);
        String approvalStatus = normalizeEnumValue(safeCommand.approvalStatus(), APPROVAL_STATUS_PENDING);
        boolean published = safeCommand.published();
        if (safeCommand.higherPerm()) {
            guardrailPort.assertSafe(definition);
            requireApprovedBeforeSharingOrPublishing(shareScope, approvalStatus, published);
        }

        RoleCardRecord record = safeCommand.id() == null
                ? new RoleCardRecord()
                : repositoryPort.findById(userId, safeCommand.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Role card", safeCommand.id()));
        if (safeCommand.id() != null) {
            assertMutable(record);
        }
        record.setId(safeCommand.id());
        record.setUserId(userId);
        record.setName(name);
        record.setDefinition(definition);
        record.setAvatarRef(safeCommand.avatarRef());
        record.setHigherPerm(safeCommand.higherPerm() ? 1 : 0);
        record.setShareScope(shareScope);
        record.setApprovalStatus(approvalStatus);
        record.setPublished(published ? 1 : 0);
        if (record.getEnabled() == null) {
            record.setEnabled(0);
        }
        if (record.getDeleted() == null) {
            record.setDeleted(0);
        }
        return repositoryPort.save(record);
    }

    @Override
    public void activate(String userId, Long roleCardId) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (roleCardId == null) {
            throw new IllegalArgumentException("roleCardId must not be null");
        }
        repositoryPort.findById(safeUserId, roleCardId)
                .orElseThrow(() -> new IllegalArgumentException("role card not found"));
        repositoryPort.disableAll(safeUserId);
        repositoryPort.setEnabled(safeUserId, roleCardId, true);
    }

    @Override
    public void delete(String userId, Long roleCardId) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (roleCardId == null) {
            throw new IllegalArgumentException("roleCardId must not be null");
        }
        RoleCardRecord record = repositoryPort.findById(safeUserId, roleCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Role card", roleCardId));
        assertMutable(record);
        repositoryPort.delete(safeUserId, roleCardId);
    }

    @Override
    public Optional<ResolvedRoleCard> resolve(String userId, Long requestedRoleCardId) {
        String safeUserId = requireText(userId, "userId must not be blank");
        Optional<RoleCardRecord> selected = requestedRoleCardId != null
                ? repositoryPort.findById(safeUserId, requestedRoleCardId)
                : repositoryPort.findEnabled(safeUserId);
        return selected.map(this::toResolved);
    }

    private ResolvedRoleCard toResolved(RoleCardRecord record) {
        return new ResolvedRoleCard(
                String.valueOf(record.getId()),
                record.getName(),
                record.getDefinition(),
                Integer.valueOf(1).equals(record.getHigherPerm()));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String normalizeEnumValue(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase();
    }

    private static void requireApprovedBeforeSharingOrPublishing(
            String shareScope,
            String approvalStatus,
            boolean published) {
        boolean shared = !SHARE_SCOPE_PRIVATE.equals(shareScope);
        if ((shared || published) && !APPROVAL_STATUS_APPROVED.equals(approvalStatus)) {
            throw new IllegalStateException("High permission role cards require approval before sharing or publishing");
        }
    }

    private static void assertMutable(RoleCardRecord record) {
        if (Integer.valueOf(1).equals(record.getReadonly())) {
            throw new IllegalStateException("Readonly system role cards cannot be edited or deleted");
        }
    }
}
