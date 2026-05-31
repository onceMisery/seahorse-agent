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

package com.miracle.ai.seahorse.agent.kernel.application.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationAttachmentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.UploadConversationAttachmentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelConversationAttachmentService implements ConversationAttachmentInboundPort {

    private static final String BUCKET_NAME = "conversation-attachments";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String RESOURCE_TYPE = "CONVERSATION_ATTACHMENT";
    private static final String ATTACHMENT_ID_PREFIX = "attachment-";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConversationAttachmentRepositoryPort repositoryPort;
    private final ObjectStoragePort objectStoragePort;

    public KernelConversationAttachmentService(ConversationAttachmentRepositoryPort repositoryPort,
                                               ObjectStoragePort objectStoragePort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
    }

    @Override
    public ConversationAttachment upload(UploadConversationAttachmentCommand command) {
        UploadConversationAttachmentCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        objectStoragePort.ensureBucket(BUCKET_NAME);
        StoredObject storedObject = objectStoragePort.upload(
                BUCKET_NAME,
                safeCommand.content(),
                safeCommand.sizeBytes(),
                safeCommand.fileName(),
                mimeType(safeCommand.mimeType()));
        String attachmentId = nextAttachmentId();
        ConversationAttachment attachment = new ConversationAttachment(
                attachmentId,
                safeCommand.conversationId(),
                safeCommand.messageId(),
                safeCommand.userId(),
                fileName(safeCommand.fileName(), storedObject.originalFilename()),
                mimeType(storedObject.detectedType()),
                storedObject.size() == null ? safeCommand.sizeBytes() : storedObject.size(),
                storedObject.url(),
                ConversationAttachmentParseStatus.PENDING,
                resourceRefJson(attachmentId, safeCommand.conversationId(), safeCommand.userId(), storedObject.url()),
                Instant.now());
        return repositoryPort.save(attachment);
    }

    @Override
    public List<ConversationAttachment> list(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return repositoryPort.listByConversation(conversationId.trim(), userId.trim());
    }

    @Override
    public void delete(String conversationId, String attachmentId, String userId) {
        ConversationAttachment attachment = repositoryPort.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
        if (!attachment.belongsTo(conversationId, userId)) {
            throw new SecurityException("attachment does not belong to current user");
        }
        if (!repositoryPort.delete(attachmentId, userId)) {
            throw new IllegalArgumentException("attachment not found");
        }
        objectStoragePort.deleteByUrl(attachment.storageRef());
    }

    private String resourceRefJson(String attachmentId, String conversationId, String userId, String storageRef) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "resourceType", RESOURCE_TYPE,
                    "attachmentId", attachmentId,
                    "conversationId", conversationId,
                    "userId", userId,
                    "storageRef", storageRef));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("build attachment resource ref failed", ex);
        }
    }

    private String nextAttachmentId() {
        return ATTACHMENT_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private String fileName(String requestedName, String storedName) {
        return hasText(requestedName) ? requestedName.trim() : storedName;
    }

    private String mimeType(String mimeType) {
        return hasText(mimeType) ? mimeType.trim() : DEFAULT_MIME_TYPE;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
