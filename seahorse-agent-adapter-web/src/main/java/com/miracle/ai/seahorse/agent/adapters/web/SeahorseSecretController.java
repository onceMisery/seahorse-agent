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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credential vault management API. Responses never include plaintext secrets.
 */
@RestController
public class SeahorseSecretController {

    private final ObjectProvider<SecretManagementInboundPort> secretManagementPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseSecretController(ObjectProvider<SecretManagementInboundPort> secretManagementPortProvider,
                                    ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(secretManagementPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseSecretController(ObjectProvider<SecretManagementInboundPort> secretManagementPortProvider,
                                    AdvancedFeatureGate advancedFeatureGate) {
        this.secretManagementPortProvider = secretManagementPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/secrets")
    public ApiResponse<Object> create(@RequestBody SecretCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SECRET_MANAGEMENT);
        SecretCreateRequest safeRequest = request == null ? new SecretCreateRequest(null, null, null) : request;
        return ApiResponses.requireService(secretManagementPortProvider, port -> port.create(new SecretCreateCommand(
                safeRequest.tenantId(),
                SecretValue.of(safeRequest.secretValue()),
                safeRequest.metadataJson())));
    }

    public record SecretCreateRequest(String tenantId, String secretValue, String metadataJson) {
    }
}
