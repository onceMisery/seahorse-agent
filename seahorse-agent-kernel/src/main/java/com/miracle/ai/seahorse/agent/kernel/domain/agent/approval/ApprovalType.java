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
 * 审批类型，用于把不同中断原因路由到不同审批队列和展示模板。
 */
public enum ApprovalType {

    /**
     * 工具执行审批，适用于高风险、删除、对外发送等工具调用。
     */
    TOOL_EXECUTION,

    /**
     * 数据访问审批，适用于资源 ACL 或敏感数据访问例外。
     */
    DATA_ACCESS,

    /**
     * 对外发送审批，适用于邮件、Webhook、工单等外发动作。
     */
    EXTERNAL_SEND,

    /**
     * 策略例外审批，适用于临时越权或灰度策略豁免。
     */
    POLICY_EXCEPTION
}
