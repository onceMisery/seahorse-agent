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

import java.util.Optional;

/**
 * 审批决策出站端口。替代实现必须遵守不可变审批记录和乐观状态更新语义。
 */
public interface ApprovalRequestDecisionPort {

    /**
     * 按 fromStatus 乐观更新审批状态，并返回更新后的审批记录。
     */
    Optional<ApprovalRequest> decide(ApprovalRequestDecision decision);
}

