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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBindingStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcConnectorCredentialBindingRepositoryAdapter implements ConnectorCredentialBindingRepositoryPort {

    private static final String COLUMNS = """
            binding_id, tenant_id, connector_id, operation_id, auth_type, credential_ref,
            status, bound_by, bound_at, rotated_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_connector_credential_binding
            (binding_id, tenant_id, connector_id, operation_id, auth_type, credential_ref,
             status, bound_by, bound_at, rotated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_connector_credential_binding
            SET tenant_id = ?,
                connector_id = ?,
                operation_id = ?,
                auth_type = ?,
                credential_ref = ?,
                status = ?,
                bound_by = ?,
                bound_at = ?,
                rotated_at = ?
            WHERE binding_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_connector_credential_binding
            WHERE binding_id = ?
            """.formatted(COLUMNS);
    private static final String SQL_FIND_ACTIVE = """
            SELECT %s
            FROM sa_connector_credential_binding
            WHERE tenant_id = ?
              AND connector_id = ?
              AND operation_id = ?
              AND auth_type = ?
              AND status = ?
            ORDER BY bound_at DESC, binding_id DESC
            """.formatted(COLUMNS);
    private static final String SQL_FIND_ACTIVE_BY_OPERATION = """
            SELECT %s
            FROM sa_connector_credential_binding
            WHERE tenant_id = ?
              AND connector_id = ?
              AND operation_id = ?
              AND status = ?
            ORDER BY auth_type ASC, bound_at DESC, binding_id DESC
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcConnectorCredentialBindingRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public ConnectorCredentialBinding save(ConnectorCredentialBinding binding) {
        ConnectorCredentialBinding safeBinding = Objects.requireNonNull(binding, "binding must not be null");
        if (findById(safeBinding.bindingId()).isPresent()) {
            update(safeBinding);
            return safeBinding;
        }
        insert(safeBinding);
        return safeBinding;
    }

    @Override
    public Optional<ConnectorCredentialBinding> findActive(String tenantId,
                                                           String connectorId,
                                                           String operationId,
                                                           CredentialAuthType authType) {
        if (!hasText(tenantId) || !hasText(connectorId) || !hasText(operationId) || authType == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_ACTIVE,
                        this::mapBinding,
                        tenantId.trim(),
                        connectorId.trim(),
                        operationId.trim(),
                        authType.name(),
                        ConnectorCredentialBindingStatus.ACTIVE.name())
                .stream()
                .findFirst();
    }

    @Override
    public List<ConnectorCredentialBinding> findActiveByOperation(String tenantId,
                                                                  String connectorId,
                                                                  String operationId) {
        if (!hasText(tenantId) || !hasText(connectorId) || !hasText(operationId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_FIND_ACTIVE_BY_OPERATION,
                this::mapBinding,
                tenantId.trim(),
                connectorId.trim(),
                operationId.trim(),
                ConnectorCredentialBindingStatus.ACTIVE.name());
    }

    private Optional<ConnectorCredentialBinding> findById(String bindingId) {
        if (!hasText(bindingId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapBinding, bindingId.trim()).stream().findFirst();
    }

    private void insert(ConnectorCredentialBinding binding) {
        jdbcTemplate.update(SQL_INSERT,
                binding.bindingId(),
                binding.tenantId(),
                binding.connectorId(),
                binding.operationId(),
                binding.authType().name(),
                binding.credentialRef(),
                binding.status().name(),
                binding.boundBy(),
                toTimestamp(binding.boundAt()),
                toTimestamp(binding.rotatedAt()));
    }

    private void update(ConnectorCredentialBinding binding) {
        jdbcTemplate.update(SQL_UPDATE,
                binding.tenantId(),
                binding.connectorId(),
                binding.operationId(),
                binding.authType().name(),
                binding.credentialRef(),
                binding.status().name(),
                binding.boundBy(),
                toTimestamp(binding.boundAt()),
                toTimestamp(binding.rotatedAt()),
                binding.bindingId());
    }

    private ConnectorCredentialBinding mapBinding(ResultSet resultSet, int rowNum) throws SQLException {
        return new ConnectorCredentialBinding(
                resultSet.getString("binding_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("connector_id"),
                resultSet.getString("operation_id"),
                CredentialAuthType.valueOf(resultSet.getString("auth_type")),
                resultSet.getString("credential_ref"),
                ConnectorCredentialBindingStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("bound_by"),
                toInstant(resultSet.getTimestamp("bound_at")),
                toInstant(resultSet.getTimestamp("rotated_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
