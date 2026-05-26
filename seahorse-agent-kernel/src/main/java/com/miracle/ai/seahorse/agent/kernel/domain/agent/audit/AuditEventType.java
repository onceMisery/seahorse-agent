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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.audit;

public enum AuditEventType {
    AGENT_PUBLISHED,
    AGENT_PUBLISH_VALIDATED,
    AGENT_ROLLED_BACK,
    RUN_STARTED,
    RUN_FINISHED,
    TOOL_POLICY_DECIDED,
    TOOL_INVOKED,
    APPROVAL_DECIDED,
    CONTEXT_ACCESSED,
    RESOURCE_ACL_CHANGED,
    SECRET_USED,
    CONNECTOR_IMPORTED,
    CONNECTOR_CREDENTIAL_BOUND,
    CONNECTOR_OPERATION_ENABLED,
    CONNECTOR_OPERATION_DISABLED,
    SANDBOX_SESSION_CREATED,
    SANDBOX_EXECUTION_FINISHED,
    AGENT_HANDOFF_CREATED,
    AGENT_HANDOFF_FINISHED
}
