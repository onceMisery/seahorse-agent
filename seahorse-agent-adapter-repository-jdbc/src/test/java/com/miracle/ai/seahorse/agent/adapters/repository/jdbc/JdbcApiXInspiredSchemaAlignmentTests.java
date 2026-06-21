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

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcApiXInspiredSchemaAlignmentTests {

    @Test
    void shouldKeepPackagedPostgresSqlAlignedWithApixInspiredTables() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "META-INF",
                "seahorse-agent",
                "sql",
                "agent-registry-run-store-postgresql.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS t_run_context_snapshot");
        assertThat(sql).contains("COMMENT ON TABLE t_run_context_snapshot IS '运行上下文快照表");
        assertThat(sql).contains("COMMENT ON COLUMN t_run_context_snapshot.executor_engine IS '执行引擎标识");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS t_conversation_branch_cursor");
        assertThat(sql).contains("COMMENT ON TABLE t_conversation_branch_cursor IS '会话分支游标表");
        assertThat(sql).contains("COMMENT ON COLUMN t_conversation_branch_cursor.leaf_message_id IS '当前视图选中的分支叶子消息 ID'");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_run_profile");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_run_profile_tool");
        assertThat(sql).contains("approval_status");
        assertThat(sql).contains("运行画像审批状态");
        assertThat(sql).contains("COMMENT ON TABLE sa_run_profile IS '运行画像表");
        assertThat(sql).contains("COMMENT ON COLUMN sa_run_profile.executor_engine IS '默认执行引擎");
        assertThat(sql).contains("COMMENT ON TABLE sa_run_profile_tool IS '运行画像工具绑定表");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_run_experiment");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_run_experiment_trial");
        assertThat(sql).contains("COMMENT ON TABLE sa_run_experiment IS '运行实验表");
        assertThat(sql).contains("COMMENT ON COLUMN sa_run_experiment_trial.run_id IS '本次试验对应的 Agent run ID");
    }
}
