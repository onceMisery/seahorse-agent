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
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcConversationAttachmentRepositoryAdapter implements ConversationAttachmentRepositoryPort {

    private static final String COLUMNS = """
            attachment_id, conversation_id, message_id, user_id, file_name, mime_type, size_bytes, storage_ref,
            parse_status, resource_ref_json, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_conversation_attachment
            (attachment_id, conversation_id, message_id, user_id, file_name, mime_type, size_bytes, storage_ref,
             parse_status, resource_ref_json, created_at, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_conversation_attachment
            SET conversation_id = ?,
                message_id = ?,
                user_id = ?,
                file_name = ?,
                mime_type = ?,
                size_bytes = ?,
                storage_ref = ?,
                parse_status = ?,
                resource_ref_json = ?,
                created_at = ?,
                deleted = 0
            WHERE attachment_id = ?
            """;
    private static final String SQL_FIND = """
            SELECT %s
            FROM sa_conversation_attachment
            WHERE attachment_id = ? AND deleted = 0
            """.formatted(COLUMNS);
    private static final String SQL_LIST_BY_CONVERSATION = """
            SELECT %s
            FROM sa_conversation_attachment
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0
            ORDER BY created_at ASC, attachment_id ASC
            """.formatted(COLUMNS);
    private static final String SQL_DELETE = """
            UPDATE sa_conversation_attachment
            SET deleted = 1
            WHERE attachment_id = ? AND user_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcConversationAttachmentRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public ConversationAttachment save(ConversationAttachment attachment) {
        ConversationAttachment safeAttachment = Objects.requireNonNull(attachment, "attachment must not be null");
        if (findById(safeAttachment.attachmentId()).isPresent()) {
            update(safeAttachment);
            return safeAttachment;
        }
        insert(safeAttachment);
        return safeAttachment;
    }

    @Override
    public Optional<ConversationAttachment> findById(String attachmentId) {
        if (!hasText(attachmentId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND, this::mapAttachment, attachmentId.trim()).stream().findFirst();
    }

    @Override
    public List<ConversationAttachment> listByConversation(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BY_CONVERSATION, this::mapAttachment, conversationId.trim(), userId.trim());
    }

    @Override
    public boolean delete(String attachmentId, String userId) {
        if (!hasText(attachmentId) || !hasText(userId)) {
            return false;
        }
        return jdbcTemplate.update(SQL_DELETE, attachmentId.trim(), userId.trim()) > 0;
    }

    private void insert(ConversationAttachment attachment) {
        jdbcTemplate.update(SQL_INSERT,
                attachment.attachmentId(),
                attachment.conversationId(),
                attachment.messageId(),
                attachment.userId(),
                attachment.fileName(),
                attachment.mimeType(),
                attachment.sizeBytes(),
                attachment.storageRef(),
                attachment.parseStatus().name(),
                attachment.resourceRefJson(),
                toTimestamp(attachment.createdAt()));
    }

    private void update(ConversationAttachment attachment) {
        jdbcTemplate.update(SQL_UPDATE,
                attachment.conversationId(),
                attachment.messageId(),
                attachment.userId(),
                attachment.fileName(),
                attachment.mimeType(),
                attachment.sizeBytes(),
                attachment.storageRef(),
                attachment.parseStatus().name(),
                attachment.resourceRefJson(),
                toTimestamp(attachment.createdAt()),
                attachment.attachmentId());
    }

    private ConversationAttachment mapAttachment(ResultSet resultSet, int rowNum) throws SQLException {
        return new ConversationAttachment(
                resultSet.getString("attachment_id"),
                resultSet.getString("conversation_id"),
                resultSet.getString("message_id"),
                resultSet.getString("user_id"),
                resultSet.getString("file_name"),
                resultSet.getString("mime_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("storage_ref"),
                ConversationAttachmentParseStatus.valueOf(resultSet.getString("parse_status")),
                resultSet.getString("resource_ref_json"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
