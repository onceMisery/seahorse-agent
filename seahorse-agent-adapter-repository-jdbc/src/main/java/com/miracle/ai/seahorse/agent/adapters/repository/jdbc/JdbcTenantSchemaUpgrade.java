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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * 幂等执行多租户 schema 升级：为 P0 核心表添加 tenant_id 列并启用 RLS。
 * <p>
 * 该升级类遵循 {@link JdbcChatSchemaUpgrade} 的模式，在应用启动时自动执行。
 * 所有 DDL 操作都是幂等的（IF NOT EXISTS / 先检查再执行），可安全重复运行。
 */
public class JdbcTenantSchemaUpgrade {

    private static final Logger log = LoggerFactory.getLogger(JdbcTenantSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * P0 阶段需要新增 tenant_id 列的表（当前无此列）。
     */
    private static final List<String> TABLES_NEEDING_TENANT_ID = List.of(
            "t_user",
            "t_conversation",
            "t_conversation_summary",
            "t_message",
            "sa_conversation_attachment",
            "t_message_feedback",
            "t_knowledge_base",
            "t_knowledge_document",
            "t_knowledge_chunk",
            "t_knowledge_document_chunk_log",
            "t_knowledge_vector",
            "t_intent_node",
            "t_query_term_mapping",
            "t_rag_trace_run",
            "t_rag_trace_node",
            "t_sample_question"
    );

    /**
     * P0 阶段需要启用 RLS 的所有表（包括新增 tenant_id 的和已有 tenant_id 的）。
     */
    private static final List<String> TABLES_NEEDING_RLS = List.of(
            "t_user",
            "t_conversation",
            "t_conversation_summary",
            "t_message",
            "sa_conversation_attachment",
            "t_message_feedback",
            "t_knowledge_base",
            "t_knowledge_document",
            "t_knowledge_chunk",
            "t_knowledge_document_chunk_log",
            "t_knowledge_vector",
            "t_intent_node",
            "t_query_term_mapping",
            "t_rag_trace_run",
            "t_rag_trace_node",
            "t_sample_question",
            "sa_agent_definition",
            "sa_quota_policy"
    );

    public JdbcTenantSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    /**
     * 执行多租户 schema 升级。
     * <p>
     * 分三阶段：1) 加列 → 2) 建索引 → 3) 启用 RLS。
     * 每一步都是幂等的，可安全重复执行。
     */
    public void upgrade() {
        log.info("[TenantSchema] 开始多租户 schema 升级...");
        addTenantIdColumns();
        enableRowLevelSecurity();
        log.info("[TenantSchema] 多租户 schema 升级完成");
    }

    private void addTenantIdColumns() {
        for (String table : TABLES_NEEDING_TENANT_ID) {
            if (!tableExists(table)) {
                log.debug("[TenantSchema] 表 {} 不存在，跳过", table);
                continue;
            }
            addColumnIfMissing(table, "tenant_id", "VARCHAR(64) NOT NULL DEFAULT 'default'");
        }
    }

    private void enableRowLevelSecurity() {
        for (String table : TABLES_NEEDING_RLS) {
            if (!tableExists(table)) {
                log.debug("[TenantSchema] 表 {} 不存在，跳过 RLS", table);
                continue;
            }
            enableRls(table);
        }
    }

    private void enableRls(String table) {
        try {
            // ENABLE ROW LEVEL SECURITY 是幂等的（PostgreSQL 不会报错）
            jdbcTemplate.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            jdbcTemplate.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");

            // 创建 RLS 策略（先检查是否已存在同名策略）
            Integer policyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_policies WHERE tablename = ? AND policyname = 'rls_tenant_isolation'",
                    Integer.class, table);
            if (policyCount == null || policyCount == 0) {
                jdbcTemplate.execute(
                        "CREATE POLICY rls_tenant_isolation ON " + table
                        + " USING (tenant_id = current_setting('app.current_tenant_id', true))");
                log.debug("[TenantSchema] 为表 {} 创建 RLS 策略", table);
            }
        } catch (Exception e) {
            log.warn("[TenantSchema] 为表 {} 启用 RLS 失败（可能不是 PostgreSQL）: {}", table, e.getMessage());
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("[TenantSchema] 为表 {} 添加列 {}", table, column);
        }
    }
}
