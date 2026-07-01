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

import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentReport;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRunExperimentControllerTests {

    @Test
    void shouldCreateRunExperiment() throws Exception {
        RunExperimentInboundPort port = mock(RunExperimentInboundPort.class);
        when(port.create(any())).thenReturn(details());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunExperimentController(provider(port))).build();

        mvc.perform(post("/api/run-experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": 101,
                                  "baseLeafMessageId": 202,
                                  "name": "Profile compare",
                                  "runProfileIds": [12, 13]
                                }
                                """)
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.experiment.id").value(1))
                .andExpect(jsonPath("$.data.trials[0].runProfileId").value(12))
                .andExpect(jsonPath("$.data.trials[0].status").value("PENDING"));

        ArgumentCaptor<RunExperimentCommand> captor = ArgumentCaptor.forClass(RunExperimentCommand.class);
        verify(port).create(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("100");
        assertThat(captor.getValue().getConversationId()).isEqualTo(101L);
        assertThat(captor.getValue().getRunProfileIds()).containsExactly(12L, 13L);
    }

    @Test
    void shouldGetRunExperiment() throws Exception {
        RunExperimentInboundPort port = mock(RunExperimentInboundPort.class);
        when(port.findById("100", 1L)).thenReturn(Optional.of(details()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunExperimentController(provider(port))).build();

        mvc.perform(get("/api/run-experiments/1").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.experiment.name").value("Profile compare"))
                .andExpect(jsonPath("$.data.trials[1].runProfileId").value(13));

        verify(port).findById("100", 1L);
    }

    @Test
    void shouldExportRunExperimentReport() throws Exception {
        RunExperimentInboundPort port = mock(RunExperimentInboundPort.class);
        when(port.exportReport("100", 1L)).thenReturn(new RunExperimentReport(
                "profile-compare-1.md",
                "text/markdown; charset=UTF-8",
                "# Run Experiment Report\n\nTrial 10"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunExperimentController(provider(port))).build();

        mvc.perform(get("/api/run-experiments/1/report").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.fileName").value("profile-compare-1.md"))
                .andExpect(jsonPath("$.data.contentType").value("text/markdown; charset=UTF-8"))
                .andExpect(jsonPath("$.data.markdown").value("# Run Experiment Report\n\nTrial 10"));

        verify(port).exportReport("100", 1L);
    }

    @Test
    void shouldCancelAndScoreRunExperiment() throws Exception {
        RunExperimentInboundPort port = mock(RunExperimentInboundPort.class);
        when(port.cancel("100", 1L)).thenReturn(detailsWithStatus("CANCELLED"));
        when(port.scoreTrial("100", 1L, 10L, "{\"rating\":5}")).thenReturn(detailsWithScore());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunExperimentController(provider(port))).build();

        mvc.perform(post("/api/run-experiments/1/cancel").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.experiment.status").value("CANCELLED"));
        mvc.perform(post("/api/run-experiments/1/trials/10/score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":{"rating":5}}
                                """)
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.trials[0].scoreJson").value("{\"rating\":5}"));

        verify(port).cancel("100", 1L);
        verify(port).scoreTrial("100", 1L, 10L, "{\"rating\":5}");
    }

    @Test
    void shouldForkRunExperimentTrialToBranch() throws Exception {
        RunExperimentInboundPort port = mock(RunExperimentInboundPort.class);
        ConversationBranchInboundPort branchPort = mock(ConversationBranchInboundPort.class);
        when(port.findById("100", 1L)).thenReturn(Optional.of(detailsWithOutputMessage()));
        when(branchPort.switchBranch("101", "100", 301L)).thenReturn(List.of(new MessageTreeNode(
                message("301", 202L, "assistant", "trial output", 1),
                List.of(),
                List.of(),
                0,
                1)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunExperimentController(
                provider(port),
                provider(branchPort))).build();

        mvc.perform(post("/api/run-experiments/1/trials/10/fork-to-branch").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.trialId").value(10))
                .andExpect(jsonPath("$.data.outputMessageId").value(301))
                .andExpect(jsonPath("$.data.branch[0].message.id").value("301"))
                .andExpect(jsonPath("$.data.branch[0].message.active").value(1));

        verify(port).findById("100", 1L);
        verify(branchPort).switchBranch("101", "100", 301L);
    }

    private static RunExperimentDetails details() {
        return RunExperimentDetails.builder()
                .experiment(RunExperimentRecord.builder()
                        .id(1L)
                        .userId("100")
                        .conversationId(101L)
                        .baseLeafMessageId(202L)
                        .name("Profile compare")
                        .status("PENDING")
                        .build())
                .trials(List.of(
                        RunExperimentTrialRecord.builder()
                                .id(10L)
                                .experimentId(1L)
                                .runProfileId(12L)
                                .status("PENDING")
                                .build(),
                        RunExperimentTrialRecord.builder()
                                .id(11L)
                                .experimentId(1L)
                                .runProfileId(13L)
                                .status("PENDING")
                                .build()))
                .build();
    }

    private static RunExperimentDetails detailsWithStatus(String status) {
        RunExperimentDetails details = details();
        details.getExperiment().setStatus(status);
        details.getTrials().forEach(trial -> trial.setStatus(status));
        return details;
    }

    private static RunExperimentDetails detailsWithScore() {
        RunExperimentDetails details = details();
        details.getTrials().get(0).setScoreJson("{\"rating\":5}");
        return details;
    }

    private static RunExperimentDetails detailsWithOutputMessage() {
        RunExperimentDetails details = details();
        details.getTrials().get(0).setOutputMessageId(301L);
        return details;
    }

    private static ConversationMessageRecord message(String id, Long parentId, String role, String content,
                                                     Integer active) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(id);
        record.setParentId(parentId);
        record.setRole(role);
        record.setContent(content);
        record.setActive(active);
        return record;
    }

    private static <T> ObjectProvider<T> provider(T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(instance.getClass().getName(), instance);
        return beanFactory.getBeanProvider((Class<T>) instance.getClass().getInterfaces()[0]);
    }
}
