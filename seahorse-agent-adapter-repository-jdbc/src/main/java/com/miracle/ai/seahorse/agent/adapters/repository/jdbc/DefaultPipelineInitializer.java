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

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 默认 Pipeline 初始化器。
 *
 * <p>应用启动时检查 Pipeline 表，如果为空则自动创建默认的文档处理 Pipeline。
 */
public class DefaultPipelineInitializer implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DefaultPipelineInitializer.class);

    private static final String DEFAULT_PIPELINE_NAME = "default-chunk-pipeline";
    private static final String DEFAULT_PIPELINE_DESC = "默认文档分块流水线";

    private final JdbcTemplate jdbcTemplate;
    private final IngestionPipelineRepositoryPort pipelineRepository;

    public DefaultPipelineInitializer(
            JdbcTemplate jdbcTemplate,
            IngestionPipelineRepositoryPort pipelineRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.pipelineRepository = pipelineRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_ingestion_pipeline WHERE deleted = 0",
                    Long.class);

            if (count == null || count == 0) {
                log.info("No pipeline found, creating default pipeline: {}", DEFAULT_PIPELINE_NAME);
                createDefaultPipeline();
                log.info("Default pipeline created successfully");
            } else {
                log.debug("Found {} existing pipeline(s), skipping default pipeline creation", count);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize default pipeline", ex);
            // 不抛出异常，避免阻止应用启动
        }
    }

    private void createDefaultPipeline() {
        // 创建 Pipeline
        jdbcTemplate.update(
                "INSERT INTO t_ingestion_pipeline (id, name, description, created_by, updated_by, deleted) " +
                "VALUES (1, ?, ?, 0, 0, 0)",
                DEFAULT_PIPELINE_NAME, DEFAULT_PIPELINE_DESC);

        // 创建 Parser 节点
        jdbcTemplate.update(
                "INSERT INTO t_ingestion_pipeline_node (id, pipeline_id, node_id, node_type, next_node_id, settings_json, created_by, updated_by, deleted) " +
                "VALUES (1, 1, 1, 'parser', 2, '{\"extractMetadata\": true}'::jsonb, 0, 0, 0)");

        // 创建 Chunker 节点
        jdbcTemplate.update(
                "INSERT INTO t_ingestion_pipeline_node (id, pipeline_id, node_id, node_type, next_node_id, settings_json, created_by, updated_by, deleted) " +
                "VALUES (2, 1, 2, 'chunker', 3, '{\"strategy\": \"fixed\", \"chunkSize\": 500, \"overlap\": 50}'::jsonb, 0, 0, 0)");

        // 创建 Embedder 节点
        jdbcTemplate.update(
                "INSERT INTO t_ingestion_pipeline_node (id, pipeline_id, node_id, node_type, next_node_id, settings_json, created_by, updated_by, deleted) " +
                "VALUES (3, 1, 3, 'embedder', 4, '{\"modelName\": \"default\"}'::jsonb, 0, 0, 0)");

        // 创建 Indexer 节点
        jdbcTemplate.update(
                "INSERT INTO t_ingestion_pipeline_node (id, pipeline_id, node_id, node_type, next_node_id, settings_json, created_by, updated_by, deleted) " +
                "VALUES (4, 1, 4, 'indexer', null, '{}'::jsonb, 0, 0, 0)");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
