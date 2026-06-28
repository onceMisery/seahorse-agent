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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;

import java.util.Objects;

public record ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                   MemoryEnginePort memoryEnginePort,
                                   MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                                   MemoryAggregationServicePort memoryAggregationServicePort,
                                   MemoryAggregationPolicy memoryAggregationPolicy,
                                   QueryOptimizerPort queryOptimizerPort,
                                   QueryRewritePort queryRewritePort,
                                   IntentResolutionPort intentResolutionPort,
                                   IntentGuidancePort intentGuidancePort,
                                   RetrievalContextPort retrievalContextPort,
                                   MemoryConflictLogRepositoryPort memoryConflictLogRepositoryPort) {

    public ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                QueryRewritePort queryRewritePort,
                                IntentResolutionPort intentResolutionPort,
                                IntentGuidancePort intentGuidancePort,
                                RetrievalContextPort retrievalContextPort) {
        this(memoryPort, MemoryEnginePort.noop(), command -> MemoryIngestionResult.ignored("noop"),
                MemoryAggregationServicePort.noop(), MemoryAggregationPolicy.defaults(),
                QueryOptimizerPort.passthrough(),
                queryRewritePort, intentResolutionPort, intentGuidancePort, retrievalContextPort,
                MemoryConflictLogRepositoryPort.empty());
    }

    public ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                MemoryEnginePort memoryEnginePort,
                                QueryOptimizerPort queryOptimizerPort,
                                QueryRewritePort queryRewritePort,
                                IntentResolutionPort intentResolutionPort,
                                IntentGuidancePort intentGuidancePort,
                                RetrievalContextPort retrievalContextPort) {
        this(memoryPort, memoryEnginePort, command -> {
            memoryEnginePort.writeMemory(command == null ? null : command.writeRequest());
            return MemoryIngestionResult.ignored("delegated_to_memory_engine");
        }, MemoryAggregationServicePort.noop(), MemoryAggregationPolicy.defaults(),
                queryOptimizerPort, queryRewritePort, intentResolutionPort, intentGuidancePort, retrievalContextPort,
                MemoryConflictLogRepositoryPort.empty());
    }

    public ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                MemoryEnginePort memoryEnginePort,
                                MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                                QueryOptimizerPort queryOptimizerPort,
                                QueryRewritePort queryRewritePort,
                                IntentResolutionPort intentResolutionPort,
                                IntentGuidancePort intentGuidancePort,
                                RetrievalContextPort retrievalContextPort) {
        this(memoryPort, memoryEnginePort, memoryIngestionWorkflowPort,
                MemoryAggregationServicePort.noop(), MemoryAggregationPolicy.defaults(),
                queryOptimizerPort, queryRewritePort, intentResolutionPort, intentGuidancePort, retrievalContextPort,
                MemoryConflictLogRepositoryPort.empty());
    }

    public ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                MemoryEnginePort memoryEnginePort,
                                MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                                MemoryAggregationServicePort memoryAggregationServicePort,
                                MemoryAggregationPolicy memoryAggregationPolicy,
                                QueryOptimizerPort queryOptimizerPort,
                                QueryRewritePort queryRewritePort,
                                IntentResolutionPort intentResolutionPort,
                                IntentGuidancePort intentGuidancePort,
                                RetrievalContextPort retrievalContextPort) {
        this(memoryPort, memoryEnginePort, memoryIngestionWorkflowPort,
                memoryAggregationServicePort, memoryAggregationPolicy,
                queryOptimizerPort, queryRewritePort, intentResolutionPort, intentGuidancePort, retrievalContextPort,
                MemoryConflictLogRepositoryPort.empty());
    }

    public ChatPreparationPorts {
        Objects.requireNonNull(memoryPort, "memoryPort must not be null");
        Objects.requireNonNull(memoryEnginePort, "memoryEnginePort must not be null");
        Objects.requireNonNull(memoryIngestionWorkflowPort, "memoryIngestionWorkflowPort must not be null");
        memoryAggregationServicePort = Objects.requireNonNullElseGet(memoryAggregationServicePort,
                MemoryAggregationServicePort::noop);
        memoryAggregationPolicy = Objects.requireNonNullElseGet(memoryAggregationPolicy,
                MemoryAggregationPolicy::defaults);
        Objects.requireNonNull(queryOptimizerPort, "queryOptimizerPort must not be null");
        Objects.requireNonNull(queryRewritePort, "queryRewritePort must not be null");
        Objects.requireNonNull(intentResolutionPort, "intentResolutionPort must not be null");
        Objects.requireNonNull(intentGuidancePort, "intentGuidancePort must not be null");
        Objects.requireNonNull(retrievalContextPort, "retrievalContextPort must not be null");
        memoryConflictLogRepositoryPort = Objects.requireNonNullElseGet(memoryConflictLogRepositoryPort,
                MemoryConflictLogRepositoryPort::empty);
    }
}
