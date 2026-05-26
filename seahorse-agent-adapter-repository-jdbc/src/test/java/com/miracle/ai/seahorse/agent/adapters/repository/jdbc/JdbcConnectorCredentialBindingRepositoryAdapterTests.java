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
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConnectorCredentialBindingRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveFindActiveAndRotateConnectorCredentialBinding() {
        DriverManagerDataSource dataSource = dataSource("connector-credential-binding");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCredentialBindingSchema(jdbcTemplate);
        JdbcConnectorCredentialBindingRepositoryAdapter adapter =
                new JdbcConnectorCredentialBindingRepositoryAdapter(dataSource);
        ConnectorCredentialBinding active = binding(
                "binding-1",
                ConnectorCredentialBindingStatus.ACTIVE,
                "secret-ref-1",
                null);

        adapter.save(active);
        adapter.save(active.rotate(NOW.plusSeconds(60)));
        adapter.save(binding(
                "binding-2",
                ConnectorCredentialBindingStatus.ACTIVE,
                "secret-ref-2",
                null));

        assertThat(adapter.findActive(
                "tenant-1",
                "conn-1",
                "op-1",
                CredentialAuthType.STATIC_BEARER))
                .get()
                .extracting(ConnectorCredentialBinding::bindingId)
                .isEqualTo("binding-2");
        assertThat(adapter.findActiveByOperation("tenant-1", "conn-1", "op-1"))
                .extracting(ConnectorCredentialBinding::bindingId)
                .containsExactly("binding-2");
    }

    private static ConnectorCredentialBinding binding(String bindingId,
                                                      ConnectorCredentialBindingStatus status,
                                                      String credentialRef,
                                                      Instant rotatedAt) {
        return new ConnectorCredentialBinding(
                bindingId,
                "tenant-1",
                "conn-1",
                "op-1",
                CredentialAuthType.STATIC_BEARER,
                credentialRef,
                status,
                "admin-1",
                NOW,
                rotatedAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    static void createCredentialBindingSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_connector_credential_binding (
                    binding_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    connector_id VARCHAR(64) NOT NULL,
                    operation_id VARCHAR(64) NOT NULL,
                    auth_type VARCHAR(32) NOT NULL,
                    credential_ref VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    bound_by VARCHAR(64) NOT NULL,
                    bound_at TIMESTAMP NOT NULL,
                    rotated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_connector_credential_binding_operation
                ON sa_connector_credential_binding(tenant_id, connector_id, operation_id, auth_type, status)
                """);
    }
}
