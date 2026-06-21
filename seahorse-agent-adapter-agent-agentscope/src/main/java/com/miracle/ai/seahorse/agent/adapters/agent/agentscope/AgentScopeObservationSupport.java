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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AgentScopeObservationSupport {

    private final ObservationPort observationPort;

    public AgentScopeObservationSupport(ObservationPort observationPort) {
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
    }

    public static AgentScopeObservationSupport noop() {
        return new AgentScopeObservationSupport(ObservationPort.noop());
    }

    public ObservationScope start(String name, String tenantId, Map<String, String> attributes) {
        return observationPort.start(new ObservationCommand(name, tenantId, clean(attributes)));
    }

    public void event(String name, Map<String, String> attributes) {
        observationPort.recordEvent(new ObservationEvent(name, Instant.now(), clean(attributes)));
    }

    public Map<String, String> attributes(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1];
            if (key != null && !key.isBlank() && value != null) {
                result.put(key, value);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, String> clean(Map<String, String> attributes) {
        Map<String, String> result = new LinkedHashMap<>();
        Objects.requireNonNullElse(attributes, Map.<String, String>of()).forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }
}
