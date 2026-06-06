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

import com.miracle.ai.seahorse.agent.kernel.model.AiModelConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.config.AiModelConfigRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 模型配置 JDBC 仓储适配器
 */
public class JdbcAiModelConfigRepositoryAdapter implements AiModelConfigRepositoryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAiModelConfigRepositoryAdapter.class);

    private static final String ENCRYPTION_KEY = "SeahorseAgent16B"; // 16字节密钥
    private static final String ALGORITHM = "AES";

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2Database;

    public JdbcAiModelConfigRepositoryAdapter(DataSource dataSource) {
        DataSource safeDataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcTemplate = new JdbcTemplate(safeDataSource);
        this.h2Database = isH2Database(safeDataSource);
    }

    @Override
    public List<AiModelConfig> findAll() {
        return findAll(DEFAULT_TENANT_ID);
    }

    @Override
    public List<AiModelConfig> findAll(String tenantId) {
        String sql = """
                SELECT id, tenant_id, config_key, config_value, config_type, is_encrypted, description,
                       created_by, updated_by, created_at, updated_at, deleted
                FROM sa_ai_model_config
                WHERE tenant_id = ? AND deleted = 0
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new AiModelConfigRowMapper(), normalizeTenantId(tenantId));
    }

    @Override
    public Optional<AiModelConfig> findByKey(String configKey) {
        return findByKey(DEFAULT_TENANT_ID, configKey);
    }

    @Override
    public Optional<AiModelConfig> findByKey(String tenantId, String configKey) {
        String sql = """
                SELECT id, tenant_id, config_key, config_value, config_type, is_encrypted, description,
                       created_by, updated_by, created_at, updated_at, deleted
                FROM sa_ai_model_config
                WHERE tenant_id = ? AND config_key = ? AND deleted = 0
                """;
        List<AiModelConfig> results = jdbcTemplate.query(sql, new AiModelConfigRowMapper(),
                normalizeTenantId(tenantId), configKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void save(AiModelConfig config) {
        String sql = h2Database ? h2UpsertSql() : postgresUpsertSql();

        String valueToStore = config.isEncrypted() ? encrypt(config.getConfigValue()) : config.getConfigValue();

        jdbcTemplate.update(sql,
                requiredLong(config.getId(), "id"),
                normalizeTenantId(config.getTenantId()),
                config.getConfigKey(),
                valueToStore,
                config.getConfigType().name(),
                config.isEncrypted() ? 1 : 0,
                config.getDescription(),
                nullableLong(config.getCreatedBy()),
                nullableLong(config.getUpdatedBy()),
                Timestamp.valueOf(config.getCreatedAt()),
                Timestamp.valueOf(config.getUpdatedAt()));
    }

    private String postgresUpsertSql() {
        return """
                INSERT INTO sa_ai_model_config
                    (id, tenant_id, config_key, config_value, config_type, is_encrypted, description,
                     created_by, updated_by, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT (tenant_id, config_key) DO UPDATE SET
                    config_value = EXCLUDED.config_value,
                    config_type = EXCLUDED.config_type,
                    is_encrypted = EXCLUDED.is_encrypted,
                    description = EXCLUDED.description,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """;
    }

    private String h2UpsertSql() {
        return """
                MERGE INTO sa_ai_model_config
                    (id, tenant_id, config_key, config_value, config_type, is_encrypted, description,
                     created_by, updated_by, created_at, updated_at, deleted)
                KEY (tenant_id, config_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;
    }

    @Override
    public void update(String configKey, String configValue, String updatedBy) {
        update(DEFAULT_TENANT_ID, configKey, configValue, updatedBy);
    }

    @Override
    public void update(String tenantId, String configKey, String configValue, String updatedBy) {
        Optional<AiModelConfig> existing = findByKey(tenantId, configKey);
        if (existing.isEmpty()) {
            return;
        }

        AiModelConfig config = existing.get();
        String valueToStore = config.isEncrypted() ? encrypt(configValue) : configValue;

        String sql = """
                UPDATE sa_ai_model_config
                SET config_value = ?, updated_by = ?, updated_at = ?
                WHERE tenant_id = ? AND config_key = ? AND deleted = 0
                """;

        jdbcTemplate.update(sql, valueToStore, nullableLong(updatedBy), Timestamp.valueOf(LocalDateTime.now()),
                normalizeTenantId(tenantId), configKey);
    }

    @Override
    public void delete(String configKey) {
        delete(DEFAULT_TENANT_ID, configKey);
    }

    @Override
    public void delete(String tenantId, String configKey) {
        String sql = """
                UPDATE sa_ai_model_config
                SET deleted = 1, updated_at = ?
                WHERE tenant_id = ? AND config_key = ?
                """;
        jdbcTemplate.update(sql, Timestamp.valueOf(LocalDateTime.now()), normalizeTenantId(tenantId), configKey);
    }

    private String encrypt(String value) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    private String decrypt(String encryptedValue) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    private String decryptOrUseLegacyPlaintext(String configKey, String value) {
        try {
            return decrypt(value);
        } catch (RuntimeException ex) {
            LOGGER.warn("AI model config {} is marked encrypted but cannot be decrypted; treating it as legacy plaintext",
                    configKey);
            return value;
        }
    }

    private class AiModelConfigRowMapper implements RowMapper<AiModelConfig> {
        @Override
        public AiModelConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiModelConfig config = new AiModelConfig();
            config.setId(rs.getString("id"));
            config.setTenantId(rs.getString("tenant_id"));
            config.setConfigKey(rs.getString("config_key"));

            boolean isEncrypted = rs.getInt("is_encrypted") == 1;
            String value = rs.getString("config_value");
            config.setConfigValue(isEncrypted ? decryptOrUseLegacyPlaintext(config.getConfigKey(), value) : value);

            config.setConfigType(AiModelConfig.ConfigType.valueOf(rs.getString("config_type")));
            config.setEncrypted(isEncrypted);
            config.setDescription(rs.getString("description"));
            config.setCreatedBy(rs.getString("created_by"));
            config.setUpdatedBy(rs.getString("updated_by"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            config.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            config.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);

            config.setDeleted(rs.getInt("deleted") == 1);
            return config;
        }
    }

    private String normalizeTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim();
    }

    private Long nullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

    private Long requiredLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return nullableLong(value);
    }

    private boolean isH2Database(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException ex) {
            LOGGER.warn("Unable to detect AI model config database dialect; defaulting to PostgreSQL SQL", ex);
            return false;
        }
    }
}
