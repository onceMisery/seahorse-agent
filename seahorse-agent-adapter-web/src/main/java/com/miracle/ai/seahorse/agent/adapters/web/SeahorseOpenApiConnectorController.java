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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorCredentialBindingCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationDisableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationEnableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiConnectorInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiImportCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseOpenApiConnectorController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<OpenApiConnectorInboundPort> connectorPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseOpenApiConnectorController(ObjectProvider<OpenApiConnectorInboundPort> connectorPortProvider,
                                              ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(connectorPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseOpenApiConnectorController(ObjectProvider<OpenApiConnectorInboundPort> connectorPortProvider,
                                              AdvancedFeatureGate advancedFeatureGate) {
        this.connectorPortProvider = connectorPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/connectors/openapi")
    public ApiResponse<Object> importOpenApi(@RequestBody OpenApiImportRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        OpenApiImportRequest safeRequest = request == null
                ? new OpenApiImportRequest(null, null, null, null)
                : request;
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.importSpec(new OpenApiImportCommand(
                        safeRequest.tenantId(),
                        safeRequest.name(),
                        safeRequest.specJson(),
                        safeRequest.importedBy())));
    }

    @GetMapping("/api/connectors")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) ConnectorStatus status,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.page(new ConnectorQuery(tenantId, keyword, status, current, size)));
    }

    @GetMapping("/api/connectors/{connectorId}/operations")
    public ApiResponse<Object> listOperations(@PathVariable String connectorId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        return ApiResponses.requireService(connectorPortProvider, port -> port.listOperations(connectorId));
    }

    @PutMapping("/api/connectors/{connectorId}/operations/{operationId}/credential-binding")
    public ApiResponse<Object> bindCredential(@PathVariable String connectorId,
                                              @PathVariable String operationId,
                                              @RequestBody ConnectorCredentialBindingRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        ConnectorCredentialBindingRequest safeRequest = request == null
                ? new ConnectorCredentialBindingRequest(null, null, null)
                : request;
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.bindCredential(new ConnectorCredentialBindingCommand(
                        connectorId,
                        operationId,
                        safeRequest.authType(),
                        safeRequest.credentialRef(),
                        safeRequest.boundBy())));
    }

    @GetMapping("/api/connectors/{connectorId}/operations/{operationId}/credential-binding")
    public ApiResponse<Object> listActiveCredentialBindings(@PathVariable String connectorId,
                                                            @PathVariable String operationId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.listActiveCredentialBindings(connectorId, operationId));
    }

    @PostMapping("/api/connectors/{connectorId}/operations/{operationId}/enable")
    public ApiResponse<Object> enableOperation(@PathVariable String connectorId,
                                               @PathVariable String operationId,
                                               @RequestBody(required = false) ConnectorOperationEnableRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        ConnectorOperationEnableRequest safeRequest = request == null
                ? new ConnectorOperationEnableRequest(null, false)
                : request;
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.enableOperation(new ConnectorOperationEnableCommand(
                        connectorId,
                        operationId,
                        safeRequest.approvalPolicyId(),
                        safeRequest.operatorConfirmedRisk())));
    }

    @PostMapping("/api/connectors/{connectorId}/operations/{operationId}/disable")
    public ApiResponse<Object> disableOperation(@PathVariable String connectorId,
                                                @PathVariable String operationId,
                                                @RequestBody(required = false) ConnectorOperationDisableRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.CONNECTOR_MANAGEMENT);
        ConnectorOperationDisableRequest safeRequest = request == null
                ? new ConnectorOperationDisableRequest(null)
                : request;
        return ApiResponses.requireService(connectorPortProvider,
                port -> port.disableOperation(new ConnectorOperationDisableCommand(
                        connectorId,
                        operationId,
                        safeRequest.reasonCode())));
    }

    public record OpenApiImportRequest(String tenantId,
                                       String name,
                                       String specJson,
                                       String importedBy) {
    }

    public record ConnectorCredentialBindingRequest(CredentialAuthType authType,
                                                    String credentialRef,
                                                    String boundBy) {
    }

    public record ConnectorOperationEnableRequest(String approvalPolicyId,
                                                  boolean operatorConfirmedRisk) {
    }

    public record ConnectorOperationDisableRequest(String reasonCode) {
    }
}
