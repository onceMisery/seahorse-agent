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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;

import java.util.List;
import java.util.Optional;

public interface ConnectorCredentialBindingRepositoryPort {

    ConnectorCredentialBinding save(ConnectorCredentialBinding binding);

    Optional<ConnectorCredentialBinding> findActive(String tenantId,
                                                    String connectorId,
                                                    String operationId,
                                                    CredentialAuthType authType);

    List<ConnectorCredentialBinding> findActiveByOperation(String tenantId,
                                                           String connectorId,
                                                           String operationId);

    static ConnectorCredentialBindingRepositoryPort empty() {
        return new ConnectorCredentialBindingRepositoryPort() {
            @Override
            public ConnectorCredentialBinding save(ConnectorCredentialBinding binding) {
                return binding;
            }

            @Override
            public Optional<ConnectorCredentialBinding> findActive(String tenantId,
                                                                   String connectorId,
                                                                   String operationId,
                                                                   CredentialAuthType authType) {
                return Optional.empty();
            }

            @Override
            public List<ConnectorCredentialBinding> findActiveByOperation(String tenantId,
                                                                          String connectorId,
                                                                          String operationId) {
                return List.of();
            }
        };
    }
}
