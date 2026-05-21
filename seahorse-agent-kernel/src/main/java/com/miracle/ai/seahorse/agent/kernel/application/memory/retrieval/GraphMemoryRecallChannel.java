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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;

import java.util.List;
import java.util.Objects;

public class GraphMemoryRecallChannel implements MemoryRecallChannelPort {

    public static final String CHANNEL_NAME = "graph";
    private static final int ORDER = 300;
    private static final int DEFAULT_MAX_HOPS = 1;

    private final MemoryGraphPort graphPort;
    private final int maxHops;

    public GraphMemoryRecallChannel(MemoryGraphPort graphPort) {
        this(graphPort, DEFAULT_MAX_HOPS);
    }

    public GraphMemoryRecallChannel(MemoryGraphPort graphPort, int maxHops) {
        this.graphPort = Objects.requireNonNull(graphPort, "graphPort must not be null");
        this.maxHops = maxHops > 0 ? maxHops : DEFAULT_MAX_HOPS;
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
        if (request == null || request.query().isBlank()) {
            return List.of();
        }
        return graphPort.recallNeighborhood(request, maxHops);
    }
}
