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
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionUpdateValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSampleQuestionRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcSampleQuestionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sample-question;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcSampleQuestionRepositoryAdapter(dataSource);
    }

    @Test
    void shouldCreateQueryUpdateAndDeleteSampleQuestion() {
        String id = adapter.create("title", "desc", "question");

        boolean updated = adapter.update(id, new SampleQuestionUpdateValues(
                null, "new desc", "new question", true, true, true));
        SampleQuestionRecord updatedRecord = adapter.findById(id).orElseThrow();
        boolean deleted = adapter.delete(id);

        assertThat(updated).isTrue();
        assertThat(updatedRecord.title()).isNull();
        assertThat(updatedRecord.description()).isEqualTo("new desc");
        assertThat(updatedRecord.question()).isEqualTo("new question");
        assertThat(deleted).isTrue();
        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void shouldPageByKeywordAndListRandomQuestions() {
        insertQuestion("1", "RAG", "intro", "what is rag", 3);
        insertQuestion("2", "MCP", "tools", "what is mcp", 2);
        insertQuestion("3", "Memory", "rag memory", "how to remember", 1);
        insertDeletedQuestion();

        SampleQuestionPage page = adapter.page(1, 2, "rag");
        List<SampleQuestionRecord> randomQuestions = adapter.listRandomQuestions(3);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.pages()).isEqualTo(1);
        assertThat(page.records()).extracting(SampleQuestionRecord::id).containsExactly("3", "1");
        assertThat(randomQuestions).hasSize(3);
        assertThat(randomQuestions).extracting(SampleQuestionRecord::id).doesNotContain("4");
    }

    private void insertQuestion(String id, String title, String description, String question, int secondsAgo) {
        Timestamp updateTime = Timestamp.from(Instant.now().minusSeconds(secondsAgo));
        jdbcTemplate.update("""
                INSERT INTO t_sample_question
                (id, title, description, question, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, id, title, description, question, updateTime, updateTime);
    }

    private void insertDeletedQuestion() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_sample_question
                (id, title, description, question, create_time, update_time, deleted)
                VALUES ('4', 'deleted', 'deleted', 'deleted', ?, ?, 1)
                """, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_sample_question");
        jdbcTemplate.execute("""
                CREATE TABLE t_sample_question (
                    id VARCHAR(20) PRIMARY KEY,
                    title VARCHAR(64),
                    description VARCHAR(255),
                    question VARCHAR(255) NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}
