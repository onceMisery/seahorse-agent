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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1c：SelfHealingOutputRepairService 行为契约。
 */
class SelfHealingOutputRepairServiceTests {

    @Test
    void returnsRepairedContentWhenPortProducesNonBlankResult() {
        SelfHealingOutputRepairService service = new SelfHealingOutputRepairService(
                staticPort("repaired-content"));

        Optional<String> result = service.repairOnce(request("{\"missing\":1}"), List.of(issue("X")));

        assertThat(result).hasValue("repaired-content");
    }

    @Test
    void returnsEmptyWhenPortReturnsBlankContent() {
        SelfHealingOutputRepairService service = new SelfHealingOutputRepairService(
                staticPort("   "));

        Optional<String> result = service.repairOnce(request("{}"), List.of(issue("X")));

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenPortReturnsNullResult() {
        SelfHealingOutputRepairService service = new SelfHealingOutputRepairService(
                new OutputRepairModelPort() {
                    @Override
                    public String name() {
                        return "null-port";
                    }

                    @Override
                    public OutputRepairResult repair(OutputRepairRequest request) {
                        return null;
                    }
                });

        Optional<String> result = service.repairOnce(request("{}"), List.of(issue("X")));

        assertThat(result).isEmpty();
    }

    @Test
    void swallowsRuntimeExceptionsAndReturnsEmpty() {
        SelfHealingOutputRepairService service = new SelfHealingOutputRepairService(
                new OutputRepairModelPort() {
                    @Override
                    public String name() {
                        return "throwing-port";
                    }

                    @Override
                    public OutputRepairResult repair(OutputRepairRequest request) {
                        throw new IllegalStateException("upstream down");
                    }
                });

        Optional<String> result = service.repairOnce(request("{}"), List.of(issue("X")));

        assertThat(result).isEmpty();
    }

    @Test
    void exposesPortNameForObservation() {
        SelfHealingOutputRepairService service = new SelfHealingOutputRepairService(staticPort("ok"));

        assertThat(service.repairModelName()).isEqualTo("static-port");
    }

    private static OutputRepairModelPort staticPort(String repaired) {
        return new OutputRepairModelPort() {
            @Override
            public String name() {
                return "static-port";
            }

            @Override
            public OutputRepairResult repair(OutputRepairRequest request) {
                return OutputRepairResult.of(repaired);
            }
        };
    }

    private static OutputValidationRequest request(String content) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                OutputArtifactType.JSON,
                "{\"type\":\"object\"}",
                content,
                Map.of());
    }

    private static OutputValidationIssue issue(String code) {
        return new OutputValidationIssue(code, "$.field", "msg", OutputValidationDecision.BLOCK);
    }
}
