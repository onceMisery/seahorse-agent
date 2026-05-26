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

import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;

import java.time.Instant;
import java.util.Objects;

public record ConnectorCredentialBinding(String bindingId,
                                         String tenantId,
                                         String connectorId,
                                         String operationId,
                                         CredentialAuthType authType,
                                         String credentialRef,
                                         ConnectorCredentialBindingStatus status,
                                         String boundBy,
                                         Instant boundAt,
                                         Instant rotatedAt) {

    public ConnectorCredentialBinding {
        bindingId = Connector.requireText(bindingId, "bindingId must not be blank");
        tenantId = Connector.requireText(tenantId, "tenantId must not be blank");
        connectorId = Connector.requireText(connectorId, "connectorId must not be blank");
        operationId = Connector.requireText(operationId, "operationId must not be blank");
        authType = Objects.requireNonNull(authType, "authType must not be null");
        if (authType == CredentialAuthType.NONE) {
            throw new IllegalArgumentException("authType must require credentials");
        }
        credentialRef = Connector.requireText(credentialRef, "credentialRef must not be blank");
        status = Objects.requireNonNull(status, "status must not be null");
        boundBy = Connector.requireText(boundBy, "boundBy must not be blank");
        boundAt = Objects.requireNonNull(boundAt, "boundAt must not be null");
    }

    public ConnectorCredentialBinding rotate(Instant rotatedAt) {
        if (status == ConnectorCredentialBindingStatus.ROTATED) {
            return this;
        }
        return new ConnectorCredentialBinding(
                bindingId,
                tenantId,
                connectorId,
                operationId,
                authType,
                credentialRef,
                ConnectorCredentialBindingStatus.ROTATED,
                boundBy,
                boundAt,
                Objects.requireNonNull(rotatedAt, "rotatedAt must not be null"));
    }
}
