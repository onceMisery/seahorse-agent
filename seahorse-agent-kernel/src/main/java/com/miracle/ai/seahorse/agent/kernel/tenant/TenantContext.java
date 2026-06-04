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
 * 基于 ThreadLocal 的租户上下文，用于在请求链路中传递当前租户 ID。
 * <p>
 * <b>使用规范</b>：
 * <ul>
 *   <li>请求入口（Interceptor/Filter）调用 {@link #set(String)} 设置 tenantId</li>
 *   <li>请求结束必须调用 {@link #clear()} 清理，防止线程池复用导致数据泄漏</li>
 *   <li>异步场景使用 {@link #capture()} / {@link #restore(String)} 手动传播</li>
 *   <li>业务代码使用 {@link #get()} 或 {@link #require()} 获取当前租户</li>
 * </ul>
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
        // 工具类禁止实例化
    }

    /**
     * 设置当前线程的租户 ID。
     *
     * @param tenantId 租户标识，不允许为 null
     * @throws IllegalArgumentException 如果 tenantId 为 null
     */
    public static void set(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不能为 null");
        }
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID。
     *
     * @return 当前租户 ID，如果未设置则返回 {@link TenantConstants#DEFAULT_TENANT_ID}
     */
    public static String get() {
        String tenantId = TENANT_ID.get();
        return tenantId != null ? tenantId : TenantConstants.DEFAULT_TENANT_ID;
    }

    /**
     * 获取当前线程的租户 ID，要求必须已设置（非默认值场景使用）。
     *
     * @return 当前租户 ID
     * @throws IllegalStateException 如果租户 ID 未设置
     */
    public static String require() {
        String tenantId = TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "TenantContext 未设置。请确保 TenantInterceptor 已正确注册，"
                    + "或者在异步场景中使用了 capture/restore 传播租户上下文。");
        }
        return tenantId;
    }

    /**
     * 检查当前线程是否已设置租户 ID。
     */
    public static boolean isSet() {
        return TENANT_ID.get() != null;
    }

    /**
     * 捕获当前线程的租户 ID，用于异步场景的传播。
     * <p>
     * 典型用法：
     * <pre>{@code
     * String captured = TenantContext.capture();
     * executor.submit(() -> {
     *     TenantContext.restore(captured);
     *     try {
     *         // 业务逻辑
     *     } finally {
     *         TenantContext.clear();
     *     }
     * });
     * }</pre>
     *
     * @return 当前租户 ID，可能为 null
     */
    public static String capture() {
        return TENANT_ID.get();
    }

    /**
     * 恢复之前捕获的租户 ID。如果传入 null 则等同于 {@link #clear()}。
     */
    public static void restore(String tenantId) {
        if (tenantId == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
        }
    }

    /**
     * 清除当前线程的租户 ID。<b>必须在请求结束时调用</b>，
     * 否则线程池复用会导致跨租户数据泄漏。
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
