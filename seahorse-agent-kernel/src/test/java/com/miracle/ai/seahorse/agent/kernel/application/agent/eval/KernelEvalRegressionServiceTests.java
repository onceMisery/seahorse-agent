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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelEvalRegressionServiceTests {

    @Test
    void runRegressionShouldReturnDimensionsAndBaselineComparison() {
        EvalDatasetQueryPort datasetQueryPort = datasetId -> List.of(
                new EvalSample("sample-1", datasetId, "write report", "report [1]", "bad", "run-1"),
                new EvalSample("sample-2", datasetId, "write summary", "summary [1]", "bad", "run-2"));
        ChatModelPort chatModel = new ChatModelPort() {
            @Override
            public String chat(ChatRequest request, String modelId) {
                String prompt = request.getMessages().get(0).getContent();
                if (prompt.contains("report")) {
                    return "report [1]";
                }
                return "summary without citation";
            }
        };
        KernelEvalRegressionService service = new KernelEvalRegressionService(datasetQueryPort, chatModel);

        KernelEvalRegressionService.EvalReport report = service.runRegression("ds-1", "gpt-4", 0.75d);

        assertEquals(2, report.total());
        assertEquals(1, report.passed());
        assertEquals(0.5d, report.passRate());
        assertEquals(0.75d, report.baseline().baselinePassRate());
        assertEquals(-0.25d, report.baseline().passRateDelta());
        assertEquals(EvalDimension.CITATION_COMPLETENESS, report.dimensions().get(0).dimension());
        assertEquals(EvalDimension.TASK_COMPLETION, report.dimensions().get(1).dimension());
        assertEquals(EvalDimension.ANSWER_QUALITY, report.dimensions().get(2).dimension());
        assertEquals(EvalDimension.SOURCE_GROUNDEDNESS, report.dimensions().get(3).dimension());
        assertEquals(EvalDimension.LATENCY, report.dimensions().get(4).dimension());
        assertEquals(EvalDimension.COST_PER_TASK, report.dimensions().get(5).dimension());
        assertEquals(0.5d, report.dimensions().get(0).score());
        assertTrue(report.dimensions().get(0).automated());
        assertEquals(1.0d, report.dimensions().get(1).score());
        assertFalse(report.dimensions().get(2).automated());
    }
}
