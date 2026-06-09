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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRunEventBufferAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentRepositoryAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentOpsRepositoryAutoConfiguration.class,
                    SeahorseAgentIngestionRepositoryAutoConfiguration.class,
                    SeahorseAgentRetrievalRepositoryAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void registersJsonBackedRepositoriesWhenObjectMapperIsNotReadyYet() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IntentTreeRepositoryPort.class);
            assertThat(context).hasSingleBean(PipelineDefinitionRepositoryPort.class);
            assertThat(context).hasSingleBean(IngestionPipelineRepositoryPort.class);
            assertThat(context).hasSingleBean(IngestionTaskRepositoryPort.class);
            assertThat(context).hasSingleBean(RetrievalEvaluationDatasetRepositoryPort.class);
        });
    }

    @Test
    void registersJdbcAgentRunEventBufferWhenObjectMapperIsReady() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentRegistryRepositoryAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentRunEventBufferPort.class);
                    assertThat(context.getBean(AgentRunEventBufferPort.class))
                            .isInstanceOf(JdbcAgentRunEventBufferAdapter.class);
                });
    }

    @Test
    void registersJdbcAgentRunEventBufferWhenObjectMapperBeanIsNotReadyYet() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentRegistryRepositoryAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentRunEventBufferPort.class);
                    assertThat(context.getBean(AgentRunEventBufferPort.class))
                            .isInstanceOf(JdbcAgentRunEventBufferAdapter.class);
                });
    }

    @Test
    void chatCallbackFactoryUsesTheConfiguredEventBuffer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelChatAutoConfiguration.class))
                .withBean(AgentRunEventBufferPort.class, RecordingEventBuffer::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ChatStreamCallbackFactoryPort factory = context.getBean(ChatStreamCallbackFactoryPort.class);
                    RecordingEventBuffer eventBuffer = context.getBean(RecordingEventBuffer.class);

                    StreamCallback callback = factory.create(new SseEmitter(), "conversation-1", "task-1", "user-1");
                    callback.onRunStarted("run-1");
                    callback.onComplete();

                    assertThat(eventBuffer.events)
                            .extracting(StreamEventEnvelope::runId)
                            .containsExactly("run-1");
                });
    }

    private static final class RecordingEventBuffer implements AgentRunEventBufferPort {

        private final List<StreamEventEnvelope> events = new ArrayList<>();

        @Override
        public void append(String runId, StreamEventEnvelope event) {
            events.add(event);
        }

        @Override
        public List<StreamEventEnvelope> getAfter(String runId, long afterSeq) {
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.eventSeq() > afterSeq)
                    .toList();
        }

        @Override
        public Optional<Long> getLatestSeq(String runId) {
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .map(StreamEventEnvelope::eventSeq)
                    .max(Long::compareTo);
        }

        @Override
        public void expire(String runId) {
            events.removeIf(event -> event.runId().equals(runId));
        }
    }
}
