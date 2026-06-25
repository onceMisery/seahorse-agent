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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的知识库检索策略模板覆盖仓储。
 *
 * <p>全局模板先返回，知识库级模板后返回；同名 {@code templateKey} 由知识库级配置覆盖。
 */
public class JdbcRetrievalStrategyTemplateRepositoryAdapter implements RetrievalStrategyTemplateRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String optionsJsonPlaceholder;

    public JdbcRetrievalStrategyTemplateRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        DataSource safeDataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcTemplate = new JdbcTemplate(safeDataSource);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.optionsJsonPlaceholder = isPostgres(safeDataSource) ? "?::jsonb" : "?";
    }

    @Override
    public List<RetrievalStrategyTemplate> listTemplates(String kbId) {
        String safeKbId = Objects.requireNonNullElse(kbId, "").trim();
        try {
            List<RetrievalStrategyTemplate> rows = jdbcTemplate.query("""
                    SELECT template_key, display_name, description, options_json, recommended
                    FROM t_retrieval_strategy_template
                    WHERE enabled = 1
                      AND deleted = 0
                      AND (kb_id = ? OR kb_id IS NULL OR kb_id = '')
                    ORDER BY CASE WHEN kb_id = ? THEN 1 ELSE 0 END,
                             recommended DESC,
                             sort_order ASC,
                             update_time DESC,
                             template_key ASC
                    """, this::toTemplate, safeKbId, safeKbId);
            return mergeByTemplateKey(rows);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public RetrievalStrategyTemplate upsertTemplate(String kbId, RetrievalStrategyTemplatePayload payload) {
        String safeKbId = Objects.requireNonNullElse(kbId, "").trim();
        RetrievalStrategyTemplatePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String optionsJson = json(safePayload.options());
        int enabled = Boolean.FALSE.equals(safePayload.enabled()) ? 0 : 1;
        // 软删除后再次保存同 key 时复用原记录，避免触发范围唯一索引。
        int updated = jdbcTemplate.update("""
                UPDATE t_retrieval_strategy_template
                SET display_name = ?,
                    description = ?,
                    options_json = %s,
                    sort_order = ?,
                    recommended = 0,
                    enabled = ?,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE COALESCE(kb_id, '') = ?
                  AND template_key = ?
                """.formatted(optionsJsonPlaceholder), safePayload.displayName(), safePayload.description(), optionsJson,
                safePayload.sortOrder(), enabled, safeKbId, safePayload.templateKey());
        if (updated <= 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_strategy_template(
                        id, kb_id, template_key, display_name, description, options_json,
                        sort_order, recommended, enabled, create_time, update_time, deleted
                    ) VALUES (?, ?, ?, ?, ?, %s, ?, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """.formatted(optionsJsonPlaceholder), SnowflakeIds.nextIdString(), safeKbId, safePayload.templateKey(),
                    safePayload.displayName(), safePayload.description(), optionsJson,
                    safePayload.sortOrder(), enabled);
        }
        return findTemplate(safeKbId, safePayload.templateKey()).orElseGet(safePayload::toTemplate);
    }

    @Override
    public RetrievalStrategyTemplate promoteRecommendedTemplate(String kbId, RetrievalStrategyTemplatePayload payload) {
        String safeKbId = Objects.requireNonNullElse(kbId, "").trim();
        RetrievalStrategyTemplatePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String optionsJson = json(safePayload.options());
        int enabled = Boolean.FALSE.equals(safePayload.enabled()) ? 0 : 1;
        jdbcTemplate.update("""
                UPDATE t_retrieval_strategy_template
                SET recommended = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE COALESCE(kb_id, '') = ?
                  AND recommended = 1
                  AND deleted = 0
                """, safeKbId);
        int updated = jdbcTemplate.update("""
                UPDATE t_retrieval_strategy_template
                SET display_name = ?,
                    description = ?,
                    options_json = %s,
                    sort_order = ?,
                    recommended = 1,
                    enabled = ?,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE COALESCE(kb_id, '') = ?
                  AND template_key = ?
                """.formatted(optionsJsonPlaceholder), safePayload.displayName(), safePayload.description(), optionsJson,
                safePayload.sortOrder(), enabled, safeKbId, safePayload.templateKey());
        if (updated <= 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_retrieval_strategy_template(
                        id, kb_id, template_key, display_name, description, options_json,
                        sort_order, recommended, enabled, create_time, update_time, deleted
                    ) VALUES (?, ?, ?, ?, ?, %s, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """.formatted(optionsJsonPlaceholder), SnowflakeIds.nextIdString(), safeKbId, safePayload.templateKey(),
                    safePayload.displayName(), safePayload.description(), optionsJson,
                    safePayload.sortOrder(), enabled);
        }
        return findTemplate(safeKbId, safePayload.templateKey()).orElseGet(() -> new RetrievalStrategyTemplate(
                safePayload.templateKey(),
                safePayload.displayName(),
                safePayload.description(),
                safePayload.options(),
                true));
    }

    @Override
    public boolean deleteTemplate(String kbId, String templateKey) {
        String safeKbId = Objects.requireNonNullElse(kbId, "").trim();
        String safeTemplateKey = Objects.requireNonNullElse(templateKey, "").trim();
        if (safeTemplateKey.isBlank()) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE t_retrieval_strategy_template
                SET deleted = 1,
                    enabled = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE COALESCE(kb_id, '') = ?
                  AND template_key = ?
                  AND deleted = 0
                """, safeKbId, safeTemplateKey) > 0;
    }

    private List<RetrievalStrategyTemplate> mergeByTemplateKey(List<RetrievalStrategyTemplate> templates) {
        Map<String, RetrievalStrategyTemplate> merged = new LinkedHashMap<>();
        for (RetrievalStrategyTemplate template : templates) {
            if (template.templateKey().isBlank()) {
                continue;
            }
            // 查询结果已保证全局在前、知识库级在后，这里按 key 覆盖即可得到最终模板集。
            merged.put(template.templateKey(), template);
        }
        return List.copyOf(merged.values());
    }

    private RetrievalStrategyTemplate toTemplate(ResultSet resultSet, int rowNum) throws SQLException {
        return new RetrievalStrategyTemplate(
                resultSet.getString("template_key"),
                resultSet.getString("display_name"),
                resultSet.getString("description"),
                options(resultSet.getString("options_json")),
                resultSet.getInt("recommended") == 1);
    }

    private Optional<RetrievalStrategyTemplate> findTemplate(String kbId, String templateKey) {
        try {
            return jdbcTemplate.query("""
                    SELECT template_key, display_name, description, options_json, recommended
                    FROM t_retrieval_strategy_template
                    WHERE COALESCE(kb_id, '') = ?
                      AND template_key = ?
                      AND deleted = 0
                    """, this::toTemplate, kbId, templateKey).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    private RetrievalOptions options(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return RetrievalOptions.defaults(5);
        }
        try {
            return objectMapper.readValue(optionsJson, RetrievalOptions.class);
        } catch (JsonProcessingException ex) {
            // 单条模板配置异常时降级默认参数，避免管理端列表整体不可用。
            return RetrievalOptions.defaults(5);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNull(value, "value must not be null"));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize retrieval strategy template failed", ex);
        }
    }

    private boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData == null ? "" : metaData.getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
