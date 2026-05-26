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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.connector;

import java.time.Instant;
import java.util.Objects;

public record Connector(String connectorId,
                        String tenantId,
                        ConnectorProvider provider,
                        String name,
                        String description,
                        ConnectorStatus status,
                        String createdBy,
                        Instant createdAt,
                        Instant updatedAt) {

    public Connector {
        connectorId = requireText(connectorId, "connectorId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        provider = Objects.requireNonNull(provider, "provider must not be null");
        name = requireText(name, "name must not be blank");
        description = trimToNull(description);
        status = Objects.requireNonNull(status, "status must not be null");
        createdBy = requireText(createdBy, "createdBy must not be blank");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public Connector withStatus(ConnectorStatus status, Instant updatedAt) {
        return new Connector(
                connectorId,
                tenantId,
                provider,
                name,
                description,
                status,
                createdBy,
                createdAt,
                updatedAt);
    }

    static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
