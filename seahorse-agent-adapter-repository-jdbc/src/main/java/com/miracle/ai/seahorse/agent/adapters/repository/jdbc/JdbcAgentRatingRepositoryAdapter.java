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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentRating;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentRatingRepositoryPort;
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
 * JDBC adapter for {@link AgentRatingRepositoryPort} that persists
 * agent rating records in the {@code sa_agent_rating} table.
 */
public class JdbcAgentRatingRepositoryAdapter implements AgentRatingRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentRatingRepositoryAdapter.class);

    private static final String COLUMNS = """
            id, agent_id, user_id, rating, comment, created_at
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_rating
            (id, agent_id, user_id, rating, comment, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_AGENT_AND_USER = """
            SELECT %s
            FROM sa_agent_rating
            WHERE agent_id = ? AND user_id = ?
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_AGENT = """
            SELECT %s
            FROM sa_agent_rating
            WHERE agent_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.formatted(COLUMNS);

    private static final String SQL_COUNT_BY_AGENT = """
            SELECT COUNT(1)
            FROM sa_agent_rating
            WHERE agent_id = ?
            """;

    private static final String SQL_AVG_RATING = """
            SELECT AVG(rating)
            FROM sa_agent_rating
            WHERE agent_id = ?
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_agent_rating
            SET rating = ?, comment = ?, created_at = ?
            WHERE id = ?
            """;

    private static final String SQL_DELETE_BY_ID = """
            DELETE FROM sa_agent_rating
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRatingRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(AgentRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        Long id = SnowflakeIds.nextId();
        try {
            jdbcTemplate.update(SQL_INSERT,
                    id,
                    rating.agentId(),
                    rating.userId(),
                    rating.rating(),
                    rating.comment(),
                    Timestamp.from(rating.createdAt()));
            return id;
        } catch (Exception e) {
            log.warn("Failed to save rating for agent={}, user={}: {}",
                    rating.agentId(), rating.userId(), e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<AgentRating> findByAgentIdAndUserId(String agentId, Long userId) {
        try {
            List<AgentRating> results = jdbcTemplate.query(
                    SQL_FIND_BY_AGENT_AND_USER, new RatingRowMapper(), agentId, userId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find rating for agent={}, user={}: {}", agentId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<AgentRating> findByAgentId(String agentId, int page, int size) {
        try {
            int offset = page * size;
            return jdbcTemplate.query(SQL_FIND_BY_AGENT, new RatingRowMapper(), agentId, size, offset);
        } catch (Exception e) {
            log.warn("Failed to find ratings for agent={}: {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countByAgentId(String agentId) {
        try {
            Long count = jdbcTemplate.queryForObject(SQL_COUNT_BY_AGENT, Long.class, agentId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to count ratings for agent={}: {}", agentId, e.getMessage());
            return 0L;
        }
    }

    @Override
    public double getAverageRating(String agentId) {
        try {
            Double avg = jdbcTemplate.queryForObject(SQL_AVG_RATING, Double.class, agentId);
            return avg != null ? avg : 0.0;
        } catch (Exception e) {
            log.warn("Failed to get average rating for agent={}: {}", agentId, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public boolean update(AgentRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        try {
            int rows = jdbcTemplate.update(SQL_UPDATE,
                    rating.rating(),
                    rating.comment(),
                    Timestamp.from(rating.createdAt()),
                    rating.id());
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to update rating id={}: {}", rating.id(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteById(Long id) {
        try {
            int rows = jdbcTemplate.update(SQL_DELETE_BY_ID, id);
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to delete rating id={}: {}", id, e.getMessage());
            return false;
        }
    }

    private static class RatingRowMapper implements RowMapper<AgentRating> {
        @Override
        public AgentRating mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new AgentRating(
                    rs.getLong("id"),
                    rs.getString("agent_id"),
                    rs.getLong("user_id"),
                    rs.getInt("rating"),
                    rs.getString("comment"),
                    createdAt != null ? createdAt.toInstant() : Instant.EPOCH
            );
        }
    }
}
