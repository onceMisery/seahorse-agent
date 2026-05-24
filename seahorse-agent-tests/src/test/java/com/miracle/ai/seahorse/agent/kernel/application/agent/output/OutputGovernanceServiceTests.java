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

package com.miracle.ai.seahorse.agent.kernel.application.agent.output;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1a：OutputGovernanceService 行为契约。
 */
class OutputGovernanceServiceTests {

    @Test
    void returnsPassWhenNoValidatorSupportsRequest() {
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);

        OutputGovernanceResult result = service.governFinalAnswer(plainTextRequest("hello world"));

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.governedContent()).isEqualTo("hello world");
        assertThat(observation.events)
                .extracting(ObservationEvent::name)
                .containsExactly(OutputGovernanceService.OBSERVATION_VALIDATION_EVENT);
    }

    @Test
    void emitsValidationEventOnSuccessfulJsonValidation() {
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);

        OutputGovernanceResult result = service.governFinalAnswer(jsonRequest(
                "{\"type\":\"object\",\"required\":[\"title\"]}",
                "{\"title\":\"ok\"}"));

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(observation.events).hasSize(1);
        ObservationEvent event = observation.events.get(0);
        assertThat(event.name()).isEqualTo(OutputGovernanceService.OBSERVATION_VALIDATION_EVENT);
        assertThat(event.attributes())
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_ARTIFACT_TYPE, "JSON")
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_DECISION, "PASS")
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_VALIDATOR,
                        JsonSchemaOutputValidator.VALIDATOR_NAME);
    }

    @Test
    void blocksOnMissingRequiredFieldAndEmitsFailedEvent() {
        RecordingObservation observation = new RecordingObservation();
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                OutputValidationRecordPort.noop(),
                observation);

        OutputGovernanceResult result = service.governFinalAnswer(jsonRequest(
                "{\"type\":\"object\",\"required\":[\"title\",\"steps\"]}",
                "{\"title\":\"missing steps\"}"));

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.blocked()).isTrue();
        assertThat(result.governedContent()).isNotEqualTo(result.originalContent());
        assertThat(result.governedContent()).contains("blocked by governance");

        assertThat(observation.events)
                .extracting(ObservationEvent::name)
                .containsExactly(
                        OutputGovernanceService.OBSERVATION_VALIDATION_EVENT,
                        OutputGovernanceService.OBSERVATION_VALIDATION_FAILED_EVENT);
        ObservationEvent failed = observation.events.get(1);
        assertThat(failed.attributes())
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_ISSUE_CODE,
                        JsonSchemaOutputValidator.CODE_JSON_REQUIRED_FIELD_MISSING)
                .containsEntry(OutputGovernanceService.OBSERVATION_ATTR_DECISION, "BLOCK");
    }

    @Test
    void recordsRunRegardlessOfDecision() {
        RecordingRecordPort records = new RecordingRecordPort();
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(new JsonSchemaOutputValidator()),
                records,
                ObservationPort.noop());

        service.governFinalAnswer(jsonRequest(
                "{\"type\":\"object\",\"required\":[\"title\"]}",
                "{\"other\":1}"));
        service.governFinalAnswer(jsonRequest(
                "{\"type\":\"object\",\"required\":[\"title\"]}",
                "{\"title\":\"ok\"}"));

        assertThat(records.invocations.get()).isEqualTo(2);
    }

    @Test
    void validatorRuntimeFailureIsContainedAndDoesNotBlock() {
        RecordingObservation observation = new RecordingObservation();
        OutputValidatorPort exploding = new OutputValidatorPort() {
            @Override
            public String name() {
                return "exploding";
            }

            @Override
            public boolean supports(OutputValidationRequest request) {
                return true;
            }

            @Override
            public OutputValidationResult validate(OutputValidationRequest request) {
                throw new IllegalStateException("boom");
            }
        };
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(exploding),
                OutputValidationRecordPort.noop(),
                observation);

        OutputGovernanceResult result = service.governFinalAnswer(plainTextRequest("hello"));

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.WARN);
        assertThat(result.governedContent()).isEqualTo("hello");
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .contains("VALIDATOR_RUNTIME_FAILURE");
    }

    @Test
    void aggregatesIssuesFromMultipleApplicableValidators() {
        RecordingObservation observation = new RecordingObservation();
        OutputValidatorPort firstWarn = warningValidator("first-validator", "FIRST_WARN");
        OutputValidatorPort secondWarn = warningValidator("second-validator", "SECOND_WARN");
        OutputGovernanceService service = new OutputGovernanceService(
                List.of(firstWarn, secondWarn),
                OutputValidationRecordPort.noop(),
                observation);

        OutputGovernanceResult result = service.governFinalAnswer(plainTextRequest("hello"));

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.WARN);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly("FIRST_WARN", "SECOND_WARN");
        assertThat(result.governedContent()).isEqualTo("hello");
    }

    private static OutputValidatorPort warningValidator(String name, String code) {
        return new OutputValidatorPort() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean supports(OutputValidationRequest request) {
                return true;
            }

            @Override
            public OutputValidationResult validate(OutputValidationRequest request) {
                return OutputValidationResult.warn(List.of(new OutputValidationIssue(
                        code, "", "issue from " + name, OutputValidationDecision.WARN)));
            }
        };
    }

    private static OutputValidationRequest plainTextRequest(String content) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                OutputArtifactType.PLAIN_TEXT,
                null,
                content,
                Map.of());
    }

    private static OutputValidationRequest jsonRequest(String schemaJson, String content) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                OutputArtifactType.JSON,
                schemaJson,
                content,
                Map.of());
    }

    private static final class RecordingObservation implements ObservationPort {
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
                    // no-op
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingRecordPort implements OutputValidationRecordPort {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void record(OutputValidationRequest request, OutputGovernanceResult result) {
            invocations.incrementAndGet();
        }
    }
}
