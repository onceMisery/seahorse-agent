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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.policy;

/**
 * Tool Policy 的稳定原因码，供策略、审计、前端展示和自动化告警复用。
 */
public final class ToolPolicyReasonCodes {

    public static final String ALLOW = "ALLOW";
    public static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    public static final String TOOL_DISABLED = "TOOL_DISABLED";
    public static final String TOOL_NOT_BOUND = "TOOL_NOT_BOUND";
    public static final String TOOL_APPROVAL_REQUIRED = "TOOL_APPROVAL_REQUIRED";
    public static final String POLICY_DECISION_MISSING = "POLICY_DECISION_MISSING";

    private ToolPolicyReasonCodes() {
    }
}
