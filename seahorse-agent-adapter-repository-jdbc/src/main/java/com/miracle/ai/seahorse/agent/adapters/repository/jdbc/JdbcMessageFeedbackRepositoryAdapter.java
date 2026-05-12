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

import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackSubmission;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 基于旧反馈表的 JDBC 原生 adapter。
 *
 * <p>复用 {@code t_message_feedback} 和 {@code t_message}，面向 Seahorse 原生反馈端口提供持久化能力。
 */
public class JdbcMessageFeedbackRepositoryAdapter implements MessageFeedbackRepositoryPort {

    private static final String ROLE_ASSISTANT = "assistant";
    private static final String SQL_FIND_CONVERSATION = """
            SELECT conversation_id
            FROM t_message
            WHERE id = ? AND user_id = ? AND role = ? AND deleted = 0
            LIMIT 1
            """;
    private static final String SQL_FIND_FEEDBACK_ID = """
            SELECT id
            FROM t_message_feedback
            WHERE message_id = ? AND user_id = ? AND deleted = 0
            LIMIT 1
            """;
    private static final String SQL_INSERT_FEEDBACK = """
            INSERT INTO t_message_feedback
            (id, message_id, conversation_id, user_id, vote, reason, comment, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_UPDATE_FEEDBACK = """
            UPDATE t_message_feedback
            SET vote = ?, reason = ?, comment = ?, update_time = ?
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_FIND_VOTES = """
            SELECT message_id, vote
            FROM t_message_feedback
            WHERE user_id = ? AND deleted = 0 AND message_id IN (%s)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMessageFeedbackRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void upsert(MessageFeedbackSubmission feedback) {
        MessageFeedbackSubmission safeFeedback = Objects.requireNonNull(feedback, "feedback must not be null");
        String conversationId = requireAssistantConversation(safeFeedback);
        String feedbackId = findFeedbackId(safeFeedback);
        if (feedbackId == null) {
            insertFeedback(safeFeedback, conversationId);
            return;
        }
        updateFeedback(safeFeedback, feedbackId);
    }

    @Override
    public Map<String, Integer> findUserVotes(String userId, List<String> messageIds) {
        if (!hasText(userId) || messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = messageIds.stream()
                .filter(this::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(","));
        Object[] args = buildVoteArgs(userId, ids);
        return jdbcTemplate.query(String.format(SQL_FIND_VOTES, placeholders), (resultSet, rowNum) ->
                Map.entry(resultSet.getString("message_id"), resultSet.getInt("vote")), args)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
    }

    private String requireAssistantConversation(MessageFeedbackSubmission feedback) {
        List<String> conversations = jdbcTemplate.query(SQL_FIND_CONVERSATION,
                (resultSet, rowNum) -> resultSet.getString("conversation_id"),
                feedback.messageId(), feedback.userId(), ROLE_ASSISTANT);
        if (conversations.isEmpty() || !hasText(conversations.get(0))) {
            throw new IllegalArgumentException("assistant message not found");
        }
        return conversations.get(0);
    }

    private String findFeedbackId(MessageFeedbackSubmission feedback) {
        List<String> ids = jdbcTemplate.query(SQL_FIND_FEEDBACK_ID,
                (resultSet, rowNum) -> resultSet.getString("id"),
                feedback.messageId(), feedback.userId());
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    private void insertFeedback(MessageFeedbackSubmission feedback, String conversationId) {
        Timestamp now = Timestamp.from(feedback.submitTime());
        jdbcTemplate.update(SQL_INSERT_FEEDBACK,
                nextId(),
                feedback.messageId(),
                conversationId,
                feedback.userId(),
                feedback.vote(),
                feedback.reason(),
                feedback.comment(),
                now,
                now);
    }

    private void updateFeedback(MessageFeedbackSubmission feedback, String feedbackId) {
        jdbcTemplate.update(SQL_UPDATE_FEEDBACK,
                feedback.vote(),
                feedback.reason(),
                feedback.comment(),
                Timestamp.from(feedback.submitTime()),
                feedbackId);
    }

    private Object[] buildVoteArgs(String userId, List<String> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = userId;
        for (int index = 0; index < ids.size(); index++) {
            args[index + 1] = ids.get(index);
        }
        return args;
    }

    private String nextId() {
        long millis = System.currentTimeMillis();
        int suffix = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return Long.toString(millis, 36) + suffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
