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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildItemCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParseResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Assembles user-selected conversation attachments into runtime context candidates.
 */
public class ConversationAttachmentContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationAttachmentContextAssembler.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ATTACHMENT_COUNT = 5;
    private static final int MAX_ATTACHMENT_BYTES = 1_048_576;
    private static final int MAX_ATTACHMENT_TEXT_CHARS = 4_000;
    private static final int TOKEN_CHAR_RATIO = 4;
    private static final double ATTACHMENT_SCORE = 0.95D;
    private static final double ATTACHMENT_CONFIDENCE = 1.0D;
    private static final String CITATION_SOURCE_FIELD = "source";
    private static final String CITATION_CONVERSATION_ATTACHMENT = "conversation_attachment";
    private static final String CITATION_ATTACHMENT_ID = "attachmentId";
    private static final String CITATION_FILE_NAME = "fileName";
    private static final String CITATION_MIME_TYPE = "mimeType";
    private static final String CITATION_TRUNCATED = "truncated";
    private static final String PARSER_OPTION_SOURCE = "source";
    private static final String FILE_CONTEXT_PREFIX = "File: ";
    private static final String EMPTY_JSON_OBJECT = "{}";

    private final ConversationAttachmentRepositoryPort repositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final DocumentParserPort documentParserPort;

    public ConversationAttachmentContextAssembler(ConversationAttachmentRepositoryPort repositoryPort,
                                                  ObjectStoragePort objectStoragePort,
                                                  DocumentParserPort documentParserPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        this.documentParserPort = Objects.requireNonNullElseGet(documentParserPort, DocumentParserPort::plainText);
    }

    public static ConversationAttachmentContextAssembler noop() {
        return NoopHolder.INSTANCE;
    }

    public List<ContextBuildItemCandidate> assemble(String conversationId,
                                                    String userId,
                                                    List<String> attachmentIds) {
        if (!hasText(conversationId) || !hasText(userId) || attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<ContextBuildItemCandidate> candidates = new ArrayList<>();
        for (String attachmentId : attachmentIds.stream().limit(MAX_ATTACHMENT_COUNT).toList()) {
            if (!hasText(attachmentId)) {
                continue;
            }
            repositoryPort.findById(attachmentId.trim())
                    .ifPresentOrElse(
                            attachment -> addCandidate(candidates, attachment, conversationId.trim(), userId.trim()),
                            () -> {
                                LOG.debug("Conversation attachment skipped because it no longer exists: attachmentId={}",
                                        attachmentId);
                            });
        }
        return List.copyOf(candidates);
    }

    private void addCandidate(List<ContextBuildItemCandidate> candidates,
                              ConversationAttachment attachment,
                              String conversationId,
                              String userId) {
        if (!attachment.belongsTo(conversationId, userId)) {
            throw new SecurityException("attachment does not belong to current user");
        }
        if (attachment.parseStatus() == ConversationAttachmentParseStatus.BLOCKED) {
            throw new SecurityException("attachment is blocked");
        }
        try {
            ParsedAttachment parsed = parse(attachment);
            if (!hasText(parsed.text())) {
                repositoryPort.save(withParseStatus(attachment, ConversationAttachmentParseStatus.FAILED));
                return;
            }
            repositoryPort.save(withParseStatus(attachment, ConversationAttachmentParseStatus.PARSED));
            candidates.add(candidate(attachment, parsed));
        } catch (IOException | RuntimeException ex) {
            repositoryPort.save(withParseStatus(attachment, ConversationAttachmentParseStatus.FAILED));
            LOG.warn("Conversation attachment parse failed: attachmentId={}, fileName={}",
                    attachment.attachmentId(), attachment.fileName(), ex);
        }
    }

    private ParsedAttachment parse(ConversationAttachment attachment) throws IOException {
        byte[] content;
        try (InputStream input = objectStoragePort.openStream(attachment.storageRef())) {
            content = input.readNBytes(MAX_ATTACHMENT_BYTES + 1);
        }
        byte[] bounded = content.length > MAX_ATTACHMENT_BYTES
                ? Arrays.copyOf(content, MAX_ATTACHMENT_BYTES)
                : content;
        DocumentParseResult result = documentParserPort.parse(
                bounded,
                attachment.mimeType(),
                attachment.fileName(),
                Map.of(PARSER_OPTION_SOURCE, CITATION_CONVERSATION_ATTACHMENT));
        String text = Objects.requireNonNullElse(result.text(), "").trim();
        boolean truncated = content.length > MAX_ATTACHMENT_BYTES || text.length() > MAX_ATTACHMENT_TEXT_CHARS;
        if (text.length() > MAX_ATTACHMENT_TEXT_CHARS) {
            text = text.substring(0, MAX_ATTACHMENT_TEXT_CHARS);
        }
        return new ParsedAttachment(text, truncated);
    }

    private ContextBuildItemCandidate candidate(ConversationAttachment attachment, ParsedAttachment parsed) {
        String content = FILE_CONTEXT_PREFIX + attachment.fileName() + "\n" + parsed.text();
        return new ContextBuildItemCandidate(
                ContextItemSourceType.CONVERSATION_ATTACHMENT,
                attachment.attachmentId(),
                content,
                attachment.fileName(),
                ATTACHMENT_SCORE,
                ATTACHMENT_CONFIDENCE,
                ContextSensitivity.CONFIDENTIAL,
                new ResourceRef(
                        ContextResourceType.DOCUMENT,
                        attachment.attachmentId(),
                        AgentDefinition.DEFAULT_TENANT_ID,
                        attachment.userId(),
                        firstText(attachment.resourceRefJson(), EMPTY_JSON_OBJECT)),
                citationJson(attachment, parsed.truncated()),
                estimateTokens(content),
                null);
    }

    private ConversationAttachment withParseStatus(ConversationAttachment attachment,
                                                   ConversationAttachmentParseStatus parseStatus) {
        return new ConversationAttachment(
                attachment.attachmentId(),
                attachment.conversationId(),
                attachment.messageId(),
                attachment.userId(),
                attachment.fileName(),
                attachment.mimeType(),
                attachment.sizeBytes(),
                attachment.storageRef(),
                parseStatus,
                attachment.resourceRefJson(),
                attachment.createdAt());
    }

    private String citationJson(ConversationAttachment attachment, boolean truncated) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    CITATION_SOURCE_FIELD, CITATION_CONVERSATION_ATTACHMENT,
                    CITATION_ATTACHMENT_ID, attachment.attachmentId(),
                    CITATION_FILE_NAME, attachment.fileName(),
                    CITATION_MIME_TYPE, attachment.mimeType(),
                    CITATION_TRUNCATED, truncated));
        } catch (JsonProcessingException ex) {
            return EMPTY_JSON_OBJECT;
        }
    }

    private int estimateTokens(String content) {
        return Math.max(1, (int) Math.ceil(Objects.requireNonNullElse(content, "").length()
                / (double) TOKEN_CHAR_RATIO));
    }

    private String firstText(String first, String fallback) {
        String value = trimToNull(first);
        return value == null ? trimToNull(fallback) : value;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record ParsedAttachment(String text, boolean truncated) {
    }

    private static final class NoopHolder {
        private static final ConversationAttachmentContextAssembler INSTANCE =
                new NoopConversationAttachmentContextAssembler();
    }

    private static final class NoopConversationAttachmentContextAssembler
            extends ConversationAttachmentContextAssembler {

        private NoopConversationAttachmentContextAssembler() {
            super(new NoopConversationAttachmentRepositoryPort(), new NoopObjectStoragePort(),
                    DocumentParserPort.plainText());
        }

        @Override
        public List<ContextBuildItemCandidate> assemble(String conversationId,
                                                        String userId,
                                                        List<String> attachmentIds) {
            return List.of();
        }
    }

    private static final class NoopConversationAttachmentRepositoryPort
            implements ConversationAttachmentRepositoryPort {

        @Override
        public ConversationAttachment save(ConversationAttachment attachment) {
            return attachment;
        }

        @Override
        public Optional<ConversationAttachment> findById(String attachmentId) {
            return Optional.empty();
        }

        @Override
        public List<ConversationAttachment> listByConversation(String conversationId, String userId) {
            return List.of();
        }

        @Override
        public boolean delete(String attachmentId, String userId) {
            return false;
        }
    }

    private static final class NoopObjectStoragePort implements ObjectStoragePort {

        @Override
        public com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject upload(
                String bucketName,
                InputStream content,
                long size,
                String originalFilename,
                String contentType) {
            throw new UnsupportedOperationException("object storage is not configured");
        }

        @Override
        public InputStream openStream(String url) {
            throw new UnsupportedOperationException("object storage is not configured");
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }
}
