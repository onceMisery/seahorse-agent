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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import java.time.Duration;
import java.util.Objects;

public record MemoryGarbageCollectionOptions(
        int scanLimit,
        Duration retention,
        boolean dryRun,
        boolean vectorIndexEnabled,
        boolean keywordIndexEnabled,
        boolean graphIndexEnabled,
        boolean archiveEnabled,
        Duration archiveIdleRetention,
        double archiveScoreThreshold
) {

    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(7);
    private static final Duration DEFAULT_ARCHIVE_IDLE_RETENTION = Duration.ofDays(90);
    private static final double DEFAULT_ARCHIVE_SCORE_THRESHOLD = 0.15D;

    public MemoryGarbageCollectionOptions(int scanLimit,
                                          Duration retention,
                                          boolean dryRun,
                                          boolean vectorIndexEnabled,
                                          boolean keywordIndexEnabled,
                                          boolean graphIndexEnabled) {
        this(scanLimit,
                retention,
                dryRun,
                vectorIndexEnabled,
                keywordIndexEnabled,
                graphIndexEnabled,
                false,
                DEFAULT_ARCHIVE_IDLE_RETENTION,
                DEFAULT_ARCHIVE_SCORE_THRESHOLD);
    }

    public MemoryGarbageCollectionOptions {
        scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
        retention = Objects.requireNonNullElse(retention, DEFAULT_RETENTION);
        archiveIdleRetention = Objects.requireNonNullElse(archiveIdleRetention, DEFAULT_ARCHIVE_IDLE_RETENTION);
        archiveScoreThreshold = archiveScoreThreshold <= 0D
                ? DEFAULT_ARCHIVE_SCORE_THRESHOLD
                : Math.min(1D, archiveScoreThreshold);
    }

    public static MemoryGarbageCollectionOptions vectorOnly() {
        return new MemoryGarbageCollectionOptions(
                DEFAULT_SCAN_LIMIT,
                DEFAULT_RETENTION,
                false,
                true,
                false,
                false,
                false,
                DEFAULT_ARCHIVE_IDLE_RETENTION,
                DEFAULT_ARCHIVE_SCORE_THRESHOLD);
    }
}
