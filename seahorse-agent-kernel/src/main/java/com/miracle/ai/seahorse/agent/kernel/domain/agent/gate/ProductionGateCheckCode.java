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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.gate;

public enum ProductionGateCheckCode {
    OWNER_PRESENT,
    PUBLISHED_VERSION_PRESENT,
    TOOL_RISK_REVIEWED,
    HIGH_RISK_APPROVAL_PRESENT,
    AUDIT_LEDGER_ENABLED,
    RESOURCE_ACL_PRESENT,
    EVAL_PASSING,
    QUOTA_CONFIGURED,
    SRE_HEALTH_GREEN
}
