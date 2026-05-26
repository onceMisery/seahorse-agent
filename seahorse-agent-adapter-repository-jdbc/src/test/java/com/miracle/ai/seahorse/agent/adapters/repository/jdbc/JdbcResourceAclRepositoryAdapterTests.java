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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclLookup;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclNaturalKey;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRulePage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcResourceAclRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void shouldSaveFindAndPageResourceAclRules() {
        DriverManagerDataSource dataSource = dataSource("resource-acl-page");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createResourceAclSchema(jdbcTemplate);
        JdbcResourceAclRepositoryAdapter adapter = new JdbcResourceAclRepositoryAdapter(dataSource);

        adapter.save(rule("rule-1", "doc-1", AccessDecisionEffect.ALLOW, ResourceAclRuleStatus.ENABLED,
                100, null, NOW));
        adapter.save(rule("rule-2", "doc-2", AccessDecisionEffect.DENY, ResourceAclRuleStatus.DISABLED,
                50, null, NOW.plusSeconds(1)));

        ResourceAclRulePage page = adapter.page(new ResourceAclQuery(
                "tenant-1",
                ContextResourceType.DOCUMENT.value(),
                null,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                null,
                1L,
                10L));

        assertThat(adapter.findById("rule-1")).isPresent();
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(ResourceAclRule::ruleId).containsExactly("rule-2", "rule-1");
    }

    @Test
    void shouldFindEffectiveRulesByDenyWinsOrderingAndIgnoreExpiredRules() {
        DriverManagerDataSource dataSource = dataSource("resource-acl-effective");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createResourceAclSchema(jdbcTemplate);
        JdbcResourceAclRepositoryAdapter adapter = new JdbcResourceAclRepositoryAdapter(dataSource);

        adapter.save(rule("low-deny", "doc-1", AccessDecisionEffect.DENY, ResourceAclRuleStatus.ENABLED,
                10, null, NOW));
        adapter.save(rule("high-allow", "doc-1", AccessDecisionEffect.ALLOW, ResourceAclRuleStatus.ENABLED,
                100, null, NOW.plusSeconds(1)));
        adapter.save(rule("high-deny", "doc-1", AccessDecisionEffect.DENY, ResourceAclRuleStatus.ENABLED,
                100, null, NOW.plusSeconds(2)));
        adapter.save(rule("expired-deny", "doc-1", AccessDecisionEffect.DENY, ResourceAclRuleStatus.ENABLED,
                200, NOW.minusSeconds(1), NOW.plusSeconds(3)));

        List<ResourceAclRule> effective = adapter.findEffective(new ResourceAclLookup(
                "tenant-1",
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                NOW));

        assertThat(effective).extracting(ResourceAclRule::ruleId)
                .containsExactly("high-deny", "low-deny", "high-allow");
    }

    @Test
    void shouldFindRulesByNaturalKeyAndRejectInvalidOrDuplicateEffectiveRules() {
        DriverManagerDataSource dataSource = dataSource("resource-acl-natural-key");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createResourceAclSchema(jdbcTemplate);
        JdbcResourceAclRepositoryAdapter adapter = new JdbcResourceAclRepositoryAdapter(dataSource);

        adapter.save(rule("rule-1", "doc-1", AccessDecisionEffect.ALLOW, ResourceAclRuleStatus.ENABLED,
                100, null, NOW));
        adapter.save(rule("disabled-duplicate", "doc-1", AccessDecisionEffect.DENY, ResourceAclRuleStatus.DISABLED,
                50, null, NOW.plusSeconds(1)));

        List<ResourceAclRule> naturalKeyRules = adapter.findByNaturalKey(new ResourceAclNaturalKey(
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ), NOW);

        assertThat(naturalKeyRules).extracting(ResourceAclRule::ruleId).containsExactly("rule-1");
        adapter.save(rule("active-deny", "doc-1", AccessDecisionEffect.DENY,
                ResourceAclRuleStatus.ENABLED, 10, null, NOW.plusSeconds(2)));
        assertThatThrownBy(() -> adapter.save(rule("active-exact-duplicate", "doc-1", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.ENABLED, 100, null, NOW.plusSeconds(3))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sa_resource_acl_rule
                        (rule_id, tenant_id, scope, resource_type, resource_id, subject_type, subject_id,
                         action, effect, status, priority, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "invalid-effect",
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE.name(),
                ContextResourceType.DOCUMENT.value(),
                "doc-2",
                AccessSubjectType.USER_DELEGATED_AGENT.name(),
                "user-1",
                ResourceAction.READ.name(),
                AccessDecisionEffect.MASK.name(),
                ResourceAclRuleStatus.ENABLED.name(),
                10,
                "admin-1",
                NOW,
                NOW))
                .isInstanceOf(RuntimeException.class);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createResourceAclSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_resource_acl_rule (
                    rule_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    scope VARCHAR(32) NOT NULL,
                    resource_type VARCHAR(64) NOT NULL,
                    resource_id VARCHAR(128) NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    subject_id VARCHAR(64) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    effect VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    priority INT NOT NULL,
                    expires_at TIMESTAMP,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    CONSTRAINT chk_sa_resource_acl_scope CHECK (scope IN ('EXACT_RESOURCE', 'RESOURCE_TYPE')),
                    CONSTRAINT chk_sa_resource_acl_subject_type CHECK (subject_type IN ('USER', 'AGENT', 'USER_DELEGATED_AGENT')),
                    CONSTRAINT chk_sa_resource_acl_action CHECK (action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE')),
                    CONSTRAINT chk_sa_resource_acl_effect CHECK (effect IN ('ALLOW', 'DENY')),
                    CONSTRAINT chk_sa_resource_acl_status CHECK (status IN ('ENABLED', 'DISABLED'))
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_sa_resource_acl_active_exact_rule
                ON sa_resource_acl_rule(tenant_id, scope, resource_type, resource_id, subject_type, subject_id, action, effect, priority, status)
                """);
    }

    private static ResourceAclRule rule(String ruleId,
                                        String resourceId,
                                        AccessDecisionEffect effect,
                                        ResourceAclRuleStatus status,
                                        int priority,
                                        Instant expiresAt,
                                        Instant createdAt) {
        return new ResourceAclRule(
                ruleId,
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                resourceId,
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                status,
                priority,
                expiresAt,
                "admin-1",
                createdAt,
                createdAt);
    }
}
