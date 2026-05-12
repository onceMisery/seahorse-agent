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

import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardKpi;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardKpiGroup;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrendPoint;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrendSeries;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于旧会话、用户和 Trace 表的 Dashboard 只读 JDBC adapter。
 */
public class JdbcDashboardRepositoryAdapter implements DashboardRepositoryPort {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String NO_DOC_REPLY = "未检索到与问题相关的文档内容。";
    private static final String GRANULARITY_DAY = "day";
    private static final String GRANULARITY_HOUR = "hour";
    private static final long SLOW_LATENCY_THRESHOLD_MS = 20_000L;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDashboardRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public DashboardOverview overview(String window) {
        WindowRange range = resolveWindowRange(window, Duration.ofHours(24));
        long totalUsers = countAll("t_user");
        long usersInWindow = countByCreateTime("t_user", range.start(), range.end());
        long totalSessions = countAll("t_conversation");
        long sessionsInWindow = countByCreateTime("t_conversation", range.start(), range.end());
        long sessionsPrevWindow = countByCreateTime("t_conversation", range.prevStart(), range.prevEnd());
        long totalMessages = countAll("t_message");
        long messagesInWindow = countByCreateTime("t_message", range.start(), range.end());
        long messagesPrevWindow = countByCreateTime("t_message", range.prevStart(), range.prevEnd());
        long activeUsers = countActiveUsers(range.start(), range.end());
        long activeUsersPrev = countActiveUsers(range.prevStart(), range.prevEnd());
        DashboardKpiGroup group = new DashboardKpiGroup(
                buildKpi(totalUsers, usersInWindow, null),
                buildKpi(activeUsers, activeUsers - activeUsersPrev, calcPct(activeUsers, activeUsersPrev)),
                buildKpi(totalSessions, sessionsInWindow, null),
                buildKpi(sessionsInWindow, sessionsInWindow - sessionsPrevWindow,
                        calcPct(sessionsInWindow, sessionsPrevWindow)),
                buildKpi(totalMessages, messagesInWindow, null),
                buildKpi(messagesInWindow, messagesInWindow - messagesPrevWindow,
                        calcPct(messagesInWindow, messagesPrevWindow)));
        return new DashboardOverview(range.windowLabel(), range.compareLabel(), System.currentTimeMillis(), group);
    }

    @Override
    public DashboardPerformance performance(String window) {
        WindowRange range = resolveWindowRange(window, Duration.ofHours(24));
        List<Long> durations = listDurations(range.start(), range.end());
        long success = countTraceRuns(range.start(), range.end(), STATUS_SUCCESS);
        long error = countErrorTraceRuns(range.start(), range.end());
        long total = success + error;
        long assistantCount = countAssistantMessages(range.start(), range.end());
        long noDocCount = countNoDocMessages(range.start(), range.end());
        long slowCount = durations.stream().filter(duration -> duration > SLOW_LATENCY_THRESHOLD_MS).count();
        DashboardPerformance performance = new DashboardPerformance();
        performance.setWindow(range.windowLabel());
        performance.setAvgLatencyMs(average(durations));
        performance.setP95LatencyMs(percentile(durations));
        performance.setSuccessRate(total == 0 ? 0.0 : round1((success * 100.0) / total));
        performance.setErrorRate(total == 0 ? 0.0 : round1((error * 100.0) / total));
        performance.setNoDocRate(assistantCount == 0 ? 0.0 : round1((noDocCount * 100.0) / assistantCount));
        performance.setSlowRate(durations.isEmpty() ? 0.0 : round1((slowCount * 100.0) / durations.size()));
        return performance;
    }

    @Override
    public DashboardTrends trends(String metric, String window, String granularity) {
        String normalizedMetric = metric == null ? "" : metric.trim().toLowerCase();
        Duration windowDuration = parseWindow(window, Duration.ofDays(7));
        WindowRange range = resolveWindowRange(window, Duration.ofDays(7));
        String resolvedGranularity = resolveTrendGranularity(granularity, windowDuration);
        ZoneId zoneId = ZoneId.systemDefault();
        List<TimeBucket> buckets = buildBuckets(range.end(), windowDuration, resolvedGranularity, zoneId);
        List<DashboardTrendSeries> series = buildTrendSeries(normalizedMetric, buckets, resolvedGranularity, zoneId);
        return new DashboardTrends(metric, range.windowLabel(), resolvedGranularity, series);
    }

