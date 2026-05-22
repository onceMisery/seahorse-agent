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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface MemoryGarbageCollectionPort {

    List<MemoryGarbageCollectionCandidate> scanDerivedIndexDeleteCandidates(
            Instant now,
            Duration retention,
            int limit);

    default List<MemoryGarbageCollectionCandidate> scanLifecycleArchiveCandidates(
            Instant now,
            Duration idleRetention,
            double scoreThreshold,
            int limit) {
        return List.of();
    }

    default int markArchived(List<String> memoryIds, Instant archivedAt, String reason) {
        return 0;
    }

    int markDerivedIndexesDeleted(List<String> memoryIds, Instant markedAt);

    static MemoryGarbageCollectionPort noop() {
        return new MemoryGarbageCollectionPort() {
            @Override
            public List<MemoryGarbageCollectionCandidate> scanDerivedIndexDeleteCandidates(
                    Instant now,
                    Duration retention,
                    int limit) {
                return List.of();
            }

            @Override
            public int markDerivedIndexesDeleted(List<String> memoryIds, Instant markedAt) {
                return 0;
            }
        };
    }
}
