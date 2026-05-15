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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRetrievalStrategyTemplateRepositoryAdapterTests {

    @Test
    void shouldListKnowledgeBaseTemplatesAndOverrideGlobalTemplates() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-list");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        insertTemplate(jdbcTemplate, "1", "", "hybrid_rrf", "全局 RRF", 1, 1, 0,
                "{\"finalTopK\":6,\"enableVector\":true,\"enableKeyword\":true,\"enableRrf\":true}");
        insertTemplate(jdbcTemplate, "2", "kb-1", "hybrid_rrf", "知识库 RRF", 2, 1, 0,
                "{\"finalTopK\":9,\"enableVector\":true,\"enableKeyword\":true,\"enableRrf\":true}");
        insertTemplate(jdbcTemplate, "3", "kb-1", "kb_custom", "知识库自定义", 3, 1, 0,
                "{\"finalTopK\":4,\"enableVector\":true,\"enableKeyword\":false,\"enableRrf\":false}");
        insertTemplate(jdbcTemplate, "4", "kb-2", "other_kb", "其他知识库", 4, 1, 0,
                "{\"finalTopK\":5}");
        insertTemplate(jdbcTemplate, "5", "kb-1", "disabled", "已禁用", 5, 0, 0,
                "{\"finalTopK\":5}");
        insertTemplate(jdbcTemplate, "6", "kb-1", "deleted", "已删除", 6, 1, 1,
                "{\"finalTopK\":5}");
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("hybrid_rrf", "kb_custom");
        assertThat(templates.get(0).displayName()).isEqualTo("知识库 RRF");
        assertThat(templates.get(0).options().finalTopK()).isEqualTo(9);
        assertThat(templates.get(1).options().finalTopK()).isEqualTo(4);
    }

    @Test
    void shouldReturnEmptyWhenTemplateTableDoesNotExist() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-missing");
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(templates).isEmpty();
    }

    @Test
    void shouldFallbackToDefaultOptionsWhenOptionsJsonIsInvalid() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-invalid-json");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        insertTemplate(jdbcTemplate, "1", "kb-1", "broken", "坏配置", 1, 1, 0, "{invalid");
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).templateKey()).isEqualTo("broken");
        assertThat(templates.get(0).options().finalTopK()).isEqualTo(5);
    }

    @Test
    void shouldUpsertKnowledgeBaseTemplateOverride() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-upsert");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        RetrievalStrategyTemplate created = adapter.upsertTemplate("kb-1", payload("hybrid_rrf", 7));
        RetrievalStrategyTemplate updated = adapter.upsertTemplate("kb-1", payload("hybrid_rrf", 9));
        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(created.options().finalTopK()).isEqualTo(7);
        assertThat(updated.options().finalTopK()).isEqualTo(9);
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).displayName()).isEqualTo("模板-hybrid_rrf");
        assertThat(templates.get(0).options().finalTopK()).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM t_retrieval_strategy_template
                WHERE kb_id = 'kb-1' AND template_key = 'hybrid_rrf' AND deleted = 0
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldSoftDeleteKnowledgeBaseTemplateOverride() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-delete");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        insertTemplate(jdbcTemplate, "1", "kb-1", "keyword_precise", "关键词精确", 1, 1, 0,
                "{\"finalTopK\":3,\"enableKeyword\":true}");
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        boolean deleted = adapter.deleteTemplate("kb-1", "keyword_precise");
        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(deleted).isTrue();
        assertThat(templates).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted
                FROM t_retrieval_strategy_template
                WHERE kb_id = 'kb-1' AND template_key = 'keyword_precise'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRestoreSoftDeletedTemplateOnUpsert() {
        DriverManagerDataSource dataSource = dataSource("retrieval-template-restore");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema(jdbcTemplate);
        insertTemplate(jdbcTemplate, "1", "kb-1", "keyword_precise", "旧关键词精确", 1, 0, 1,
                "{\"finalTopK\":3,\"enableKeyword\":true}");
        JdbcRetrievalStrategyTemplateRepositoryAdapter adapter =
                new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, new ObjectMapper());

        RetrievalStrategyTemplate restored = adapter.upsertTemplate("kb-1", payload("keyword_precise", 8));
        List<RetrievalStrategyTemplate> templates = adapter.listTemplates("kb-1");

        assertThat(restored.displayName()).isEqualTo("模板-keyword_precise");
        assertThat(restored.options().finalTopK()).isEqualTo(8);
        assertThat(templates).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM t_retrieval_strategy_template
                WHERE kb_id = 'kb-1' AND template_key = 'keyword_precise'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted
                FROM t_retrieval_strategy_template
                WHERE kb_id = 'kb-1' AND template_key = 'keyword_precise'
                """, Integer.class)).isEqualTo(0);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_retrieval_strategy_template (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64),
                    template_key VARCHAR(128) NOT NULL,
                    display_name VARCHAR(128) NOT NULL,
                    description VARCHAR(512),
                    options_json VARCHAR(4096) NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_retrieval_strategy_template_scope
                ON t_retrieval_strategy_template (kb_id, template_key)
                """);
    }

    private void insertTemplate(JdbcTemplate jdbcTemplate,
                                String id,
                                String kbId,
                                String templateKey,
                                String displayName,
                                int sortOrder,
                                int enabled,
                                int deleted,
                                String optionsJson) {
        jdbcTemplate.update("""
                INSERT INTO t_retrieval_strategy_template
                (id, kb_id, template_key, display_name, description, options_json, sort_order, enabled, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, kbId, templateKey, displayName, "desc-" + id, optionsJson, sortOrder, enabled, deleted);
    }

    private RetrievalStrategyTemplatePayload payload(String templateKey, int finalTopK) {
        return new RetrievalStrategyTemplatePayload(
                templateKey,
                "模板-" + templateKey,
                "desc-" + templateKey,
                RetrievalOptions.builder()
                        .finalTopK(finalTopK)
                        .enableVector(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .build(),
                10,
                true);
    }
}
