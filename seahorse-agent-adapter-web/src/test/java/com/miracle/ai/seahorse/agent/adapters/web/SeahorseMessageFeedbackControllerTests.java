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

import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseMessageFeedbackControllerTests {

    @Test
    void shouldQueryFeedbackEvaluationCandidates() throws Exception {
        MessageFeedbackInboundPort feedbackPort = mock(MessageFeedbackInboundPort.class);
        FeedbackEvaluationCandidateQueryInboundPort candidatePort =
                mock(FeedbackEvaluationCandidateQueryInboundPort.class);
        when(candidatePort.page(org.mockito.ArgumentMatchers.any())).thenReturn(new FeedbackEvaluationCandidatePage(
                List.of(new FeedbackEvaluationCandidate(
                        "feedback-1",
                        "message-1",
                        "conversation-1",
                        "user-1",
                        "run-1",
                        -1,
                        "INCORRECT",
                        "wrong fact",
                        "assistant answer",
                        Instant.parse("2026-05-26T00:00:00Z"))),
                1L,
                10L,
                1L,
                1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMessageFeedbackController(
                provider(MessageFeedbackInboundPort.class, feedbackPort),
                provider(FeedbackEvaluationCandidateQueryInboundPort.class, candidatePort))).build();

        mvc.perform(get("/api/feedback/evaluation-candidates")
                        .param("userId", "user-1")
                        .param("runId", "run-1")
                        .param("reason", "INCORRECT")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].feedbackId").value("feedback-1"))
                .andExpect(jsonPath("$.data.records[0].agentRunId").value("run-1"))
                .andExpect(jsonPath("$.data.records[0].reason").value("INCORRECT"));

        ArgumentCaptor<FeedbackEvaluationCandidateQuery> queryCaptor =
                ArgumentCaptor.forClass(FeedbackEvaluationCandidateQuery.class);
        verify(candidatePort).page(queryCaptor.capture());
        assertThat(queryCaptor.getValue().userId()).isEqualTo("user-1");
        assertThat(queryCaptor.getValue().runId()).isEqualTo("run-1");
        assertThat(queryCaptor.getValue().reason()).isEqualTo("INCORRECT");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
