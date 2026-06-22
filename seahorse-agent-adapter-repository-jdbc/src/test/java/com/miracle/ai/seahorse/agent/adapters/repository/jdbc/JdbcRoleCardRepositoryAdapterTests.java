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

import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRoleCardRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcRoleCardRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:role-card;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRoleCardRepositoryAdapter(dataSource);
    }

    @Test
    void shouldSaveUpdateListAndFindRoleCardsByUser() {
        RoleCardRecord first = new RoleCardRecord();
        first.setUserId("default");
        first.setName("Coach");
        first.setDefinition("Ask short questions.");
        first.setAvatarRef("coach.png");
        first.setHigherPerm(1);
        first.setShareScope("TEAM");
        first.setApprovalStatus("APPROVED");
        first.setPublished(1);
        first.setAssetSource("SYSTEM");
        first.setPresetKey("role.research-analyst");
        first.setPresetVersion(3);
        first.setReadonly(1);
        Long firstId = adapter.save(first);

        RoleCardRecord second = new RoleCardRecord();
        second.setUserId("default");
        second.setName("Planner");
        second.setDefinition("Write plans.");
        second.setHigherPerm(0);
        Long secondId = adapter.save(second);

        RoleCardRecord update = adapter.findById("default", firstId).orElseThrow();
        update.setName("Senior Coach");
        update.setDefinition("Ask concise questions.");
        update.setAvatarRef("senior.png");
        update.setHigherPerm(0);
        update.setShareScope("PRIVATE");
        update.setApprovalStatus("PENDING");
        update.setPublished(0);
        adapter.save(update);

        List<RoleCardRecord> cards = adapter.listByUser("default");

        assertThat(cards).extracting(RoleCardRecord::getId).containsExactlyInAnyOrder(firstId, secondId);
        assertThat(adapter.findById("default", firstId)).get()
                .satisfies(card -> {
                    assertThat(card.getName()).isEqualTo("Senior Coach");
                    assertThat(card.getDefinition()).isEqualTo("Ask concise questions.");
                    assertThat(card.getAvatarRef()).isEqualTo("senior.png");
                    assertThat(card.getHigherPerm()).isZero();
                    assertThat(card.getEnabled()).isZero();
                    assertThat(card.getShareScope()).isEqualTo("PRIVATE");
                    assertThat(card.getApprovalStatus()).isEqualTo("PENDING");
                    assertThat(card.getPublished()).isZero();
                    assertThat(card.getAssetSource()).isEqualTo("SYSTEM");
                    assertThat(card.getPresetKey()).isEqualTo("role.research-analyst");
                    assertThat(card.getPresetVersion()).isEqualTo(3);
                    assertThat(card.getReadonly()).isEqualTo(1);
                });
        assertThat(adapter.findById("other", firstId)).isPresent();
        assertThat(adapter.findById("other", secondId)).isEmpty();
    }

    @Test
    void shouldEnableOneRoleCardAndIgnoreDeletedCards() {
        Long firstId = adapter.save(record("default", "A", "alpha"));
        Long secondId = adapter.save(record("default", "B", "beta"));
        Long otherUserId = adapter.save(record("other", "C", "gamma"));

        adapter.setEnabled("default", firstId, true);
        adapter.disableAll("default");
        adapter.setEnabled("default", secondId, true);
        adapter.delete("default", secondId);

        assertThat(adapter.findEnabled("default")).isEmpty();
        assertThat(adapter.findById("default", secondId)).isEmpty();
        assertThat(adapter.listByUser("default")).extracting(RoleCardRecord::getId).containsExactly(firstId);
        assertThat(adapter.findById("other", otherUserId)).isPresent();
    }

    @Test
    void shouldExposeSystemRoleCardsToEveryUser() {
        RoleCardRecord preset = record("system", "Research Analyst", "Analyze requirements.");
        preset.setAssetSource("SYSTEM");
        preset.setPresetKey("role.research-analyst");
        preset.setReadonly(1);
        Long presetId = adapter.save(preset);

        assertThat(adapter.listByUser("default")).extracting(RoleCardRecord::getId).contains(presetId);
        assertThat(adapter.findById("other", presetId)).hasValueSatisfying(found -> {
            assertThat(found.getAssetSource()).isEqualTo("SYSTEM");
            assertThat(found.getReadonly()).isEqualTo(1);
        });
    }

    private RoleCardRecord record(String userId, String name, String definition) {
        RoleCardRecord record = new RoleCardRecord();
        record.setUserId(userId);
        record.setName(name);
        record.setDefinition(definition);
        record.setHigherPerm(0);
        return record;
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_role_card");
        jdbcTemplate.execute("""
                CREATE TABLE sa_role_card (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id VARCHAR(64) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    definition TEXT NOT NULL,
                    avatar_ref VARCHAR(512),
                    higher_perm SMALLINT NOT NULL DEFAULT 0,
                    enabled SMALLINT NOT NULL DEFAULT 0,
                    share_scope VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
                    approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    published SMALLINT NOT NULL DEFAULT 0,
                    asset_source VARCHAR(32) NOT NULL DEFAULT 'USER',
                    preset_key VARCHAR(128),
                    preset_version INTEGER NOT NULL DEFAULT 1,
                    readonly SMALLINT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
