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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffLimits;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCollaborationPolicyPort;
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
 * 协作授权端口 JDBC 适配器：策略存 {@code sa_agent_collaboration_policy}。
 *
 * <p>决策语义与内核默认实现一致：无显式策略放行（兼容既有分派路径），
 * 显式策略按 allowed/maxDepth 裁决，全局深度上限由 MeshPolicyPort 兜底。
 */
public class JdbcAgentCollaborationPolicyAdapter implements AgentCollaborationPolicyPort {

    private static final String POLICY_COLUMNS = """
            policy_id, tenant_id, source_agent_id, target_agent_id, allowed, max_depth,
            created_at, updated_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_collaboration_policy
            (policy_id, tenant_id, source_agent_id, target_agent_id, allowed, max_depth,
             created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_agent_collaboration_policy
            SET tenant_id = ?, source_agent_id = ?, target_agent_id = ?, allowed = ?,
                max_depth = ?, updated_at = ?
            WHERE policy_id = ?
            """;
    private static final String SQL_LIST = """
            SELECT %s FROM sa_agent_collaboration_policy
            WHERE tenant_id = ?
            ORDER BY created_at ASC, policy_id ASC
            """.formatted(POLICY_COLUMNS);
    private static final String SQL_FIND_MATCHING = """
            SELECT %s FROM sa_agent_collaboration_policy
            WHERE tenant_id = ? AND source_agent_id = ? AND target_agent_id = ?
            """.formatted(POLICY_COLUMNS);
    private static final String SQL_DELETE = """
            DELETE FROM sa_agent_collaboration_policy WHERE policy_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentCollaborationPolicyAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentCollaborationPolicyDecision decide(AgentCollaborationPolicyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.depth() > AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED,
                    "协作深度超过全局上限");
        }
        List<AgentCollaborationPolicy> matches = jdbcTemplate.query(SQL_FIND_MATCHING,
                this::mapPolicy, request.tenantId(), request.sourceAgentId(), request.targetAgentId());
        if (matches.isEmpty()) {
            return AgentCollaborationPolicyDecision.allow(null);
        }
        AgentCollaborationPolicy policy = matches.get(0);
        if (!policy.allowed()) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.POLICY_DENIED,
                    "协作策略拒绝 " + request.sourceAgentId() + " -> " + request.targetAgentId());
        }
        if (policy.maxDepth() != null && request.depth() > policy.maxDepth()) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED,
                    "协作策略深度上限 " + policy.maxDepth());
        }
        return AgentCollaborationPolicyDecision.allow("显式协作策略允许");
    }

    @Override
    public AgentCollaborationPolicy savePolicy(AgentCollaborationPolicy policy) {
        AgentCollaborationPolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        Instant now = Instant.now();
        Optional<AgentCollaborationPolicy> existing =
                jdbcTemplate.query(SQL_FIND_MATCHING, this::mapPolicy, safePolicy.tenantId(),
                        safePolicy.sourceAgentId(), safePolicy.targetAgentId()).stream().findFirst();
        if (existing.isPresent()) {
            jdbcTemplate.update(SQL_UPDATE,
                    safePolicy.tenantId(),
                    safePolicy.sourceAgentId(),
                    safePolicy.targetAgentId(),
                    safePolicy.allowed(),
                    safePolicy.maxDepth(),
                    toTimestamp(now),
                    safePolicy.policyId());
        } else {
            jdbcTemplate.update(SQL_INSERT,
                    safePolicy.policyId(),
                    safePolicy.tenantId(),
                    safePolicy.sourceAgentId(),
                    safePolicy.targetAgentId(),
                    safePolicy.allowed(),
                    safePolicy.maxDepth(),
                    toTimestamp(now),
                    toTimestamp(now));
        }
        return safePolicy;
    }

    @Override
    public List<AgentCollaborationPolicy> listPolicies(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST, this::mapPolicy, tenantId.trim());
    }

    @Override
    public void deletePolicy(String policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        jdbcTemplate.update(SQL_DELETE, policyId.trim());
    }

    private AgentCollaborationPolicy mapPolicy(ResultSet resultSet, int rowNum) throws SQLException {
        int maxDepth = resultSet.getInt("max_depth");
        boolean maxDepthPresent = !resultSet.wasNull();
        return new AgentCollaborationPolicy(
                resultSet.getString("policy_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("source_agent_id"),
                resultSet.getString("target_agent_id"),
                resultSet.getBoolean("allowed"),
                maxDepthPresent ? maxDepth : null);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
