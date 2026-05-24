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

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWriteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC secret store adapter. It owns persistence and encryption mechanics only.
 */
public class JdbcSecretStoreAdapter implements SecretStorePort, SecretWritePort {

    private static final String SECRET_COLUMNS = """
            secret_ref, tenant_id, encrypted_value, metadata_json, created_at, rotated_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_secret_ref
            (secret_ref, tenant_id, encrypted_value, metadata_json, created_at, rotated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_REF = """
            SELECT %s
            FROM sa_secret_ref
            WHERE secret_ref = ?
            """.formatted(SECRET_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final SecretValueCipher cipher;

    public JdbcSecretStoreAdapter(DataSource dataSource, SecretValueCipher cipher) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
    }

    @Override
    public SecretMetadata putSecret(SecretWriteCommand command) {
        SecretWriteCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeCommand.secretRef(),
                safeCommand.tenantId(),
                cipher.encrypt(safeCommand.secretValue().reveal()),
                safeCommand.metadataJson(),
                toTimestamp(safeCommand.createdAt()),
                null);
        return findMetadataByRef(safeCommand.secretRef())
                .orElseThrow(() -> new IllegalStateException("stored secret metadata not found"));
    }

    @Override
    public Optional<SecretValue> getSecret(String secretRef) {
        if (!hasText(secretRef)) {
            return Optional.empty();
        }
        List<String> encryptedValues = jdbcTemplate.query(SQL_FIND_BY_REF,
                (resultSet, rowNum) -> resultSet.getString("encrypted_value"),
                secretRef.trim());
        return encryptedValues.stream()
                .findFirst()
                .map(cipher::decrypt)
                .map(SecretValue::of);
    }

    private Optional<SecretMetadata> findMetadataByRef(String secretRef) {
        if (!hasText(secretRef)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_REF, this::mapMetadata, secretRef.trim()).stream().findFirst();
    }

    private SecretMetadata mapMetadata(ResultSet resultSet, int rowNum) throws SQLException {
        return new SecretMetadata(
                resultSet.getString("secret_ref"),
                resultSet.getString("tenant_id"),
                resultSet.getString("metadata_json"),
                toInstant(resultSet.getTimestamp("created_at")),
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
