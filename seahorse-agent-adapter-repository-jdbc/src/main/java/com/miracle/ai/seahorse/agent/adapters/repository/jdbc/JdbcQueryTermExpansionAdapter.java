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
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 基于 {@code t_query_term_mapping} 表的在线术语扩展 adapter。
 */
public class JdbcQueryTermExpansionAdapter implements QueryTermExpansionPort {

    static final String CACHE_KEY = "seahorse-agent:query-term:mappings";

    private static final Logger LOG = LoggerFactory.getLogger(JdbcQueryTermExpansionAdapter.class);
    private static final TypeReference<List<Rule>> RULES_TYPE = new TypeReference<>() {
    };
    private static final int EXACT_MATCH = 1;
    private static final int FUZZY_MATCH = 2;
    private static final int REGEX_MATCH = 3;
    private static final String SQL_LOAD_RULES = """
            SELECT source_term, target_term, match_type, priority
            FROM t_query_term_mapping
            WHERE deleted = 0 AND enabled = 1
            ORDER BY priority ASC, update_time DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final KeyValueCachePort cachePort;
    private final ObjectMapper objectMapper;
    private final Options options;

    public JdbcQueryTermExpansionAdapter(DataSource dataSource,
                                         KeyValueCachePort cachePort,
                                         ObjectMapper objectMapper,
                                         Options options) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.options = Objects.requireNonNullElseGet(options, Options::defaults);
    }

    @Override
    public Map<String, List<String>> expand(String queryText) {
        if (!hasText(queryText)) {
            return Map.of();
        }
        try {
            return expandWithRules(queryText, loadRules());
        } catch (Exception ex) {
            LOG.debug("query term expansion failed, fallback to empty result", ex);
            return Map.of();
        }
    }

    private Map<String, List<String>> expandWithRules(String queryText, List<Rule> rules) {
        if (rules.isEmpty()) {
            return Map.of();
        }
        Map<String, LinkedHashSet<String>> matched = new LinkedHashMap<>();
        int expandedCount = 0;
        for (Rule rule : rules) {
            if (expandedCount >= options.maxExpandedTerms()) {
                break;
            }
            if (!isUsable(rule) || !matches(queryText, rule)) {
                continue;
            }
            LinkedHashSet<String> targets = matched.computeIfAbsent(rule.sourceTerm(), ignored -> new LinkedHashSet<>());
            if (targets.add(rule.targetTerm())) {
                expandedCount++;
            }
        }
        if (matched.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        matched.forEach((sourceTerm, targets) -> result.put(sourceTerm, List.copyOf(targets)));
        return result;
    }

    private List<Rule> loadRules() {
        Optional<String> cached = cachePort.get(CACHE_KEY);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), RULES_TYPE);
            } catch (Exception ex) {
                LOG.debug("query term expansion cache is invalid, reloading from jdbc", ex);
            }
        }
        List<Rule> rules = jdbcTemplate.query(SQL_LOAD_RULES, this::mapRule, options.maxRules());
        writeCache(rules);
        return rules;
    }

    private void writeCache(List<Rule> rules) {
        try {
            cachePort.set(CACHE_KEY, objectMapper.writeValueAsString(rules), options.cacheTtl());
        } catch (Exception ex) {
            LOG.debug("query term expansion cache write failed", ex);
        }
    }

    private Rule mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new Rule(
                rs.getString("source_term"),
                rs.getString("target_term"),
                rs.getObject("match_type", Integer.class),
                rs.getObject("priority", Integer.class));
    }

    private boolean isUsable(Rule rule) {
        return hasText(rule.sourceTerm())
                && hasText(rule.targetTerm())
                && rule.sourceTerm().length() <= options.maxSourceTermLength();
    }

    private boolean matches(String queryText, Rule rule) {
        return switch (Objects.requireNonNullElse(rule.matchType(), EXACT_MATCH)) {
            case FUZZY_MATCH -> matchesFuzzy(queryText, rule.sourceTerm());
            case REGEX_MATCH -> options.regexEnabled() && matchesRegex(queryText, rule.sourceTerm());
            default -> matchesExact(queryText, rule.sourceTerm());
        };
    }

    private boolean matchesExact(String queryText, String sourceTerm) {
        return normalize(queryText).contains(normalize(sourceTerm));
    }

    private boolean matchesFuzzy(String queryText, String sourceTerm) {
        String normalizedQuery = normalize(queryText);
        String normalizedSource = normalize(sourceTerm);
        if (normalizedQuery.contains(normalizedSource) || normalizedSource.startsWith(normalizedQuery)) {
            return true;
        }
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.startsWith(normalizedSource) || normalizedSource.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRegex(String queryText, String sourceTerm) {
        try {
            return Pattern.compile(sourceTerm).matcher(queryText).find();
        } catch (PatternSyntaxException ex) {
            LOG.debug("invalid query term regex skipped: sourceTerm={}", sourceTerm, ex);
            return false;
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Options(
            boolean regexEnabled,
            int maxRules,
            int maxExpandedTerms,
            int maxSourceTermLength,
            Duration cacheTtl) {

        private static final int DEFAULT_MAX_RULES = 500;
        private static final int DEFAULT_MAX_EXPANDED_TERMS = 20;
        private static final int DEFAULT_MAX_SOURCE_TERM_LENGTH = 128;
        private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

        public Options {
            maxRules = positiveOrDefault(maxRules, DEFAULT_MAX_RULES);
            maxExpandedTerms = positiveOrDefault(maxExpandedTerms, DEFAULT_MAX_EXPANDED_TERMS);
            maxSourceTermLength = positiveOrDefault(maxSourceTermLength, DEFAULT_MAX_SOURCE_TERM_LENGTH);
            cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                    ? DEFAULT_CACHE_TTL
                    : cacheTtl;
        }

        public static Options defaults() {
            return new Options(false, DEFAULT_MAX_RULES, DEFAULT_MAX_EXPANDED_TERMS,
                    DEFAULT_MAX_SOURCE_TERM_LENGTH, DEFAULT_CACHE_TTL);
        }

        private static int positiveOrDefault(int value, int defaultValue) {
            return value > 0 ? value : defaultValue;
        }
    }

    public record Rule(String sourceTerm, String targetTerm, Integer matchType, Integer priority) {
    }
}
