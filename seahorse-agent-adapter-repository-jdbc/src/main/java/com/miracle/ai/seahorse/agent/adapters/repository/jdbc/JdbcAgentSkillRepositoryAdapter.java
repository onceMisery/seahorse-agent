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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
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

public class JdbcAgentSkillRepositoryAdapter implements AgentSkillRepositoryPort {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentSkillRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcAgentSkillRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public void saveSkill(AgentSkill skill) {
        AgentSkill safe = Objects.requireNonNull(skill, "skill must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE sa_agent_skill
                SET category = ?, source = ?, status = ?, enabled = ?, latest_revision_id = ?, description = ?,
                    tags_json = ?, allowed_tools_json = ?, updated_by = ?, updated_at = ?, deleted = ?
                WHERE tenant_id = ? AND skill_name = ?
                """,
                safe.category().name(),
                safe.source().name(),
                safe.status().name(),
                safe.enabled() ? 1 : 0,
                safe.latestRevisionId(),
                safe.description(),
                toJson(safe.tags()),
                toJson(safe.allowedTools()),
                safe.updatedBy(),
                toTimestamp(safe.updatedAt()),
                safe.status() == AgentSkillStatus.DELETED ? 1 : 0,
                safe.tenantId(),
                safe.name());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sa_agent_skill
                    (skill_name, tenant_id, category, source, status, enabled, latest_revision_id, description,
                     tags_json, allowed_tools_json, created_by, updated_by, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    safe.name(),
                    safe.tenantId(),
                    safe.category().name(),
                    safe.source().name(),
                    safe.status().name(),
                    safe.enabled() ? 1 : 0,
                    safe.latestRevisionId(),
                    safe.description(),
                    toJson(safe.tags()),
                    toJson(safe.allowedTools()),
                    safe.createdBy(),
                    safe.updatedBy(),
                    toTimestamp(safe.createdAt()),
                    toTimestamp(safe.updatedAt()),
                    safe.status() == AgentSkillStatus.DELETED ? 1 : 0);
        }
    }

    @Override
    public Optional<AgentSkill> findSkill(String tenantId, String name) {
        if (!hasText(name)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT skill_name, tenant_id, category, source, status, enabled, latest_revision_id, description,
                       tags_json, allowed_tools_json, created_by, updated_by, created_at, updated_at
                FROM sa_agent_skill
                WHERE tenant_id = ? AND skill_name = ? AND deleted = 0
                """, this::mapSkill, defaultTenant(tenantId), name.trim()).stream().findFirst();
    }