    private List<DashboardTrendSeries> buildTrendSeries(
            String metric, List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        if ("sessions".equals(metric)) {
            return List.of(new DashboardTrendSeries("会话数",
                    toPoints(countTimestampBuckets("t_conversation", "create_time", buckets, granularity, zoneId),
                            buckets)));
        }
        if ("messages".equals(metric)) {
            return List.of(new DashboardTrendSeries("消息数",
                    toPoints(countTimestampBuckets("t_message", "create_time", buckets, granularity, zoneId), buckets)));
        }
        if ("activeusers".equals(metric)) {
            return List.of(new DashboardTrendSeries("活跃用户",
                    toPoints(countActiveUserBuckets(buckets, granularity, zoneId), buckets)));
        }
        if ("avglatency".equals(metric)) {
            return List.of(new DashboardTrendSeries("平均响应时间",
                    toPoints(averageLatencyBuckets(buckets, granularity, zoneId), buckets)));
        }
        if ("quality".equals(metric)) {
            return buildQualitySeries(buckets, granularity, zoneId);
        }
        return List.of();
    }

    private List<DashboardTrendSeries> buildQualitySeries(List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        Map<Long, Double> errorRate = new HashMap<>();
        Map<Long, Double> noDocRate = new HashMap<>();
        Map<Long, Long> successMap = countTraceBuckets(buckets, granularity, zoneId, STATUS_SUCCESS);
        Map<Long, Long> errorMap = mergeBuckets(
                countTraceBuckets(buckets, granularity, zoneId, STATUS_ERROR),
                countTraceBuckets(buckets, granularity, zoneId, STATUS_FAILED));
        Map<Long, Long> assistantMap = countAssistantBuckets(buckets, granularity, zoneId);
        Map<Long, Long> noDocMap = countNoDocBuckets(buckets, granularity, zoneId);
        for (TimeBucket bucket : buckets) {
            long success = successMap.getOrDefault(bucket.ts(), 0L);
            long error = errorMap.getOrDefault(bucket.ts(), 0L);
            long assistant = assistantMap.getOrDefault(bucket.ts(), 0L);
            long noDoc = noDocMap.getOrDefault(bucket.ts(), 0L);
            errorRate.put(bucket.ts(), success + error == 0L ? 0.0 : round1((error * 100.0) / (success + error)));
            noDocRate.put(bucket.ts(), assistant == 0L ? 0.0 : round1((noDoc * 100.0) / assistant));
        }
        return List.of(
                new DashboardTrendSeries("错误率", toPoints(errorRate, buckets)),
                new DashboardTrendSeries("无知识率", toPoints(noDocRate, buckets)));
    }

