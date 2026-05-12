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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;

import java.util.Objects;

public record MemoryManagementServicePorts(
        WorkingMemoryPort workingMemoryPort,
        ShortTermMemoryPort shortTermMemoryPort,
        LongTermMemoryPort longTermMemoryPort,
        SemanticMemoryPort semanticMemoryPort,
        MemoryQualitySnapshotRepositoryPort qualitySnapshotRepositoryPort,
        MemoryConflictLogRepositoryPort conflictLogRepositoryPort
) {

    public MemoryManagementServicePorts {
        Objects.requireNonNull(workingMemoryPort, "workingMemoryPort must not be null");
        Objects.requireNonNull(shortTermMemoryPort, "shortTermMemoryPort must not be null");
        Objects.requireNonNull(longTermMemoryPort, "longTermMemoryPort must not be null");
        Objects.requireNonNull(semanticMemoryPort, "semanticMemoryPort must not be null");
        Objects.requireNonNull(qualitySnapshotRepositoryPort, "qualitySnapshotRepositoryPort must not be null");
        Objects.requireNonNull(conflictLogRepositoryPort, "conflictLogRepositoryPort must not be null");
    }
}
