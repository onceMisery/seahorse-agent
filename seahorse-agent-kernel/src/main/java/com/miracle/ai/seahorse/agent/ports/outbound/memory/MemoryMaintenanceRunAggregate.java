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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record MemoryMaintenanceRunAggregate(
        int limit,
        int sampleCount,
        int succeededCount,
        int succeededWithWarningsCount,
        int failedCount,
        long compactionScannedTotal,
        long compactionGroupTotal,
        long compactionFragmentTotal,
        long aliasScannedTotal,
        long aliasNormalizedTotal,
        long aliasDictionaryMatchTotal,
        long aliasSkippedTotal,
        long gcScannedTotal,
        long gcEnqueuedTotal,
        long gcMarkedTotal,
        Instant windowStart,
        Instant windowEnd
) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 200;
    public static final int MIN_LIMIT = 1;
    public static final String DEFAULT_LIMIT_LITERAL = "20";

    public MemoryMaintenanceRunAggregate {
        limit = clampLimit(limit);
        sampleCount = Math.max(0, sampleCount);
        succeededCount = Math.max(0, succeededCount);
        succeededWithWarningsCount = Math.max(0, succeededWithWarningsCount);
        failedCount = Math.max(0, failedCount);
        compactionScannedTotal = Math.max(0L, compactionScannedTotal);
        compactionGroupTotal = Math.max(0L, compactionGroupTotal);
        compactionFragmentTotal = Math.max(0L, compactionFragmentTotal);
        aliasScannedTotal = Math.max(0L, aliasScannedTotal);
        aliasNormalizedTotal = Math.max(0L, aliasNormalizedTotal);
        aliasDictionaryMatchTotal = Math.max(0L, aliasDictionaryMatchTotal);
        aliasSkippedTotal = Math.max(0L, aliasSkippedTotal);
        gcScannedTotal = Math.max(0L, gcScannedTotal);
        gcEnqueuedTotal = Math.max(0L, gcEnqueuedTotal);
        gcMarkedTotal = Math.max(0L, gcMarkedTotal);
    }

    public static int clampLimit(int requested) {
        if (requested < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public static MemoryMaintenanceRunAggregate empty(int limit) {
        return new MemoryMaintenanceRunAggregate(
                limit, 0, 0, 0, 0,
                0L, 0L, 0L,
                0L, 0L, 0L, 0L,
                0L, 0L, 0L,
                null, null);
    }

    public static MemoryMaintenanceRunAggregate of(List<MemoryMaintenanceRunRecord> records, int limit) {
        List<MemoryMaintenanceRunRecord> safeRecords = Objects.requireNonNullElse(records, List.of());
        int effectiveLimit = clampLimit(limit);
        if (safeRecords.isEmpty()) {
            return empty(effectiveLimit);
        }
        Accumulator accumulator = new Accumulator();
        for (MemoryMaintenanceRunRecord record : safeRecords) {
            accumulator.add(record);
        }
        return accumulator.toAggregate(effectiveLimit);
    }

    private static final class Accumulator {
        private int sampleCount;
        private int succeededCount;
        private int succeededWithWarningsCount;
        private int failedCount;
        private long compactionScannedTotal;
        private long compactionGroupTotal;
        private long compactionFragmentTotal;
        private long aliasScannedTotal;
        private long aliasNormalizedTotal;
        private long aliasDictionaryMatchTotal;
        private long aliasSkippedTotal;
        private long gcScannedTotal;
        private long gcEnqueuedTotal;
        private long gcMarkedTotal;
        private Instant windowStart;
        private Instant windowEnd;

        private void add(MemoryMaintenanceRunRecord record) {
            if (record == null) {
                return;
            }
            sampleCount++;
            switch (record.status()) {
                case MemoryMaintenanceRunRecord.STATUS_SUCCEEDED -> succeededCount++;
                case MemoryMaintenanceRunRecord.STATUS_SUCCEEDED_WITH_WARNINGS -> succeededWithWarningsCount++;
                case MemoryMaintenanceRunRecord.STATUS_FAILED -> failedCount++;
                default -> { /* unknown statuses are counted into the sample but not in any bucket */ }
            }
            compactionScannedTotal += record.compactionScannedCount();
            compactionGroupTotal += record.compactionGroupCount();
            compactionFragmentTotal += record.compactionFragmentCount();
            aliasScannedTotal += record.aliasScannedCount();
            aliasNormalizedTotal += record.aliasNormalizedCount();
            aliasDictionaryMatchTotal += record.aliasDictionaryMatchCount();
            aliasSkippedTotal += record.aliasSkippedCount();
            gcScannedTotal += record.gcScannedCount();
            gcEnqueuedTotal += record.gcEnqueuedCount();
            gcMarkedTotal += record.gcMarkedCount();
            windowStart = earlier(windowStart, record.createTime());
            windowEnd = later(windowEnd, record.createTime());
        }

        private MemoryMaintenanceRunAggregate toAggregate(int effectiveLimit) {
            return new MemoryMaintenanceRunAggregate(
                    effectiveLimit,
                    sampleCount,
                    succeededCount,
                    succeededWithWarningsCount,
                    failedCount,
                    compactionScannedTotal,
                    compactionGroupTotal,
                    compactionFragmentTotal,
                    aliasScannedTotal,
                    aliasNormalizedTotal,
                    aliasDictionaryMatchTotal,
                    aliasSkippedTotal,
                    gcScannedTotal,
                    gcEnqueuedTotal,
                    gcMarkedTotal,
                    windowStart,
                    windowEnd);
        }

        private static Instant earlier(Instant current, Instant candidate) {
            if (candidate == null) {
                return current;
            }
            if (current == null) {
                return candidate;
            }
            return Comparator.<Instant>naturalOrder().compare(current, candidate) <= 0 ? current : candidate;
        }

        private static Instant later(Instant current, Instant candidate) {
            if (candidate == null) {
                return current;
            }
            if (current == null) {
                return candidate;
            }
            return Comparator.<Instant>naturalOrder().compare(current, candidate) >= 0 ? current : candidate;
        }
    }
}