    private long countAll(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table + " WHERE deleted = 0", Long.class);
        return value == null ? 0L : value;
    }

    private long countByCreateTime(String table, Instant start, Instant end) {
        return count("""
                SELECT COUNT(1)
                FROM %s
                WHERE deleted = 0 AND create_time >= ? AND create_time < ?
                """.formatted(table), start, end);
    }

    private long countActiveUsers(Instant start, Instant end) {
        return count("""
                SELECT COUNT(DISTINCT user_id)
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ?
                """, start, end);
    }

    private long countTraceRuns(Instant start, Instant end, String status) {
        return count("""
                SELECT COUNT(1)
                FROM t_rag_trace_run
                WHERE deleted = 0 AND start_time >= ? AND start_time < ? AND status = ?
                """, start, end, status);
    }

    private long countErrorTraceRuns(Instant start, Instant end) {
        return countTraceRuns(start, end, STATUS_ERROR) + countTraceRuns(start, end, STATUS_FAILED);
    }

    private long countAssistantMessages(Instant start, Instant end) {
        return count("""
                SELECT COUNT(1)
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ? AND role = ?
                """, start, end, ROLE_ASSISTANT);
    }

    private long countNoDocMessages(Instant start, Instant end) {
        return count("""
                SELECT COUNT(1)
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ? AND role = ? AND content = ?
                """, start, end, ROLE_ASSISTANT, NO_DOC_REPLY);
    }

    private List<Long> listDurations(Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT duration_ms
                FROM t_rag_trace_run
                WHERE deleted = 0 AND start_time >= ? AND start_time < ? AND status = ? AND duration_ms > 0
                """, (resultSet, rowNum) -> resultSet.getLong("duration_ms"),
                Timestamp.from(start), Timestamp.from(end), STATUS_SUCCESS);
    }

    private Map<Long, Double> averageLatencyBuckets(List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        Map<Long, List<Long>> grouped = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT start_time, duration_ms
                FROM t_rag_trace_run
                WHERE deleted = 0 AND start_time >= ? AND start_time < ? AND status = ? AND duration_ms > 0
                """, Timestamp.from(buckets.get(0).start()), Timestamp.from(lastEnd(buckets)), STATUS_SUCCESS);
        for (Map<String, Object> row : rows) {
            Long key = bucketKey(row.get("start_time"), granularity, zoneId);
            Long duration = toLong(row.get("duration_ms"));
            if (key != null && duration != null) {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(duration);
            }
        }
        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), (double) average(entry.getValue()));
        }
        return result;
    }

    private Map<Long, Long> countTimestampBuckets(
            String table, String column, List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        Map<Long, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s AS ts
                FROM %s
                WHERE deleted = 0 AND %s >= ? AND %s < ?
                """.formatted(column, table, column, column),
                Timestamp.from(buckets.get(0).start()), Timestamp.from(lastEnd(buckets)));
        for (Map<String, Object> row : rows) {
            Long key = bucketKey(row.get("ts"), granularity, zoneId);
            if (key != null) {
                result.merge(key, 1L, Long::sum);
            }
        }
        return result;
    }

    private Map<Long, Long> countActiveUserBuckets(List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        Map<Long, Set<String>> grouped = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT create_time, user_id
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ?
                """, Timestamp.from(buckets.get(0).start()), Timestamp.from(lastEnd(buckets)));
        for (Map<String, Object> row : rows) {
            Long key = bucketKey(row.get("create_time"), granularity, zoneId);
            Object userId = row.get("user_id");
            if (key != null && userId != null) {
                grouped.computeIfAbsent(key, ignored -> new HashSet<>()).add(String.valueOf(userId));
            }
        }
        Map<Long, Long> result = new HashMap<>();
        grouped.forEach((key, users) -> result.put(key, (long) users.size()));
        return result;
    }

    private Map<Long, Long> countTraceBuckets(List<TimeBucket> buckets, String granularity, ZoneId zoneId, String status) {
        return countFilteredBuckets("""
                SELECT start_time AS ts
                FROM t_rag_trace_run
                WHERE deleted = 0 AND start_time >= ? AND start_time < ? AND status = ?
                """, buckets, granularity, zoneId, status);
    }

    private Map<Long, Long> countAssistantBuckets(List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        return countFilteredBuckets("""
                SELECT create_time AS ts
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ? AND role = ?
                """, buckets, granularity, zoneId, ROLE_ASSISTANT);
    }

    private Map<Long, Long> countNoDocBuckets(List<TimeBucket> buckets, String granularity, ZoneId zoneId) {
        Map<Long, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT create_time AS ts
                FROM t_message
                WHERE deleted = 0 AND create_time >= ? AND create_time < ? AND role = ? AND content = ?
                """, Timestamp.from(buckets.get(0).start()), Timestamp.from(lastEnd(buckets)),
                ROLE_ASSISTANT, NO_DOC_REPLY);
        for (Map<String, Object> row : rows) {
            Long key = bucketKey(row.get("ts"), granularity, zoneId);
            if (key != null) {
                result.merge(key, 1L, Long::sum);
            }
        }
        return result;
    }

    private Map<Long, Long> countFilteredBuckets(
            String sql, List<TimeBucket> buckets, String granularity, ZoneId zoneId, String value) {
        Map<Long, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                Timestamp.from(buckets.get(0).start()), Timestamp.from(lastEnd(buckets)), value);
        for (Map<String, Object> row : rows) {
            Long key = bucketKey(row.get("ts"), granularity, zoneId);
            if (key != null) {
                result.merge(key, 1L, Long::sum);
            }
        }
        return result;
    }

    private Map<Long, Long> mergeBuckets(Map<Long, Long> first, Map<Long, Long> second) {
        Map<Long, Long> result = new HashMap<>(first);
        second.forEach((key, value) -> result.merge(key, value, Long::sum));
        return result;
    }

    private List<DashboardTrendPoint> toPoints(Map<Long, ? extends Number> values, List<TimeBucket> buckets) {
        List<DashboardTrendPoint> points = new ArrayList<>();
        for (TimeBucket bucket : buckets) {
            Number value = values.containsKey(bucket.ts()) ? values.get(bucket.ts()) : 0;
            points.add(new DashboardTrendPoint(bucket.ts(), value.doubleValue()));
        }
        return points;
    }

    private List<TimeBucket> buildBuckets(Instant end, Duration duration, String granularity, ZoneId zoneId) {
        if (GRANULARITY_HOUR.equals(granularity)) {
            LocalDateTime endHour = LocalDateTime.ofInstant(end, zoneId).truncatedTo(ChronoUnit.HOURS).plusHours(1);
            LocalDateTime startHour = endHour.minusHours(Math.max(1L, duration.toHours()));
            return buildHourBuckets(startHour, endHour, zoneId);
        }
        LocalDateTime endDay = LocalDateTime.ofInstant(end, zoneId).truncatedTo(ChronoUnit.DAYS).plusDays(1);
        LocalDateTime startDay = LocalDateTime.ofInstant(end.minus(duration), zoneId).truncatedTo(ChronoUnit.DAYS);
        return buildDayBuckets(startDay, endDay, zoneId);
    }

    private List<TimeBucket> buildHourBuckets(LocalDateTime start, LocalDateTime end, ZoneId zoneId) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(end); cursor = cursor.plusHours(1)) {
            Instant bucketStart = cursor.atZone(zoneId).toInstant();
            buckets.add(new TimeBucket(bucketStart, cursor.plusHours(1).atZone(zoneId).toInstant(),
                    bucketStart.toEpochMilli()));
        }
        return buckets;
    }

    private List<TimeBucket> buildDayBuckets(LocalDateTime start, LocalDateTime end, ZoneId zoneId) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(end); cursor = cursor.plusDays(1)) {
            Instant bucketStart = cursor.atZone(zoneId).toInstant();
            buckets.add(new TimeBucket(bucketStart, cursor.plusDays(1).atZone(zoneId).toInstant(),
                    bucketStart.toEpochMilli()));
        }
        return buckets;
    }

    private Long bucketKey(Object value, String granularity, ZoneId zoneId) {
        if (!(value instanceof Timestamp timestamp)) {
            return null;
        }
        LocalDateTime time = LocalDateTime.ofInstant(timestamp.toInstant(), zoneId);
        LocalDateTime bucket = GRANULARITY_HOUR.equals(granularity)
                ? time.truncatedTo(ChronoUnit.HOURS)
                : time.truncatedTo(ChronoUnit.DAYS);
        return bucket.atZone(zoneId).toInstant().toEpochMilli();
    }

    private long count(String sql, Instant start, Instant end) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(start), Timestamp.from(end));
        return value == null ? 0L : value;
    }

    private long count(String sql, Instant start, Instant end, String value) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(start), Timestamp.from(end), value);
        return count == null ? 0L : count;
    }

    private long count(String sql, Instant start, Instant end, String first, String second) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                Timestamp.from(start), Timestamp.from(end), first, second);
        return count == null ? 0L : count;
    }

    private DashboardKpi buildKpi(long value, long delta, Double deltaPct) {
        return new DashboardKpi(value, delta, deltaPct);
    }

    private Double calcPct(long current, long prev) {
        if (prev <= 0L) {
            return null;
        }
        return round1(((current - prev) * 100.0) / prev);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (Long value : values) {
            sum += value;
        }
        return Math.round(sum / (double) values.size());
    }

    private long percentile(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private WindowRange resolveWindowRange(String window, Duration fallback) {
        Duration duration = parseWindow(window, fallback);
        Instant end = Instant.now();
        Instant start = end.minus(duration);
        String label = window == null || window.isBlank() ? formatDuration(fallback) : window;
        return new WindowRange(start, end, start.minus(duration), start, label, "prev_" + label);
    }

    private Duration parseWindow(String window, Duration fallback) {
        if (window == null || window.isBlank()) {
            return fallback;
        }
        String normalized = window.trim().toLowerCase();
        if (normalized.endsWith("h")) {
            return Duration.ofHours(parseNumber(normalized.substring(0, normalized.length() - 1), fallback.toHours()));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(parseNumber(normalized.substring(0, normalized.length() - 1), fallback.toDays()));
        }
        return fallback;
    }

    private String resolveTrendGranularity(String granularity, Duration windowDuration) {
        if (granularity != null && !granularity.isBlank()) {
            String normalized = granularity.trim().toLowerCase();
            if (GRANULARITY_HOUR.equals(normalized) || GRANULARITY_DAY.equals(normalized)) {
                return normalized;
            }
        }
        return windowDuration.toHours() <= 48L ? GRANULARITY_HOUR : GRANULARITY_DAY;
    }

    private long parseNumber(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours % 24L == 0L) {
            return (hours / 24L) + "d";
        }
        return hours + "h";
    }

    private Instant lastEnd(List<TimeBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return Instant.now();
        }
        return buckets.get(buckets.size() - 1).end();
    }

    private record TimeBucket(Instant start, Instant end, long ts) {
    }

    private record WindowRange(
            Instant start,
            Instant end,
            Instant prevStart,
            Instant prevEnd,
            String windowLabel,
            String compareLabel) {
    }
}
