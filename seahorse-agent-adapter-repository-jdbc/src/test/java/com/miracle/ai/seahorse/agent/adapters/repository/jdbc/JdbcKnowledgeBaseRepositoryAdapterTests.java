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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeBaseValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseUpdateValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKnowledgeBaseRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcKnowledgeBaseRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:knowledge-base;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcKnowledgeBaseRepositoryAdapter(dataSource);
    }

    @Test
    void shouldCreateQueryUpdateAndDeleteKnowledgeBase() {
        Long id = adapter.create(new CreateKnowledgeBaseValues("Research KB", "embed-a", "kb_rnd", "tester"));

        boolean exists = adapter.nameExists("ResearchKB", null);
        boolean updated = adapter.update(id, new KnowledgeBaseUpdateValues("Research KB Updated", "embed-b", "tester"));
        KnowledgeBaseRecord updatedRecord = adapter.findById(id).orElseThrow();
        boolean deleted = adapter.delete(id, "tester");

        assertThat(exists).isTrue();
        assertThat(updated).isTrue();
        assertThat(updatedRecord.getName()).isEqualTo("Research KB Updated");
        assertThat(updatedRecord.getEmbeddingModel()).isEqualTo("embed-b");
        assertThat(deleted).isTrue();
        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void shouldCastExcludedKnowledgeBaseIdNullCheckForPostgres() {
        DriverManagerDataSource delegate = new DriverManagerDataSource(
                "jdbc:h2:mem:knowledge-base-sql-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        RecordingDataSource dataSource = new RecordingDataSource(delegate);
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();

        new JdbcKnowledgeBaseRepositoryAdapter(dataSource).nameExists("Alpha", null);

        assertThat(String.join("\n", dataSource.sql()))
                .contains("CAST(? AS VARCHAR) IS NULL OR id <> ?");
    }

    @Test
    void shouldPageKnowledgeBasesWithDocumentCount() {
        insertKnowledgeBase(1L, "Alpha", "col-a", 3);
        insertKnowledgeBase(2L, "Beta", "col-b", 2);
        insertDeletedKnowledgeBase();
        insertDocument(1L, 1L, 2);
        insertDocument(2L, 1L, 0);
        insertDocument(3L, 2L, 0);

        KnowledgeBasePage page = adapter.page(1, 10, "a");

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).extracting(KnowledgeBaseRecord::getId).containsExactly(2L, 1L);
        assertThat(page.records().get(1).getDocumentCount()).isEqualTo(2);
        assertThat(adapter.hasDocuments(1L)).isTrue();
        assertThat(adapter.hasVectorizedDocuments(1L)).isTrue();
        assertThat(adapter.hasVectorizedDocuments(2L)).isFalse();
    }

    private void insertKnowledgeBase(Long id, String name, String collectionName, int secondsAgo) {
        Timestamp updateTime = Timestamp.from(Instant.now().minusSeconds(secondsAgo));
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base
                (id, name, embedding_model, collection_name, created_by, updated_by,
                 create_time, update_time, deleted)
                VALUES (?, ?, 'embed', ?, 'tester', 'tester', ?, ?, 0)
                """, id, name, collectionName, updateTime, updateTime);
    }

    private void insertDeletedKnowledgeBase() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base
                (id, name, embedding_model, collection_name, created_by, updated_by,
                 create_time, update_time, deleted)
                VALUES (3, 'Gamma', 'embed', 'col-c', 'tester', 'tester', ?, ?, 1)
                """, now, now);
    }

    private void insertDocument(Long id, Long kbId, int chunkCount) {
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, kb_id, doc_name, chunk_count, deleted)
                VALUES (?, ?, ?, ?, 0)
                """, id, kbId, "doc-" + id, chunkCount);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(128) NOT NULL,
                    embedding_model VARCHAR(128),
                    collection_name VARCHAR(128) NOT NULL,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT NOT NULL,
                    doc_name VARCHAR(128),
                    chunk_count INTEGER DEFAULT 0,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }

    private static final class RecordingDataSource extends AbstractDataSource {

        private final DataSource delegate;
        private final List<String> sql = new CopyOnWriteArrayList<>();

        private RecordingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return record(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return record(delegate.getConnection(username, password));
        }

        private List<String> sql() {
            return sql;
        }

        private Connection record(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    connection.getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (args != null
                                && args.length > 0
                                && args[0] instanceof String statementSql
                                && method.getName().startsWith("prepare")) {
                            sql.add(statementSql);
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getTargetException();
                        }
                    });
        }
    }
}
