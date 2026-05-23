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
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenCase;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseProfile;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRecallGoldenHarnessServiceTests {

    @Test
    void shouldRunProfileAndDelegateToEvaluationPort() {
        RecordingEvaluationPort evaluationPort = new RecordingEvaluationPort(report(2, 2, 1.0D));
        StaticRepository repository = new StaticRepository(Map.of(
                "smoke", new MemoryRecallGoldenCaseProfile("smoke", 5, List.of(
                        new MemoryRecallGoldenCase("case-1", "user-1", "conv-1", "q1", List.of("mem-1")),
                        new MemoryRecallGoldenCase("case-2", "user-1", "conv-1", "q2", List.of("mem-2"))))));
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(repository, evaluationPort);

        MemoryRecallEvaluationReport report = service.runProfile("smoke");

        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(evaluationPort.captured).hasSize(1);
        MemoryRecallEvaluationCommand command = evaluationPort.captured.get(0);
        assertThat(command.topK()).isEqualTo(5);
        assertThat(command.cases()).hasSize(2);
    }

    @Test
    void shouldReturnEmptyReportWhenProfileIsMissing() {
        RecordingEvaluationPort evaluationPort = new RecordingEvaluationPort(report(1, 1, 1.0D));
        StaticRepository repository = new StaticRepository(Map.of());
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(repository, evaluationPort);

        MemoryRecallEvaluationReport report = service.runProfile("unknown");

        assertThat(report.caseCount()).isZero();
        assertThat(evaluationPort.captured).isEmpty();
    }

    @Test
    void shouldReturnEmptyReportWhenProfileHasNoCases() {
        RecordingEvaluationPort evaluationPort = new RecordingEvaluationPort(report(1, 1, 1.0D));
        StaticRepository repository = new StaticRepository(Map.of(
                "empty", new MemoryRecallGoldenCaseProfile("empty", 5, List.of())));
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(repository, evaluationPort);

        MemoryRecallEvaluationReport report = service.runProfile("empty");

        assertThat(report.caseCount()).isZero();
        assertThat(evaluationPort.captured).isEmpty();
    }

    @Test
    void shouldEmitObservationEventTaggedWithOutcomeAndProfile() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        RecordingEvaluationPort evaluationPort = new RecordingEvaluationPort(report(1, 1, 1.0D));
        StaticRepository repository = new StaticRepository(Map.of(
                "smoke", new MemoryRecallGoldenCaseProfile("smoke", 5, List.of(
                        new MemoryRecallGoldenCase("case-1", "u", "c", "q", List.of("mem-1"))))));
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(
                repository, evaluationPort, observationPort);

        service.runProfile("smoke");

        assertThat(observationPort.events).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo(MemoryRecallGoldenHarnessService.OBSERVATION_RUN_EVENT);
            assertThat(event.attributes())
                    .containsEntry(MemoryRecallGoldenHarnessService.OBSERVATION_ATTR_OUTCOME,
                            MemoryRecallGoldenHarnessService.OBSERVATION_OUTCOME_SUCCESS)
                    .containsEntry(MemoryRecallGoldenHarnessService.OBSERVATION_ATTR_PROFILE, "smoke");
        });
    }

    @Test
    void shouldEmitMissingOutcomeObservationWhenProfileNotFound() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(
                new StaticRepository(Map.of()), new RecordingEvaluationPort(report(0, 0, 0D)), observationPort);

        service.runProfile("unknown");

        assertThat(observationPort.events).singleElement().satisfies(event -> assertThat(event.attributes())
                .containsEntry(MemoryRecallGoldenHarnessService.OBSERVATION_ATTR_OUTCOME,
                        MemoryRecallGoldenHarnessService.OBSERVATION_OUTCOME_MISSING));
    }

    @Test
    void shouldEmitEmptyOutcomeObservationWhenProfileExistsButHasNoCases() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        StaticRepository repository = new StaticRepository(Map.of(
                "empty", new MemoryRecallGoldenCaseProfile("empty", 5, List.of())));
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(
                repository, new RecordingEvaluationPort(report(0, 0, 0D)), observationPort);

        service.runProfile("empty");

        assertThat(observationPort.events).singleElement().satisfies(event -> assertThat(event.attributes())
                .containsEntry(MemoryRecallGoldenHarnessService.OBSERVATION_ATTR_OUTCOME,
                        MemoryRecallGoldenHarnessService.OBSERVATION_OUTCOME_EMPTY));
    }

    @Test
    void shouldListProfileNamesFromRepository() {
        StaticRepository repository = new StaticRepository(Map.of(
                "alpha", new MemoryRecallGoldenCaseProfile("alpha", 5, List.of()),
                "beta", new MemoryRecallGoldenCaseProfile("beta", 5, List.of())));
        MemoryRecallGoldenHarnessService service = new MemoryRecallGoldenHarnessService(
                repository, new RecordingEvaluationPort(report(0, 0, 0D)));

        assertThat(service.listProfiles()).containsExactlyInAnyOrder("alpha", "beta");
    }

    private MemoryRecallEvaluationReport report(int caseCount, int hitCount, double hitRate) {
        return new MemoryRecallEvaluationReport(
                caseCount, caseCount, hitCount, hitRate, hitRate, hitRate, hitRate, 0D,
                List.<MemoryRecallEvaluationResult>of());
    }

    private static final class StaticRepository implements MemoryRecallGoldenCaseRepositoryPort {

        private final Map<String, MemoryRecallGoldenCaseProfile> profiles;

        StaticRepository(Map<String, MemoryRecallGoldenCaseProfile> profiles) {
            this.profiles = Map.copyOf(profiles);
        }

        @Override
        public Optional<MemoryRecallGoldenCaseProfile> findByName(String profileName) {
            return Optional.ofNullable(profiles.get(profileName));
        }

        @Override
        public List<String> listProfileNames() {
            return List.copyOf(profiles.keySet());
        }
    }

    private static final class RecordingEvaluationPort implements MemoryRecallEvaluationInboundPort {

        private final MemoryRecallEvaluationReport stub;
        private final List<MemoryRecallEvaluationCommand> captured = new ArrayList<>();

        RecordingEvaluationPort(MemoryRecallEvaluationReport stub) {
            this.stub = stub;
        }

        @Override
        public MemoryRecallEvaluationReport evaluate(MemoryRecallEvaluationCommand command) {
            captured.add(command);
            return stub;
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
