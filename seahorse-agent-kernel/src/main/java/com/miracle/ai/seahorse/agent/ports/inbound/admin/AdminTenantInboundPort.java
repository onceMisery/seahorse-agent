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

package com.miracle.ai.seahorse.agent.ports.inbound.admin;

import com.miracle.ai.seahorse.agent.ports.outbound.admin.TenantDetail;

import java.util.List;
import java.util.Map;

/**
 * 租户管理入站用例端口。
 *
 * <p>Web 适配器依赖该端口而不是具体 {@code KernelAdminTenantService} 实现，
 * 保持「Web 依赖入站用例契约，而非 Kernel 服务实现」的依赖方向。</p>
 */
public interface AdminTenantInboundPort {

    List<TenantDetail> listTenants(int page, int size, String statusFilter);

    long countTenants(String statusFilter);

    TenantDetail getTenantDetail(String tenantId);

    void suspendTenant(String tenantId, String operator);

    void deleteTenant(String tenantId, String operator);

    List<Map<String, Object>> listUsersByTenant(String tenantId, int page, int size);

    long countUsersByTenant(String tenantId);
}
