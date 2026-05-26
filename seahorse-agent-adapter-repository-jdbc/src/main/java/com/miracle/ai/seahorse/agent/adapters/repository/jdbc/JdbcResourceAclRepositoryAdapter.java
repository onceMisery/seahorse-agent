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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclLookup;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclNaturalKey;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRulePage;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcResourceAclRepositoryAdapter implements ResourceAclRepositoryPort {

    private static final long MAX_PAGE_SIZE = 100L;

    private static final String RULE_COLUMNS = """
            rule_id, tenant_id, scope, resource_type, resource_id, subject_type, subject_id,
            action, effect, status, priority, expires_at, created_by, created_at, updated_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_resource_acl_rule
            (rule_id, tenant_id, scope, resource_type, resource_id, subject_type, subject_id,
             action, effect, status, priority, expires_at, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_resource_acl_rule
            SET tenant_id = ?,
                scope = ?,
                resource_type = ?,
                resource_id = ?,
                subject_type = ?,
                subject_id = ?,
                action = ?,
                effect = ?,
                status = ?,
                priority = ?,
                expires_at = ?,
                created_by = ?,
                created_at = ?,
                updated_at = ?
            WHERE rule_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_resource_acl_rule
            WHERE rule_id = ?
            """.formatted(RULE_COLUMNS);
    private static final String SQL_COUNT = """
            SELECT COUNT(1)
            FROM sa_resource_acl_rule
            WHERE 1 = 1
            """;
    private static final String SQL_PAGE = """
            SELECT %s
            FROM sa_resource_acl_rule
            WHERE 1 = 1
            """.formatted(RULE_COLUMNS);
    private static final String SQL_EFFECTIVE = """
            SELECT %s
            FROM sa_resource_acl_rule
            WHERE tenant_id = ?
              AND scope = ?
              AND resource_type = ?
              AND resource_id = ?
              AND subject_type = ?
              AND subject_id = ?
              AND action = ?
              AND status = ?
              AND (expires_at IS NULL OR expires_at > ?)
            ORDER BY CASE WHEN effect = ? THEN 0 ELSE 1 END ASC,
                     priority DESC,
                     created_at ASC
            """.formatted(RULE_COLUMNS);
    private static final String SQL_FIND_BY_NATURAL_KEY = """
            SELECT %s
            FROM sa_resource_acl_rule
            WHERE tenant_id = ?
              AND scope = ?
              AND resource_type = ?
              AND resource_id = ?
              AND subject_type = ?
              AND subject_id = ?
              AND action = ?
              AND status = ?
              AND (expires_at IS NULL OR expires_at > ?)
            ORDER BY CASE WHEN effect = ? THEN 0 ELSE 1 END ASC,
                     priority DESC,
                     created_at ASC
            """.formatted(RULE_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcResourceAclRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public ResourceAclRule save(ResourceAclRule rule) {
        ResourceAclRule safeRule = Objects.requireNonNull(rule, "rule must not be null");
        if (findById(safeRule.ruleId()).isPresent()) {
            update(safeRule);
            return safeRule;
        }
        insert(safeRule);
        return safeRule;
    }

    @Override
    public Optional<ResourceAclRule> findById(String ruleId) {
        if (!hasText(ruleId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapRule, ruleId.trim()).stream().findFirst();
    }

    @Override
    public ResourceAclRulePage page(ResourceAclQuery query) {
        ResourceAclQuery safeQuery = query == null
                ? new ResourceAclQuery(null, null, null, null, null, null,
                ResourceAclQuery.DEFAULT_CURRENT, ResourceAclQuery.DEFAULT_PAGE_SIZE)
                : query;
        long current = safeQuery.current();
        long size = clampSize(safeQuery.size());
        QueryParts filters = filters(safeQuery);

        Long total = jdbcTemplate.queryForObject(SQL_COUNT + filters.where(), Long.class, filters.args().toArray());
        long safeTotal = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(filters.args());
        pageArgs.add(size);
        pageArgs.add((current - 1L) * size);
        List<ResourceAclRule> records = jdbcTemplate.query(
                SQL_PAGE + filters.where() + " ORDER BY updated_at DESC, rule_id ASC LIMIT ? OFFSET ?",
                this::mapRule,
                pageArgs.toArray());
        return new ResourceAclRulePage(records, safeTotal, size, current, pages(safeTotal, size));
    }

    @Override
    public List<ResourceAclRule> findEffective(ResourceAclLookup lookup) {
        ResourceAclLookup safeLookup = Objects.requireNonNull(lookup, "lookup must not be null");
        Timestamp now = toTimestamp(safeLookup.now());
        return jdbcTemplate.query(SQL_EFFECTIVE,
                this::mapRule,
                safeLookup.tenantId(),
                ResourceAclRuleScope.EXACT_RESOURCE.name(),
                safeLookup.resourceType(),
                safeLookup.resourceId(),
                safeLookup.subjectType().name(),
                safeLookup.subjectId(),
                safeLookup.action().name(),
                ResourceAclRuleStatus.ENABLED.name(),
                now,
                AccessDecisionEffect.DENY.name());
    }

    @Override
    public List<ResourceAclRule> findByNaturalKey(ResourceAclNaturalKey naturalKey, Instant now) {
        ResourceAclNaturalKey safeNaturalKey = Objects.requireNonNull(naturalKey, "naturalKey must not be null");
        Instant safeNow = Objects.requireNonNull(now, "now must not be null");
        return jdbcTemplate.query(SQL_FIND_BY_NATURAL_KEY,
                this::mapRule,
                safeNaturalKey.tenantId(),
                safeNaturalKey.scope().name(),
                safeNaturalKey.resourceType(),
                safeNaturalKey.resourceId(),
                safeNaturalKey.subjectType().name(),
                safeNaturalKey.subjectId(),
                safeNaturalKey.action().name(),
                ResourceAclRuleStatus.ENABLED.name(),
                toTimestamp(safeNow),
                AccessDecisionEffect.DENY.name());
    }

    private void insert(ResourceAclRule rule) {
        jdbcTemplate.update(SQL_INSERT,
                rule.ruleId(),
                rule.tenantId(),
                rule.scope().name(),
                rule.resourceType(),
                rule.resourceId(),
                rule.subjectType().name(),
                rule.subjectId(),
                rule.action().name(),
                rule.effect().name(),
                rule.status().name(),
                rule.priority(),
                toTimestamp(rule.expiresAt()),
                rule.createdBy(),
                toTimestamp(rule.createdAt()),
                toTimestamp(rule.updatedAt()));
    }

    private void update(ResourceAclRule rule) {
        jdbcTemplate.update(SQL_UPDATE,
                rule.tenantId(),
                rule.scope().name(),
                rule.resourceType(),
                rule.resourceId(),
                rule.subjectType().name(),
                rule.subjectId(),
                rule.action().name(),
                rule.effect().name(),
                rule.status().name(),
                rule.priority(),
                toTimestamp(rule.expiresAt()),
                rule.createdBy(),
                toTimestamp(rule.createdAt()),
                toTimestamp(rule.updatedAt()),
                rule.ruleId());
    }

    private ResourceAclRule mapRule(ResultSet resultSet, int rowNum) throws SQLException {
        return new ResourceAclRule(
                resultSet.getString("rule_id"),
                resultSet.getString("tenant_id"),
                ResourceAclRuleScope.valueOf(resultSet.getString("scope")),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                AccessSubjectType.valueOf(resultSet.getString("subject_type")),
                resultSet.getString("subject_id"),
                ResourceAction.valueOf(resultSet.getString("action")),
                AccessDecisionEffect.valueOf(resultSet.getString("effect")),
                ResourceAclRuleStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("priority"),
                toInstant(resultSet.getTimestamp("expires_at")),
                resultSet.getString("created_by"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private QueryParts filters(ResourceAclQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addTextFilter(clauses, args, "tenant_id", query.tenantId());
        addTextFilter(clauses, args, "resource_type", query.resourceType());
        addTextFilter(clauses, args, "resource_id", query.resourceId());
        if (query.subjectType() != null) {
            clauses.add("subject_type = ?");
            args.add(query.subjectType().name());
        }
        addTextFilter(clauses, args, "subject_id", query.subjectId());
        if (query.status() != null) {
            clauses.add("status = ?");
            args.add(query.status().name());
        }
        if (clauses.isEmpty()) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND " + String.join(" AND ", clauses), args);
    }

    private void addTextFilter(List<String> clauses, List<Object> args, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        clauses.add(column + " = ?");
        args.add(value.trim());
    }

    private long clampSize(long size) {
        if (size <= 0L) {
            return ResourceAclQuery.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long pages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
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

    private record QueryParts(String where, List<Object> args) {

        private QueryParts {
            args = List.copyOf(args);
        }
    }
}
