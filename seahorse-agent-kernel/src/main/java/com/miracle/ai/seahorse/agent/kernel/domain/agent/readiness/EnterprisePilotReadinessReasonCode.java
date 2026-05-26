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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness;

public enum EnterprisePilotReadinessReasonCode {
    READY,
    OWNER_MISSING,
    FALLBACK_OWNER_MISSING,
    PUBLISHED_VERSION_MISSING,
    VERSION_DISABLED,
    TOOL_RISK_UNREVIEWED,
    HIGH_RISK_TOOL_APPROVAL_MISSING,
    RESOURCE_ACL_MISSING,
    EVAL_MISSING,
    EVAL_STALE,
    EVAL_FAILED,
    QUOTA_MISSING,
    AUDIT_READY,
    AUDIT_MISSING,
    ROLLBACK_TARGET_MISSING,
    DISABLE_SWITCH_MISSING
}
