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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildItemCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationAttachmentContextAssemblerTests {

    @Test
    void assembleShouldSkipAttachmentIdsDeletedBeforeContextBuild() {
        ConversationAttachmentContextAssembler assembler = new ConversationAttachmentContextAssembler(
                new DeletedAttachmentRepository(),
                new InMemoryObjectStoragePort("deleted attachment content"),
                DocumentParserPort.plainText());

        List<ContextBuildItemCandidate> candidates =
                assembler.assemble("conversation-1", "user-1", List.of("deleted-attachment"));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void assembleShouldIncludeExistingAttachmentContent() {
        InMemoryAttachmentRepository repository = new InMemoryAttachmentRepository(new ConversationAttachment(
                "attachment-1",
                "conversation-1",
                null,
                "user-1",
                "requirements.txt",
                "text/plain",
                20L,
                "memory://attachment-1",
                ConversationAttachmentParseStatus.PENDING,
                "{}",
                Instant.EPOCH));
        ConversationAttachmentContextAssembler assembler = new ConversationAttachmentContextAssembler(
                repository,
                new InMemoryObjectStoragePort("active attachment content"),
                DocumentParserPort.plainText());

        List<ContextBuildItemCandidate> candidates =
                assembler.assemble("conversation-1", "user-1", List.of("attachment-1"));

        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).content().contains("active attachment content"));
        assertEquals(ConversationAttachmentParseStatus.PARSED, repository.saved.parseStatus());
    }

    private static final class DeletedAttachmentRepository implements ConversationAttachmentRepositoryPort {

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

    private static final class InMemoryAttachmentRepository implements ConversationAttachmentRepositoryPort {

        private ConversationAttachment saved;

        private InMemoryAttachmentRepository(ConversationAttachment saved) {
            this.saved = saved;
        }

        @Override
        public ConversationAttachment save(ConversationAttachment attachment) {
            saved = attachment;
            return attachment;
        }

        @Override
        public Optional<ConversationAttachment> findById(String attachmentId) {
            if (saved == null || !saved.attachmentId().equals(attachmentId)) {
                return Optional.empty();
            }
            return Optional.of(saved);
        }

        @Override
        public List<ConversationAttachment> listByConversation(String conversationId, String userId) {
            return List.of();
        }

        @Override
        public boolean delete(String attachmentId, String userId) {
            if (saved == null || !saved.attachmentId().equals(attachmentId) || !saved.userId().equals(userId)) {
                return false;
            }
            saved = null;
            return true;
        }
    }

    private static final class InMemoryObjectStoragePort implements ObjectStoragePort {

        private final byte[] content;

        private InMemoryObjectStoragePort(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            return new StoredObject("memory://uploaded", contentType, size, originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }
}