    @Override
    public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        String safeTenant = defaultTenant(tenantId);
        QueryParts filter = keywordFilter(keyword);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(safeTenant);
        countArgs.addAll(filter.args());
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM sa_agent_skill
                WHERE tenant_id = ? AND deleted = 0
                """ + filter.sql(), Long.class, countArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(safeSize);
        pageArgs.add((safeCurrent - 1) * safeSize);
        List<AgentSkill> records = jdbcTemplate.query("""
                SELECT skill_name, tenant_id, category, source, status, enabled, latest_revision_id, description,
                       tags_json, allowed_tools_json, created_by, updated_by, created_at, updated_at
                FROM sa_agent_skill
                WHERE tenant_id = ? AND deleted = 0
                """ + filter.sql() + " ORDER BY updated_at DESC, skill_name ASC LIMIT ? OFFSET ?",
                this::mapSkill,
                pageArgs.toArray());
        long safeTotal = total == null ? 0 : total;
        return new AgentSkillPage(records, safeTotal, safeSize, safeCurrent,
                safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize);
    }

    @Override
    public List<String> listTenants() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT tenant_id
                FROM sa_agent_skill
                WHERE deleted = 0
                ORDER BY tenant_id ASC
                """, String.class);
    }

    @Override
    public void saveRevision(AgentSkillRevision revision) {
        AgentSkillRevision safe = Objects.requireNonNull(revision, "revision must not be null");
        jdbcTemplate.update("""
                INSERT INTO sa_agent_skill_revision
                (revision_id, skill_name, tenant_id, revision_no, content_hash, content, frontmatter_json,
                 scan_decision, scan_result_json, created_by, created_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                safe.revisionId(),
                safe.skillName(),
                safe.tenantId(),
                safe.revisionNo(),
                safe.contentHash(),
                safe.content(),
                safe.frontmatterJson(),
                safe.scanDecision().name(),
                safe.scanResultJson(),
                safe.createdBy(),
                toTimestamp(safe.createdAt()));
    }

    @Override
    public long nextRevisionNo(String tenantId, String skillName) {
        Long next = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(revision_no), 0) + 1
                FROM sa_agent_skill_revision
                WHERE tenant_id = ? AND skill_name = ? AND deleted = 0
                """, Long.class, defaultTenant(tenantId), skillName);
        return next == null ? 1 : next;
    }

    @Override
    public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
        if (!hasText(revisionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT revision_id, skill_name, tenant_id, revision_no, content_hash, content, frontmatter_json,
                       scan_decision, scan_result_json, created_by, created_at
                FROM sa_agent_skill_revision
                WHERE tenant_id = ? AND revision_id = ? AND deleted = 0
                """, this::mapRevision, defaultTenant(tenantId), revisionId.trim()).stream().findFirst();
    }

    @Override
    public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
        return jdbcTemplate.query("""
                SELECT revision_id, skill_name, tenant_id, revision_no, content_hash, content, frontmatter_json,
                       scan_decision, scan_result_json, created_by, created_at
                FROM sa_agent_skill_revision
                WHERE tenant_id = ? AND skill_name = ? AND deleted = 0
                ORDER BY revision_no DESC
                """, this::mapRevision, defaultTenant(tenantId), skillName);
    }

    @Override
    public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
        return jdbcTemplate.query("""
                SELECT agent_id, tenant_id, skill_name, revision_id, inject_mode, created_by, created_at
                FROM sa_agent_skill_binding
                WHERE tenant_id = ? AND agent_id = ? AND deleted = 0
                ORDER BY pk_id ASC
                """, this::mapBinding, defaultTenant(tenantId), agentId);
    }

    @Override
    public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
        String safeTenant = defaultTenant(tenantId);
        jdbcTemplate.update("""
                UPDATE sa_agent_skill_binding
                SET deleted = 1
                WHERE tenant_id = ? AND agent_id = ? AND deleted = 0
                """, safeTenant, agentId);
        List<AgentSkillBinding> items = bindings == null ? List.of() : bindings;
        if (items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO sa_agent_skill_binding
                (agent_id, tenant_id, skill_name, revision_id, inject_mode, created_by, created_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, items, items.size(), (ps, binding) -> {
                    ps.setString(1, binding.agentId());
                    ps.setString(2, binding.tenantId());
                    ps.setString(3, binding.skillName());
                    ps.setString(4, binding.revisionId());
                    ps.setString(5, binding.injectMode().name());
                    ps.setString(6, binding.createdBy());
                    ps.setTimestamp(7, toTimestamp(binding.createdAt()));
                });
    }

    private AgentSkill mapSkill(ResultSet rs, int rowNum) throws SQLException {
        return new AgentSkill(
                rs.getString("skill_name"),
                rs.getString("tenant_id"),
                AgentSkillCategory.valueOf(rs.getString("category")),
                AgentSkillSource.valueOf(rs.getString("source")),
                AgentSkillStatus.valueOf(rs.getString("status")),
                rs.getInt("enabled") == 1,
                rs.getString("latest_revision_id"),
                rs.getString("description"),
                stringList(rs.getString("tags_json")),
                stringList(rs.getString("allowed_tools_json")),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private AgentSkillRevision mapRevision(ResultSet rs, int rowNum) throws SQLException {
        return new AgentSkillRevision(
                rs.getString("revision_id"),
                rs.getString("skill_name"),
                rs.getString("tenant_id"),
                rs.getLong("revision_no"),
                rs.getString("content_hash"),
                rs.getString("content"),
                rs.getString("frontmatter_json"),
                SkillScanDecision.valueOf(rs.getString("scan_decision")),
                rs.getString("scan_result_json"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private AgentSkillBinding mapBinding(ResultSet rs, int rowNum) throws SQLException {
        return new AgentSkillBinding(
                rs.getString("agent_id"),
                rs.getString("tenant_id"),
                rs.getString("skill_name"),
                rs.getString("revision_id"),
                SkillInjectMode.valueOf(rs.getString("inject_mode")),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private QueryParts keywordFilter(String keyword) {
        if (!hasText(keyword)) {
            return new QueryParts("", List.of());
        }
        String like = "%" + keyword.trim() + "%";
        return new QueryParts(" AND (skill_name LIKE ? OR description LIKE ?)", List.of(like, like));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize json", ex);
        }
    }

    private List<String> stringList(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String defaultTenant(String tenantId) {
        return hasText(tenantId) ? tenantId.trim() : AgentDefinition.DEFAULT_TENANT_ID;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QueryParts(String sql, List<Object> args) {
    }
}
