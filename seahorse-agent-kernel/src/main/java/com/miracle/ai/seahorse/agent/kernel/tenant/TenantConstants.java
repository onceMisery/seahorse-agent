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

package com.miracle.ai.seahorse.agent.kernel.tenant;

/**
 * 多租户隔离的统一常量收口，替代代码中散落的 "default" 硬编码。
 * <p>
 * 所有涉及 tenant_id 默认值的代码都应引用 {@link #DEFAULT_TENANT_ID}，
 * 而非直接写字符串字面量 "default"。
 */
public final class TenantConstants {

    /**
     * 默认租户 ID。单租户模式或系统级资源使用此值。
     */
    public static final String DEFAULT_TENANT_ID = "default";

    /**
     * 系统级资源的租户标识（如内置 Skill、系统模板等）。
     */
    public static final String SYSTEM_TENANT_ID = "system";

    private TenantConstants() {
        // 工具类禁止实例化
    }

    /**
     * 判断给定的 tenantId 是否为有效值（非空且非空白）。
     */
    public static boolean isValid(String tenantId) {
        return tenantId != null && !tenantId.isBlank();
    }

    /**
     * 如果 tenantId 为 null 或空白，返回 {@link #DEFAULT_TENANT_ID}；否则返回原值。
     */
    public static String resolve(String tenantId) {
        return isValid(tenantId) ? tenantId.trim() : DEFAULT_TENANT_ID;
    }
}
