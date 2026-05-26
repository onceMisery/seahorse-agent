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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AgentPublishCheckReport(String checkId,
                                      String agentId,
                                      String versionId,
                                      AgentPublishCheckStatus status,
                                      List<AgentPublishCheckItem> items,
                                      Instant checkedAt) {

    public AgentPublishCheckReport {
        checkId = requireText(checkId, "checkId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = trimToNull(versionId);
        items = items == null ? List.of() : List.copyOf(items);
        status = status == null ? aggregate(items) : status;
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    public Optional<AgentPublishCheckItem> item(AgentPublishCheckCode code) {
        return items.stream()
                .filter(item -> item.code() == code)
                .findFirst();
    }

    private static AgentPublishCheckStatus aggregate(List<AgentPublishCheckItem> items) {
        if (items.stream().anyMatch(item -> item.status() == AgentPublishCheckStatus.FAIL)) {
            return AgentPublishCheckStatus.FAIL;
        }
        if (items.stream().anyMatch(item -> item.status() == AgentPublishCheckStatus.WARN)) {
            return AgentPublishCheckStatus.WARN;
        }
        return AgentPublishCheckStatus.PASS;
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
