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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

public record SandboxRuntimeNodeAdmissionChange(String auditId,
                                                String nodeId,
                                                boolean draining,
                                                String operatorId,
                                                String tenantId) {

    public SandboxRuntimeNodeAdmissionChange {
        auditId = requireText(auditId, "auditId must not be blank");
        nodeId = requireText(nodeId, "nodeId must not be blank");
        operatorId = requireText(operatorId, "operatorId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
    }

    private static String requireText(String value, String message) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
