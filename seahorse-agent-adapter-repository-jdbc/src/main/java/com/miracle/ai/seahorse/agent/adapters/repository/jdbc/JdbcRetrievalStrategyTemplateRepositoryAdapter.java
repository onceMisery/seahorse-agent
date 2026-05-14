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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 JDBC 的知识库检索策略模板覆盖仓储。
 *
 * <p>全局模板先返回，知识库级模板后返回；同名 {@code templateKey} 由知识库级配置覆盖。
 */
public class JdbcRetrievalStrategyTemplateRepositoryAdapter implements RetrievalStrategyTemplateRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRetrievalStrategyTemplateRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<RetrievalStrategyTemplate> listTemplates(String kbId) {
        String safeKbId = Objects.requireNonNullElse(kbId, "").trim();
        try {
            List<RetrievalStrategyTemplate> rows = jdbcTemplate.query("""
                    SELECT template_key, display_name, description, options_json
                    FROM t_retrieval_strategy_template
                    WHERE enabled = 1
                      AND deleted = 0
                      AND (kb_id = ? OR kb_id IS NULL OR kb_id = '')
                    ORDER BY CASE WHEN kb_id = ? THEN 1 ELSE 0 END,
                             sort_order ASC,
                             update_time DESC,
                             template_key ASC
                    """, this::toTemplate, safeKbId, safeKbId);
            return mergeByTemplateKey(rows);
        } catch (DataAccessException ex) {
            return List.of();
        }
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
                options(resultSet.getString("options_json")));
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
}
