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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConversationAttachmentRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveListFindAndDeleteOwnedAttachments() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:conversation-attachment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        JdbcConversationAttachmentRepositoryAdapter adapter = new JdbcConversationAttachmentRepositoryAdapter(dataSource);
        ConversationAttachment owned = attachment("attachment-1", "conversation-1", "user-1");
        ConversationAttachment otherUser = attachment("attachment-2", "conversation-1", "user-2");

        adapter.save(owned);
        adapter.save(otherUser);
        List<ConversationAttachment> listed = adapter.listByConversation("conversation-1", "user-1");
        Optional<ConversationAttachment> found = adapter.findById("attachment-1");
        boolean deletedByOther = adapter.delete("attachment-1", "user-2");
        boolean deletedByOwner = adapter.delete("attachment-1", "user-1");

        assertThat(listed).containsExactly(owned);
        assertThat(found).contains(owned);
        assertThat(deletedByOther).isFalse();
        assertThat(deletedByOwner).isTrue();
        assertThat(adapter.findById("attachment-1")).isEmpty();
    }

    private static ConversationAttachment attachment(String id, String conversationId, String userId) {
        return new ConversationAttachment(
                id,
                conversationId,
                null,
                userId,
                id + ".txt",
                "text/plain",
                12L,
                "storage://" + id,
                ConversationAttachmentParseStatus.PENDING,
                "{\"attachmentId\":\"" + id + "\"}",
                NOW);
    }

    private static void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_conversation_attachment (
                    attachment_id VARCHAR(64) PRIMARY KEY,
                    conversation_id VARCHAR(64) NOT NULL,
                    message_id VARCHAR(64),
                    user_id VARCHAR(64) NOT NULL,
                    file_name VARCHAR(256) NOT NULL,
                    mime_type VARCHAR(128) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    storage_ref VARCHAR(1000) NOT NULL,
                    parse_status VARCHAR(32) NOT NULL,
                    resource_ref_json CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_conversation_attachment_user
                ON sa_conversation_attachment(conversation_id, user_id, created_at)
                """);
    }
}
