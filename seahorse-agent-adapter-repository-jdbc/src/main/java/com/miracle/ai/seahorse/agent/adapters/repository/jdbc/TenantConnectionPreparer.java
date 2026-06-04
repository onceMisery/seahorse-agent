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

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 在每次从连接池获取数据库连接时，将当前租户 ID 设置为 PostgreSQL 的 session 变量，
 * 供 RLS（Row Level Security）策略读取。
 * <p>
 * 典型集成方式：作为 HikariCP 的 {@code ConnectionInitSql} 或通过 Spring AOP
 * 在 {@code DataSource.getConnection()} 后执行。
 * <p>
 * <b>RLS 集成原理</b>：
 * <ol>
 *   <li>{@link TenantContext} 中的 tenantId 通过 {@code SET app.current_tenant_id} 写入连接</li>
 *   <li>RLS 策略中的 {@code current_setting('app.current_tenant_id', true)} 读取此值</li>
 *   <li>数据库层强制过滤非当前租户的数据，作为应用层过滤的第二道防线</li>
 * </ol>
 */
public class TenantConnectionPreparer {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionPreparer.class);
    private static final String SET_TENANT_SQL = "SET app.current_tenant_id = '%s'";

    /**
     * 将当前 {@link TenantContext} 中的租户 ID 设置到数据库连接的 session 变量中。
     * <p>
     * 如果 TenantContext 未设置（如系统启动阶段、定时任务），则使用
     * {@link com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants#DEFAULT_TENANT_ID}。
     *
     * @param connection 数据库连接
     * @throws SQLException SQL 执行异常
     */
    public void prepare(Connection connection) throws SQLException {
        String tenantId = TenantContext.get();
        // 防止 SQL 注入：tenantId 只允许字母、数字、下划线、中划线
        if (!tenantId.matches("^[a-zA-Z0-9_-]+$")) {
            log.error("[TenantConnectionPreparer] 非法的 tenantId: {}，拒绝设置", tenantId);
            throw new IllegalArgumentException("Invalid tenantId format: " + tenantId);
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format(SET_TENANT_SQL, tenantId));
        }
    }

    /**
     * 清除连接上的租户 ID 设置（归还连接池前调用）。
     */
    public void reset(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET app.current_tenant_id");
        } catch (SQLException e) {
            // RESET 失败不影响连接归还，仅记录警告
            log.debug("[TenantConnectionPreparer] RESET app.current_tenant_id 失败: {}", e.getMessage());
        }
    }
}
