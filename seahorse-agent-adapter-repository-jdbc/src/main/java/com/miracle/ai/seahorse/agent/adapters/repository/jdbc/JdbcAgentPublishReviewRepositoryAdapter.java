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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentPublishReview;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentPublishReviewRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link AgentPublishReviewRepositoryPort} that persists
 * agent publish review records in the {@code sa_agent_publish_review} table.
 */
public class JdbcAgentPublishReviewRepositoryAdapter implements AgentPublishReviewRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentPublishReviewRepositoryAdapter.class);

    private static final String COLUMNS = """
            id, agent_id, tenant_id, submitted_by, status, review_comment, reviewed_by, submitted_at, reviewed_at
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_publish_review
            (id, agent_id, tenant_id, submitted_by, status, review_comment, reviewed_by, submitted_at, reviewed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_agent_publish_review
            WHERE id = ? AND tenant_id = ?
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_AGENT_ID = """
            SELECT %s
            FROM sa_agent_publish_review
            WHERE agent_id = ? AND tenant_id = ?
            ORDER BY submitted_at DESC
            LIMIT 1
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_STATUS = """
            SELECT %s
            FROM sa_agent_publish_review
            WHERE status = ? AND tenant_id = ?
            ORDER BY submitted_at DESC
            LIMIT ? OFFSET ?
            """.formatted(COLUMNS);

    private static final String SQL_COUNT_BY_STATUS = """
            SELECT COUNT(1)
            FROM sa_agent_publish_review
            WHERE status = ? AND tenant_id = ?
            """;

    private static final String SQL_DELETE_BY_ID = """
            DELETE FROM sa_agent_publish_review
            WHERE id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentPublishReviewRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(AgentPublishReview review) {
        Objects.requireNonNull(review, "review must not be null");
        Long id = SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId(review.tenantId());
        try {
            jdbcTemplate.update(SQL_INSERT,
                    id,
                    review.agentId(),
                    tenantId,
                    review.submittedBy(),
                    review.status(),
                    review.reviewComment(),
                    review.reviewedBy(),
                    Timestamp.from(review.submittedAt()),
                    review.reviewedAt() != null ? Timestamp.from(review.reviewedAt()) : null);
            return id;
        } catch (Exception e) {
            log.warn("Failed to save publish review for agent={}: {}", review.agentId(), e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<AgentPublishReview> findById(Long id) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            List<AgentPublishReview> results = jdbcTemplate.query(
                    SQL_FIND_BY_ID, new ReviewRowMapper(), id, tenantId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find publish review id={}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<AgentPublishReview> findByAgentId(String agentId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            List<AgentPublishReview> results = jdbcTemplate.query(
                    SQL_FIND_BY_AGENT_ID, new ReviewRowMapper(), agentId, tenantId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find publish review for agent={}: {}", agentId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<AgentPublishReview> findByStatus(String status, int page, int size) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            int offset = page * size;
            return jdbcTemplate.query(SQL_FIND_BY_STATUS, new ReviewRowMapper(), status, tenantId, size, offset);
        } catch (Exception e) {
            log.warn("Failed to find publish reviews with status={}: {}", status, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countByStatus(String status) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            Long count = jdbcTemplate.queryForObject(SQL_COUNT_BY_STATUS, Long.class, status, tenantId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to count publish reviews with status={}: {}", status, e.getMessage());
            return 0L;
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            int rows = jdbcTemplate.update(SQL_DELETE_BY_ID, id, tenantId);
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to delete publish review id={}: {}", id, e.getMessage());
            return false;
        }
    }

    private static class ReviewRowMapper implements RowMapper<AgentPublishReview> {
        @Override
        public AgentPublishReview mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp submittedAt = rs.getTimestamp("submitted_at");
            Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
            return new AgentPublishReview(
                    rs.getLong("id"),
                    rs.getString("agent_id"),
                    rs.getString("tenant_id"),
                    rs.getString("submitted_by"),
                    rs.getString("status"),
                    rs.getString("review_comment"),
                    rs.getString("reviewed_by"),
                    submittedAt != null ? submittedAt.toInstant() : Instant.EPOCH,
                    reviewedAt != null ? reviewedAt.toInstant() : null
            );
        }
    }
}
