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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.approval;

/**
 * 审批请求状态，Phase 3 会基于这些状态扩展恢复、拒绝和超时处理。
 */
public enum ApprovalRequestStatus {

    /**
     * 已创建，等待人工处理。
     */
    PENDING,

    /**
     * 审批通过，后续运行时可从 checkpoint 恢复执行。
     */
    APPROVED,

    /**
     * 审批拒绝，真实工具不得继续执行。
     */
    REJECTED,

    /**
     * 审批人修改参数后通过，后续恢复必须使用修改后的参数快照。
     */
    MODIFIED,

    /**
     * 审批超时，真实工具不得继续执行。
     */
    EXPIRED
}
