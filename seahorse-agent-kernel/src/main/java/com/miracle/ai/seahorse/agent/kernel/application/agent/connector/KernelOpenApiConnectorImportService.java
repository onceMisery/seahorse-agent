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

package com.miracle.ai.seahorse.agent.kernel.application.agent.connector;

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBindingStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationRisk;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorCredentialBindingCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorImportResult;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationDisableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationEnableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiConnectorInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiImportCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParseRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class KernelOpenApiConnectorImportService implements OpenApiConnectorInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String CONNECTOR_ID_PREFIX = "conn_";
    private static final String CONNECTOR_VERSION_ID_PREFIX = "connv_";
    private static final String OPERATION_ID_PREFIX = "op_";
    private static final String TOOL_ID_PREFIX = "openapi_";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SHORT_HASH_LENGTH = 16;
    private static final String OWNER_TEAM_PREFIX = "connector:";
    private static final String BINDING_ID_PREFIX = "connbind_";
    private static final String AUDIT_ID_PREFIX = "audit_";
    private static final String RESOURCE_TYPE_CONNECTOR = "CONNECTOR";
    private static final String RESOURCE_TYPE_CONNECTOR_OPERATION = "CONNECTOR_OPERATION";
    private static final String ACTIVE_CREDENTIAL_BINDING_REQUIRED = "active credential binding required";
    private static final String APPROVAL_POLICY_REQUIRED = "approval policy required";
    private static final String CONNECTOR_NOT_FOUND = "connector not found";
    private static final String CONNECTOR_OPERATION_NOT_FOUND = "connector operation not found";

    private final ConnectorRepositoryPort connectorRepository;
    private final OpenApiSpecParserPort parser;
    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final ConnectorCredentialBindingRepositoryPort credentialBindingRepository;
    private final KernelAuditLedgerService auditLedger;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;

    public KernelOpenApiConnectorImportService(ConnectorRepositoryPort connectorRepository,
                                               OpenApiSpecParserPort parser,
                                               ToolCatalogRepositoryPort toolCatalogRepository,
                                               CurrentUserPort currentUserPort,
                                               Clock clock) {
        this(connectorRepository,
                parser,
                toolCatalogRepository,
                ConnectorCredentialBindingRepositoryPort.empty(),
                null,
                currentUserPort,
                clock);
    }

    public KernelOpenApiConnectorImportService(ConnectorRepositoryPort connectorRepository,
                                               OpenApiSpecParserPort parser,
                                               ToolCatalogRepositoryPort toolCatalogRepository,
                                               ConnectorCredentialBindingRepositoryPort credentialBindingRepository,
                                               KernelAuditLedgerService auditLedger,
                                               CurrentUserPort currentUserPort,
                                               Clock clock) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository,
                "connectorRepository must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.toolCatalogRepository = Objects.requireNonNull(toolCatalogRepository,
                "toolCatalogRepository must not be null");
        this.credentialBindingRepository = Objects.requireNonNullElseGet(credentialBindingRepository,
                ConnectorCredentialBindingRepositoryPort::empty);
        this.auditLedger = auditLedger;
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ConnectorImportResult importSpec(OpenApiImportCommand command) {
        requireAdmin();
        OpenApiImportCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String tenantId = requireText(safeCommand.tenantId(), "tenantId must not be blank");
        String name = requireText(safeCommand.name(), "name must not be blank");
        String specJson = requireText(safeCommand.specJson(), "specJson must not be blank");
        CurrentUser user = currentUserPort.requireCurrentUser();
        String importedBy = textOrDefault(safeCommand.importedBy(), user.userId());
        Instant now = clock.instant();
        OpenApiSpecDocument document = parser.parse(new OpenApiSpecParseRequest(specJson));
        String connectorId = connectorId(tenantId, name);
        String specHash = hash(specJson);

        Connector connector = connectorRepository.findConnectorById(connectorId)
                .orElseGet(() -> new Connector(
                        connectorId,
                        tenantId,
                        ConnectorProvider.OPENAPI,
                        name,
                        document.description(),
                        ConnectorStatus.IMPORTED,
                        importedBy,
                        now,
                        now));
        connectorRepository.saveConnector(connector.withStatus(ConnectorStatus.IMPORTED, now));

        ConnectorVersion version = connectorRepository.findVersionByConnectorIdAndSpecHash(connectorId, specHash)
                .orElse(null);
        if (version == null) {
            version = connectorRepository.saveVersion(new ConnectorVersion(
                    connectorVersionId(connectorId, specHash),
                    connectorId,
                    specHash,
                    specJson,
                    importedBy,
                    now));
            for (OpenApiSpecOperation specOperation : document.operations()) {
                connectorRepository.saveOperation(toOperation(connectorId, version.connectorVersionId(),
                        specOperation, now));
            }
        }
        ConnectorImportResult result = result(connectorId, version.connectorVersionId());
        appendAudit(
                AuditEventType.CONNECTOR_IMPORTED,
                tenantId,
                user.userId(),
                RESOURCE_TYPE_CONNECTOR,
                connectorId,
                """
                        {"connectorId":"%s","connectorVersionId":"%s","operationCount":%d,"importedBy":"%s"}
                        """.formatted(connectorId, version.connectorVersionId(), result.operationCount(), importedBy));
        return result;
    }

    @Override
    public ConnectorPage page(ConnectorQuery query) {
        requireAdmin();
        return connectorRepository.page(query == null
                ? new ConnectorQuery(null, null, null,
                ConnectorQuery.DEFAULT_CURRENT, ConnectorQuery.DEFAULT_PAGE_SIZE)
                : query);
    }

    @Override
    public List<ConnectorOperation> listOperations(String connectorId) {
        requireAdmin();
        return connectorRepository.listOperations(requireText(connectorId, "connectorId must not be blank"));
    }

    @Override
    public ConnectorCredentialBinding bindCredential(ConnectorCredentialBindingCommand command) {
        requireAdmin();
        ConnectorCredentialBindingCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String connectorId = requireText(safeCommand.connectorId(), "connectorId must not be blank");
        String operationId = requireText(safeCommand.operationId(), "operationId must not be blank");
        CredentialAuthType authType = Objects.requireNonNull(safeCommand.authType(), "authType must not be null");
        String credentialRef = requireText(safeCommand.credentialRef(), "credentialRef must not be blank");
        CurrentUser user = currentUserPort.requireCurrentUser();
        String boundBy = textOrDefault(safeCommand.boundBy(), user.userId());
        Connector connector = connectorRepository.findConnectorById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_NOT_FOUND));
        connectorRepository.findOperation(connectorId, operationId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_OPERATION_NOT_FOUND));
        Instant now = clock.instant();
        credentialBindingRepository.findActive(connector.tenantId(), connectorId, operationId, authType)
                .ifPresent(binding -> credentialBindingRepository.save(binding.rotate(now)));
        ConnectorCredentialBinding binding = credentialBindingRepository.save(new ConnectorCredentialBinding(
                bindingId(connector.tenantId(), connectorId, operationId, authType, credentialRef, now),
                connector.tenantId(),
                connectorId,
                operationId,
                authType,
                credentialRef,
                ConnectorCredentialBindingStatus.ACTIVE,
                boundBy,
                now,
                null));
        appendAudit(
                AuditEventType.CONNECTOR_CREDENTIAL_BOUND,
                connector.tenantId(),
                user.userId(),
                RESOURCE_TYPE_CONNECTOR_OPERATION,
                operationId,
                """
                        {"connectorId":"%s","operationId":"%s","authType":"%s","credentialRef":"%s","boundBy":"%s"}
                        """.formatted(connectorId, operationId, authType.name(), credentialRef, boundBy));
        return binding;
    }

    @Override
    public List<ConnectorCredentialBinding> listActiveCredentialBindings(String connectorId, String operationId) {
        requireAdmin();
        String safeConnectorId = requireText(connectorId, "connectorId must not be blank");
        String safeOperationId = requireText(operationId, "operationId must not be blank");
        Connector connector = connectorRepository.findConnectorById(safeConnectorId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_NOT_FOUND));
        connectorRepository.findOperation(safeConnectorId, safeOperationId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_OPERATION_NOT_FOUND));
        return credentialBindingRepository.findActiveByOperation(
                connector.tenantId(),
                safeConnectorId,
                safeOperationId);
    }

    @Override
    public ConnectorOperation enableOperation(ConnectorOperationEnableCommand command) {
        requireAdmin();
        ConnectorOperationEnableCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String connectorId = requireText(safeCommand.connectorId(), "connectorId must not be blank");
        String operationId = requireText(safeCommand.operationId(), "operationId must not be blank");
        CurrentUser user = currentUserPort.requireCurrentUser();
        Connector connector = connectorRepository.findConnectorById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_NOT_FOUND));
        ConnectorOperation operation = connectorRepository.findOperation(connectorId, operationId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_OPERATION_NOT_FOUND));
        requireActiveCredentialBinding(connector, operation);
        requireApprovalPolicy(safeCommand, operation);
        ConnectorOperation enabled = connectorRepository.saveOperation(operation.enable(clock.instant()));
        toolCatalogRepository.save(toToolCatalogEntry(enabled));
        appendAudit(
                AuditEventType.CONNECTOR_OPERATION_ENABLED,
                connector.tenantId(),
                user.userId(),
                RESOURCE_TYPE_CONNECTOR_OPERATION,
                operationId,
                """
                        {"connectorId":"%s","operationId":"%s","toolId":"%s","riskLevel":"%s","approvalPolicyId":"%s"}
                        """.formatted(connectorId, operationId, enabled.toolId(), enabled.riskLevel().name(),
                        textOrDefault(safeCommand.approvalPolicyId(), "")));
        return enabled;
    }

    @Override
    public ConnectorOperation disableOperation(ConnectorOperationDisableCommand command) {
        requireAdmin();
        ConnectorOperationDisableCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String connectorId = requireText(safeCommand.connectorId(), "connectorId must not be blank");
        String operationId = requireText(safeCommand.operationId(), "operationId must not be blank");
        CurrentUser user = currentUserPort.requireCurrentUser();
        Connector connector = connectorRepository.findConnectorById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_NOT_FOUND));
        ConnectorOperation operation = connectorRepository.findOperation(connectorId, operationId)
                .orElseThrow(() -> new IllegalArgumentException(CONNECTOR_OPERATION_NOT_FOUND));
        ConnectorOperation disabled = connectorRepository.saveOperation(operation.disable(clock.instant()));
        toolCatalogRepository.setEnabled(disabled.toolId(), false);
        appendAudit(
                AuditEventType.CONNECTOR_OPERATION_DISABLED,
                connector.tenantId(),
                user.userId(),
                RESOURCE_TYPE_CONNECTOR_OPERATION,
                operationId,
                """
                        {"connectorId":"%s","operationId":"%s","toolId":"%s","reasonCode":"%s"}
                        """.formatted(connectorId, operationId, disabled.toolId(),
                        textOrDefault(safeCommand.reasonCode(), "")));
        return disabled;
    }

    private ConnectorOperation toOperation(String connectorId,
                                           String connectorVersionId,
                                           OpenApiSpecOperation specOperation,
                                           Instant now) {
        ConnectorOperationRisk risk = ConnectorOperationRiskMapper.map(specOperation.method());
        String operationKey = operationKey(specOperation);
        String operationId = operationId(connectorVersionId, operationKey);
        return new ConnectorOperation(
                operationId,
                connectorId,
                connectorVersionId,
                operationKey,
                specOperation.operationId(),
                specOperation.method(),
                specOperation.path(),
                specOperation.summary(),
                specOperation.description(),
                specOperation.schemaJson(),
                specOperation.outputSchemaJson(),
                toolId(connectorId, operationKey),
                risk.riskLevel(),
                risk.actionType(),
                specOperation.resourceType(),
                ConnectorOperationStatus.DISABLED,
                risk.requiresApproval(),
                now,
                now);
    }

    private ToolCatalogEntry toToolCatalogEntry(ConnectorOperation operation) {
        String name = textOrDefault(operation.summary(), operation.operationKey());
        return new ToolCatalogEntry(
                operation.toolId(),
                ToolProvider.OPENAPI,
                name,
                operation.description(),
                operation.schemaJson(),
                operation.outputSchemaJson(),
                operation.riskLevel(),
                operation.actionType(),
                operation.resourceType(),
                OWNER_TEAM_PREFIX + operation.connectorId(),
                true,
                operation.requiresApproval(),
                operation.createdAt(),
                operation.updatedAt());
    }

    private ConnectorImportResult result(String connectorId, String connectorVersionId) {
        List<ConnectorOperation> operations = connectorRepository.listOperations(connectorId).stream()
                .filter(operation -> operation.connectorVersionId().equals(connectorVersionId))
                .toList();
        int disabled = (int) operations.stream()
                .filter(operation -> operation.status() == ConnectorOperationStatus.DISABLED)
                .count();
        int highRisk = (int) operations.stream()
                .filter(ConnectorOperation::requiresApproval)
                .count();
        return new ConnectorImportResult(
                connectorId,
                connectorVersionId,
                ConnectorStatus.IMPORTED,
                operations.size(),
                disabled,
                highRisk);
    }

    private void requireAdmin() {
        currentUserPort.requireRole(ADMIN_ROLE);
    }

    private void requireActiveCredentialBinding(Connector connector, ConnectorOperation operation) {
        if (!operation.requiresCredentialBinding()) {
            return;
        }
        boolean hasActiveBinding = credentialBindingRepository.findActive(
                        connector.tenantId(),
                        operation.connectorId(),
                        operation.operationId(),
                        operation.authType())
                .isPresent();
        if (!hasActiveBinding) {
            throw new IllegalStateException(ACTIVE_CREDENTIAL_BINDING_REQUIRED);
        }
    }

    private void requireApprovalPolicy(ConnectorOperationEnableCommand command, ConnectorOperation operation) {
        if (!operation.requiresApproval()) {
            return;
        }
        if (trimToNull(command.approvalPolicyId()) == null && !command.operatorConfirmedRisk()) {
            throw new IllegalStateException(APPROVAL_POLICY_REQUIRED);
        }
    }

    private String operationKey(OpenApiSpecOperation operation) {
        String explicit = trimToNull(operation.operationId());
        if (explicit != null) {
            return explicit;
        }
        return operation.method().name() + " " + normalizePath(operation.path());
    }

    private String normalizePath(String path) {
        String trimmed = requireText(path, "path must not be blank");
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String connectorId(String tenantId, String name) {
        return CONNECTOR_ID_PREFIX + shortHash(tenantId + ":" + name.toLowerCase(Locale.ROOT));
    }

    private String connectorVersionId(String connectorId, String specHash) {
        return CONNECTOR_VERSION_ID_PREFIX + shortHash(connectorId + ":" + specHash);
    }

    private String operationId(String connectorVersionId, String operationKey) {
        return OPERATION_ID_PREFIX + shortHash(connectorVersionId + ":" + operationKey);
    }

    private String toolId(String connectorId, String operationKey) {
        return TOOL_ID_PREFIX + shortHash(connectorId + ":" + operationKey);
    }

    private String bindingId(String tenantId,
                             String connectorId,
                             String operationId,
                             CredentialAuthType authType,
                             String credentialRef,
                             Instant now) {
        return BINDING_ID_PREFIX + shortHash(tenantId + ":" + connectorId + ":" + operationId + ":"
                + authType.name() + ":" + credentialRef + ":" + now);
    }

    private void appendAudit(AuditEventType eventType,
                             String tenantId,
                             String actorId,
                             String resourceType,
                             String resourceId,
                             String payload) {
        if (auditLedger == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                AUDIT_ID_PREFIX + shortHash(eventType.name() + ":" + resourceId + ":" + now),
                tenantId,
                eventType,
                AuditActorType.USER,
                actorId,
                null,
                null,
                resourceType,
                resourceId,
                payload,
                now));
    }

    private String shortHash(String value) {
        return hash(value).substring(0, SHORT_HASH_LENGTH);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(HASH_ALGORITHM + " is not available", ex);
        }
    }

    private String textOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
