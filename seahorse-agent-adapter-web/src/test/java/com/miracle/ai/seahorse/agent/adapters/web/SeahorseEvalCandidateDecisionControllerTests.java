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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalCandidateDecisionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService.EvalReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseEvalCandidateDecisionControllerTests {

    private final KernelEvalCandidateDecisionService decisionService =
            mock(KernelEvalCandidateDecisionService.class);
    private final KernelEvalRegressionService regressionService =
            mock(KernelEvalRegressionService.class);

    @Test
    void shouldAcceptCandidate() throws Exception {
        MockMvc mvc = buildMvc(AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(post("/api/eval-candidates/cand-1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.candidateId").value("cand-1"))
                .andExpect(jsonPath("$.data.decision").value("ACCEPTED"));

        verify(decisionService).acceptCandidate("cand-1", "looks good");
    }

    @Test
    void shouldRejectCandidate() throws Exception {
        MockMvc mvc = buildMvc(AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(post("/api/eval-candidates/cand-2/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"not relevant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.candidateId").value("cand-2"))
                .andExpect(jsonPath("$.data.decision").value("REJECTED"));

        verify(decisionService).rejectCandidate("cand-2", "not relevant");
    }

    @Test
    void shouldRunRegression() throws Exception {
        EvalReport report = EvalReport.aggregate("ds-1", "gpt-4", List.of(), 0.8d);
        when(regressionService.runRegression("ds-1", "gpt-4", 0.8d)).thenReturn(report);
        MockMvc mvc = buildMvc(AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(post("/api/eval-datasets/ds-1/regression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\": \"gpt-4\", \"baselinePassRate\": 0.8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.datasetId").value("ds-1"))
                .andExpect(jsonPath("$.data.modelId").value("gpt-4"))
                .andExpect(jsonPath("$.data.baseline.baselinePassRate").value(0.8))
                .andExpect(jsonPath("$.data.dimensions[0].dimension").value("CITATION_COMPLETENESS"));

        verify(regressionService).runRegression("ds-1", "gpt-4", 0.8d);
    }

    @Test
    void shouldRejectWhenFeatureGateDisabled() throws Exception {
        MockMvc mvc = buildMvc(AdvancedFeatureGate.consumerWebDefaults());

        mvc.perform(post("/api/eval-candidates/cand-1/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));

        verifyNoInteractions(decisionService);
    }

    private MockMvc buildMvc(AdvancedFeatureGate gate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("decisionService", decisionService);
        beanFactory.addBean("regressionService", regressionService);
        ObjectProvider<KernelEvalCandidateDecisionService> decisionProvider =
                beanFactory.getBeanProvider(KernelEvalCandidateDecisionService.class);
        ObjectProvider<KernelEvalRegressionService> regressionProvider =
                beanFactory.getBeanProvider(KernelEvalRegressionService.class);
        StaticListableBeanFactory gateFactory = new StaticListableBeanFactory();
        gateFactory.addBean("gate", gate);
        ObjectProvider<AdvancedFeatureGate> gateProvider =
                gateFactory.getBeanProvider(AdvancedFeatureGate.class);
        return MockMvcBuilders.standaloneSetup(
                        new SeahorseEvalCandidateDecisionController(
                                decisionProvider, regressionProvider, gateProvider))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }
}
