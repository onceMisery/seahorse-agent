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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseProfile;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves named golden case profiles and delegates evaluation to {@link
 * MemoryRecallEvaluationInboundPort}.
 *
 * <p>The harness is intentionally thin: profile lookup happens in the repository port, scoring
 * happens in the evaluation service. The service adds observation hooks that distinguish three
 * outcomes — success / missing-profile / empty — so dashboards can alert when CI runs a
 * profile that has gone stale or vanished.
 */
public class MemoryRecallGoldenHarnessService implements MemoryRecallGoldenHarnessInboundPort {

    static final String OBSERVATION_RUN_EVENT = "memory-recall-harness-run";
    static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    static final String OBSERVATION_ATTR_PROFILE = "profile";
    static final String OBSERVATION_OUTCOME_SUCCESS = "success";
    static final String OBSERVATION_OUTCOME_MISSING = "missing";
    static final String OBSERVATION_OUTCOME_EMPTY = "empty";

    private final MemoryRecallGoldenCaseRepositoryPort repositoryPort;
    private final MemoryRecallEvaluationInboundPort evaluationPort;
    private final ObservationPort observationPort;

    public MemoryRecallGoldenHarnessService(MemoryRecallGoldenCaseRepositoryPort repositoryPort,
                                            MemoryRecallEvaluationInboundPort evaluationPort) {
        this(repositoryPort, evaluationPort, ObservationPort.noop());
    }

    public MemoryRecallGoldenHarnessService(MemoryRecallGoldenCaseRepositoryPort repositoryPort,
                                            MemoryRecallEvaluationInboundPort evaluationPort,
                                            ObservationPort observationPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.evaluationPort = Objects.requireNonNull(evaluationPort, "evaluationPort must not be null");
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
    }

    @Override
    public MemoryRecallEvaluationReport runProfile(String profileName) {
        String normalized = profileName == null ? "" : profileName.trim();
        Optional<MemoryRecallGoldenCaseProfile> profile = repositoryPort.findByName(normalized);
        if (profile.isEmpty()) {
            emitHarnessMetric(normalized, OBSERVATION_OUTCOME_MISSING);
            return emptyReport();
        }
        MemoryRecallGoldenCaseProfile resolved = profile.get();
        if (resolved.isEmpty()) {
            emitHarnessMetric(resolved.name(), OBSERVATION_OUTCOME_EMPTY);
            return emptyReport();
        }
        MemoryRecallEvaluationReport report = evaluationPort.evaluate(
                new MemoryRecallEvaluationCommand(resolved.topK(), resolved.cases()));
        emitHarnessMetric(resolved.name(), OBSERVATION_OUTCOME_SUCCESS);
        return report;
    }

    @Override
    public List<String> listProfiles() {
        List<String> names = repositoryPort.listProfileNames();
        return names == null ? List.of() : List.copyOf(names);
    }

    private void emitHarnessMetric(String profileName, String outcome) {
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put(OBSERVATION_ATTR_PROFILE, profileName);
            attributes.put(OBSERVATION_ATTR_OUTCOME, outcome);
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_RUN_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Collections.unmodifiableMap(attributes)));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change harness semantics.
        }
    }

    private MemoryRecallEvaluationReport emptyReport() {
        return new MemoryRecallEvaluationReport(0, 0, 0, 0D, 0D, 0D, 0D, 0D, List.of(), Map.of());
    }
}
