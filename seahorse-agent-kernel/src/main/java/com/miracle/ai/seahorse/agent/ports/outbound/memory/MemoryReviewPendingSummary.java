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

public record MemoryReviewPendingSummary(
        long pendingCount,
        boolean hasPending,
        MemoryReviewRecord latestPendingCandidate
) {

    public MemoryReviewPendingSummary {
        pendingCount = Math.max(0L, pendingCount);
        hasPending = pendingCount > 0L;
    }

    public MemoryReviewPendingSummary(long pendingCount, MemoryReviewRecord latestPendingCandidate) {
        this(pendingCount, pendingCount > 0L, latestPendingCandidate);
    }

    public boolean hasPending() {
        return hasPending;
    }

    public static MemoryReviewPendingSummary empty() {
        return new MemoryReviewPendingSummary(0L, false, null);
    }
}
