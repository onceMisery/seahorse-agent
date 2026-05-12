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

import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionUpdateValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于旧 {@code t_sample_question} 表的 JDBC 示例问题 adapter。
 */
public class JdbcSampleQuestionRepositoryAdapter implements SampleQuestionRepositoryPort {

    private static final String SQL_RANDOM = """
            SELECT id, title, description, question, create_time, update_time
            FROM t_sample_question
            WHERE deleted = 0
            ORDER BY RANDOM()
            LIMIT ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT id, title, description, question, create_time, update_time
            FROM t_sample_question
            WHERE id = ? AND deleted = 0
            LIMIT 1
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_sample_question
            (id, title, description, question, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSampleQuestionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<SampleQuestionRecord> listRandomQuestions(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_RANDOM, this::mapRecord, limit);
    }

    @Override
    public SampleQuestionPage page(long current, long size, String keyword) {
        long offset = (current - 1L) * size;
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(keyword, args);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_sample_question " + whereClause,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        List<SampleQuestionRecord> records = jdbcTemplate.query("""
                SELECT id, title, description, question, create_time, update_time
                FROM t_sample_question
                %s
                ORDER BY update_time DESC
                LIMIT ? OFFSET ?
                """.formatted(whereClause), this::mapRecord, pageArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        return new SampleQuestionPage(records, safeTotal, size, current, calculatePages(safeTotal, size));
    }

    @Override
    public Optional<SampleQuestionRecord> findById(String id) {
        if (!hasText(id)) {
            return Optional.empty();
        }
        List<SampleQuestionRecord> records = jdbcTemplate.query(SQL_FIND_BY_ID, this::mapRecord, id);
        return records.stream().findFirst();
    }

    @Override
    public String create(String title, String description, String question) {
        String id = nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT, id, title, description, question, now, now);
        return id;
    }

    @Override
    public boolean update(String id, SampleQuestionUpdateValues values) {
        SampleQuestionUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        if (!hasText(id)) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        String setClause = buildUpdateSetClause(safeValues, args);
        args.add(id);
        int updated = jdbcTemplate.update("""
                UPDATE t_sample_question
                SET %s
                WHERE id = ? AND deleted = 0
                """.formatted(setClause), args.toArray());
        return updated > 0;
    }

    @Override
    public boolean delete(String id) {
        if (!hasText(id)) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                UPDATE t_sample_question
                SET deleted = 1, update_time = ?
                WHERE id = ? AND deleted = 0
                """, Timestamp.from(Instant.now()), id);
        return updated > 0;
    }

    private String buildWhereClause(String keyword, List<Object> args) {
        if (!hasText(keyword)) {
            return "WHERE deleted = 0";
        }
        String pattern = "%" + keyword.trim() + "%";
        args.add(pattern);
        args.add(pattern);
        args.add(pattern);
        return """
                WHERE deleted = 0
                  AND (title LIKE ? OR description LIKE ? OR question LIKE ?)
                """;
    }

    private String buildUpdateSetClause(SampleQuestionUpdateValues values, List<Object> args) {
        List<String> fragments = new ArrayList<>();
        if (values.titlePresent()) {
            fragments.add("title = ?");
            args.add(values.title());
        }
        if (values.descriptionPresent()) {
            fragments.add("description = ?");
            args.add(values.description());
        }
        if (values.questionPresent()) {
            fragments.add("question = ?");
            args.add(values.question());
        }
        fragments.add("update_time = ?");
        args.add(Timestamp.from(Instant.now()));
        return String.join(", ", fragments);
    }

    private SampleQuestionRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp createTime = resultSet.getTimestamp("create_time");
        Timestamp updateTime = resultSet.getTimestamp("update_time");
        return new SampleQuestionRecord(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("question"),
                createTime == null ? null : createTime.toInstant(),
                updateTime == null ? null : updateTime.toInstant());
    }

    private long calculatePages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
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
