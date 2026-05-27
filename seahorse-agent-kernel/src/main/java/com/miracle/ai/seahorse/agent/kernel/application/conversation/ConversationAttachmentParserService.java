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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 附件异步解析服务。
 *
 * <p>负责将上传的附件从 PENDING 状态推进到 PARSED/FAILED/BLOCKED。
 * 解析结果写入 resourceRefJson，供后续 ContextPack 装配使用。
 */
public class ConversationAttachmentParserService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationAttachmentParserService.class);

    private static final int MAX_FILE_BYTES = 10_485_760;
    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "application/x-executable",
            "application/x-msdos-program",
            "application/x-msdownload",
            "application/octet-stream"
    );

    private final ConversationAttachmentRepositoryPort repositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final DocumentParserPort documentParserPort;

    public ConversationAttachmentParserService(ConversationAttachmentRepositoryPort repositoryPort,
                                               ObjectStoragePort objectStoragePort,
                                               DocumentParserPort documentParserPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort);
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort);
        this.documentParserPort = Objects.requireNonNullElseGet(documentParserPort, DocumentParserPort::plainText);
    }

    /**
     * 解析指定附件。状态流转：PENDING → PARSING → PARSED/FAILED/BLOCKED。
     */
    public ConversationAttachmentParseStatus parse(String attachmentId) {
        ConversationAttachment attachment = repositoryPort.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("attachment not found: " + attachmentId));

        if (attachment.parseStatus() != ConversationAttachmentParseStatus.PENDING) {
            return attachment.parseStatus();
        }

        if (BLOCKED_MIME_TYPES.contains(attachment.mimeType())) {
            updateStatus(attachment, ConversationAttachmentParseStatus.BLOCKED);
            return ConversationAttachmentParseStatus.BLOCKED;
        }

        if (attachment.sizeBytes() > MAX_FILE_BYTES) {
            updateStatus(attachment, ConversationAttachmentParseStatus.BLOCKED);
            return ConversationAttachmentParseStatus.BLOCKED;
        }

        try {
            byte[] content;
            try (InputStream input = objectStoragePort.openStream(attachment.storageRef())) {
                content = input.readNBytes(MAX_FILE_BYTES);
            }

            DocumentParseResult result = documentParserPort.parse(
                    content, attachment.mimeType(), attachment.fileName(),
                    Map.of("source", "conversation_attachment"));

            if (result.text() == null || result.text().isBlank()) {
                updateStatus(attachment, ConversationAttachmentParseStatus.FAILED);
                return ConversationAttachmentParseStatus.FAILED;
            }

            updateStatus(attachment, ConversationAttachmentParseStatus.PARSED);
            return ConversationAttachmentParseStatus.PARSED;
        } catch (IOException | RuntimeException ex) {
            LOG.warn("附件解析失败: attachmentId={}, fileName={}", attachmentId, attachment.fileName(), ex);
            updateStatus(attachment, ConversationAttachmentParseStatus.FAILED);
            return ConversationAttachmentParseStatus.FAILED;
        }
    }

    private void updateStatus(ConversationAttachment attachment, ConversationAttachmentParseStatus status) {
        repositoryPort.save(new ConversationAttachment(
                attachment.attachmentId(),
                attachment.conversationId(),
                attachment.messageId(),
                attachment.userId(),
                attachment.fileName(),
                attachment.mimeType(),
                attachment.sizeBytes(),
                attachment.storageRef(),
                status,
                attachment.resourceRefJson(),
                attachment.createdAt()));
    }
}
