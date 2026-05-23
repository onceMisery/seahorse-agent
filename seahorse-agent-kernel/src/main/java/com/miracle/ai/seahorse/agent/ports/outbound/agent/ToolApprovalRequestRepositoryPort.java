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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;

/**
 * 工具审批请求出站端口，负责保存 Gateway 因策略中断而产生的待审批记录。
 */
public interface ToolApprovalRequestRepositoryPort {

    /**
     * 保存审批请求；调用方只在策略返回 APPROVAL_REQUIRED 时写入 PENDING 请求。
     *
     * @param request 待保存的审批请求
     */
    void save(ApprovalRequest request);

    /**
     * 空实现用于未配置审批存储时保持 Tool Gateway 兼容旧运行路径。
     */
    static ToolApprovalRequestRepositoryPort noop() {
        return request -> {
        };
    }
}
