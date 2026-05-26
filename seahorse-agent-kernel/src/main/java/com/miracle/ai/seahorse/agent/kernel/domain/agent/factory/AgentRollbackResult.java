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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.factory;

import java.time.Instant;
import java.util.Objects;

public record AgentRollbackResult(String rollbackId,
                                  String agentId,
                                  String previousVersionId,
                                  String targetVersionId,
                                  AgentRollbackStatus status,
                                  AgentRollbackReasonCode reasonCode,
                                  Instant rolledBackAt) {

    public AgentRollbackResult {
        rollbackId = requireText(rollbackId, "rollbackId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        targetVersionId = requireText(targetVersionId, "targetVersionId must not be blank");
        status = Objects.requireNonNull(status, "status must not be null");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        rolledBackAt = Objects.requireNonNull(rolledBackAt, "rolledBackAt must not be null");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
