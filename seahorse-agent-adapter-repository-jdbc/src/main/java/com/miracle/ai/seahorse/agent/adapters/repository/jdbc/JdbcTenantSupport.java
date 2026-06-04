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

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;

/**
 * JDBC 层租户支持工具类，为所有 JDBC 适配器提供统一的租户 ID 解析方法。
 * <p>
 * <b>使用方式</b>：在 JDBC 适配器的 SQL 查询中，使用 {@link #resolveTenantId()} 获取
 * 当前租户 ID 作为查询条件参数：
 * <pre>{@code
 * String tenantId = JdbcTenantSupport.resolveTenantId();
 * jdbcTemplate.query("SELECT * FROM t_xxx WHERE tenant_id = ?", ..., tenantId);
 * }</pre>
 */
public final class JdbcTenantSupport {

    private JdbcTenantSupport() {
        // 工具类禁止实例化
    }

    /**
     * 解析当前租户 ID。优先从 {@link TenantContext} 获取，
     * 未设置时返回 {@link TenantConstants#DEFAULT_TENANT_ID}。
     *
     * @return 当前租户 ID，永不为 null
     */
    public static String resolveTenantId() {
        return TenantContext.get();
    }

    /**
     * 解析租户 ID，如果传入值有效则使用传入值，否则从上下文获取。
     * 适用于方法签名中已包含 tenantId 参数的适配器方法。
     *
     * @param explicitTenantId 显式传入的租户 ID，可能为 null
     * @return 有效的租户 ID
     */
    public static String resolveTenantId(String explicitTenantId) {
        if (TenantConstants.isValid(explicitTenantId)) {
            return explicitTenantId.trim();
        }
        return TenantContext.get();
    }
}
