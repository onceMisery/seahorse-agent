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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;

import java.time.Instant;
import java.util.Objects;

public record ConnectorOperation(String operationId,
                                 String connectorId,
                                 String connectorVersionId,
                                 String operationKey,
                                 String originalOperationId,
                                 OpenApiHttpMethod method,
                                 String path,
                                 String summary,
                                 String description,
                                 String schemaJson,
                                 String outputSchemaJson,
                                 String toolId,
                                 ToolRiskLevel riskLevel,
                                 ToolActionType actionType,
                                 String resourceType,
                                 CredentialAuthType authType,
                                 ConnectorOperationStatus status,
                                 boolean requiresApproval,
                                 Instant createdAt,
                                 Instant updatedAt) {

    public static final String EMPTY_JSON_OBJECT = "{}";

    public ConnectorOperation {
        operationId = Connector.requireText(operationId, "operationId must not be blank");
        connectorId = Connector.requireText(connectorId, "connectorId must not be blank");
        connectorVersionId = Connector.requireText(connectorVersionId, "connectorVersionId must not be blank");
        operationKey = Connector.requireText(operationKey, "operationKey must not be blank");
        originalOperationId = Connector.trimToNull(originalOperationId);
        method = Objects.requireNonNull(method, "method must not be null");
        path = normalizePath(path);
        summary = Connector.trimToNull(summary);
        description = Connector.trimToNull(description);
        schemaJson = defaultJson(schemaJson);
        outputSchemaJson = Connector.trimToNull(outputSchemaJson);
        toolId = Connector.requireText(toolId, "toolId must not be blank");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        resourceType = Connector.trimToNull(resourceType);
        authType = Objects.requireNonNullElse(authType, CredentialAuthType.NONE);
        status = Objects.requireNonNull(status, "status must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public ConnectorOperation(String operationId,
                              String connectorId,
                              String connectorVersionId,
                              String operationKey,
                              String originalOperationId,
                              OpenApiHttpMethod method,
                              String path,
                              String summary,
                              String description,
                              String schemaJson,
                              String outputSchemaJson,
                              String toolId,
                              ToolRiskLevel riskLevel,
                              ToolActionType actionType,
                              String resourceType,
                              ConnectorOperationStatus status,
                              boolean requiresApproval,
                              Instant createdAt,
                              Instant updatedAt) {
        this(operationId,
                connectorId,
                connectorVersionId,
                operationKey,
                originalOperationId,
                method,
                path,
                summary,
                description,
                schemaJson,
                outputSchemaJson,
                toolId,
                riskLevel,
                actionType,
                resourceType,
                CredentialAuthType.NONE,
                status,
                requiresApproval,
                createdAt,
                updatedAt);
    }

    public boolean requiresCredentialBinding() {
        return authType.isSecretBacked();
    }

    public ConnectorOperation enable(Instant updatedAt) {
        if (status == ConnectorOperationStatus.ENABLED) {
            return this;
        }
        return new ConnectorOperation(
                operationId,
                connectorId,
                connectorVersionId,
                operationKey,
                originalOperationId,
                method,
                path,
                summary,
                description,
                schemaJson,
                outputSchemaJson,
                toolId,
                riskLevel,
                actionType,
                resourceType,
                authType,
                ConnectorOperationStatus.ENABLED,
                requiresApproval,
                createdAt,
                Objects.requireNonNull(updatedAt, "updatedAt must not be null"));
    }

    public ConnectorOperation disable(Instant updatedAt) {
        if (status == ConnectorOperationStatus.DISABLED) {
            return this;
        }
        return new ConnectorOperation(
                operationId,
                connectorId,
                connectorVersionId,
                operationKey,
                originalOperationId,
                method,
                path,
                summary,
                description,
                schemaJson,
                outputSchemaJson,
                toolId,
                riskLevel,
                actionType,
                resourceType,
                authType,
                ConnectorOperationStatus.DISABLED,
                requiresApproval,
                createdAt,
                Objects.requireNonNull(updatedAt, "updatedAt must not be null"));
    }

    private static String normalizePath(String path) {
        String trimmed = Connector.requireText(path, "path must not be blank");
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String defaultJson(String value) {
        String trimmed = Connector.trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
    }
}
