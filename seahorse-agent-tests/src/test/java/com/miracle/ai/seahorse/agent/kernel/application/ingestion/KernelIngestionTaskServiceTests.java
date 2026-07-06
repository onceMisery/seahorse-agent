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

package com.miracle.ai.seahorse.agent.kernel.application.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class KernelIngestionTaskServiceTests {

    @Test
    void shouldRedactCredentialTextFromFailedTaskAndNodeLogs() {
        String failure = "parser failed Authorization: Bearer abcdefghijklmnop api_key=plain-ingestion-secret";
        RecordingTaskRepository repository = new RecordingTaskRepository();
        KernelIngestionTaskService service = new KernelIngestionTaskService(
                engine(NodeResult.fail(new IllegalStateException(failure))),
                pipelineId -> Optional.of(pipeline()),
                repository);

        IngestionTaskExecutionResult result = service.execute(new IngestionTaskCreateCommand(
                "pipeline-a",
                new IngestionDocumentSource("text", "hello", "hello.txt", Map.of()),
                Map.of(),
                "operator-a",
                null));

        Assertions.assertEquals("failed", result.status());
        assertRedacted(result.message());
        assertRedacted(repository.updated.errorMessage());
        Assertions.assertEquals(1, repository.updated.logs().size());
        assertRedacted(repository.updated.logs().get(0).getMessage());
        assertRedacted(repository.updated.logs().get(0).getError());
        Assertions.assertEquals(1, repository.nodes.size());
        assertRedacted(repository.nodes.get(0).getMessage());
        assertRedacted(repository.nodes.get(0).getErrorMessage());
    }

    private static void assertRedacted(String value) {
        Assertions.assertNotNull(value);
        Assertions.assertTrue(value.contains("[REDACTED]"), value);
        Assertions.assertFalse(value.contains("abcdefghijklmnop"), value);
        Assertions.assertFalse(value.contains("plain-ingestion-secret"), value);
    }

    private KernelIngestionEngine engine(NodeResult result) {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        IngestionNodeFeature feature = new IngestionNodeFeature() {
            @Override
            public String name() {
                return "parser";
            }

            @Override
            public String nodeType() {
                return "parser";
            }

            @Override
            public NodeResult execute(IngestionContext context, NodeConfig config) {
                return result;
            }
        };
        registry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), false), feature);
        return new KernelIngestionEngine(registry, FeatureActivationContext.empty(), (context, config) -> true,
                (context, config, nodeResult, durationMs) -> context.getLogs().add(NodeLog.builder()
                        .nodeId(config.getNodeId())
                        .nodeType(config.getNodeType())
                        .message(nodeResult == null ? null : nodeResult.getMessage())
                        .error(nodeResult == null || nodeResult.getError() == null
                                ? null
                                : nodeResult.getError().getMessage())
                        .success(nodeResult != null && nodeResult.isSuccess())
                        .durationMs(durationMs)
                        .build()));
    }

    private PipelineDefinition pipeline() {
        return PipelineDefinition.builder()
                .id("pipeline-a")
                .version(1)
                .nodes(List.of(NodeConfig.builder()
                        .nodeId("parser")
                        .nodeType("parser")
                        .build()))
                .build();
    }

    private static class RecordingTaskRepository implements IngestionTaskRepositoryPort {

        private IngestionTaskUpdateValues updated;
        private List<IngestionTaskNodeValues> nodes = new ArrayList<>();

        @Override
        public String createRunningTask(IngestionTaskCreateValues values) {
            return "task-a";
        }

        @Override
        public void updateTask(String taskId, IngestionTaskUpdateValues values) {
            this.updated = values;
        }

        @Override
        public void replaceNodeLogs(String taskId, List<IngestionTaskNodeValues> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }

        @Override
        public Optional<IngestionTaskRecord> findById(String taskId) {
            return Optional.empty();
        }

        @Override
        public List<IngestionTaskNodeRecord> listNodes(String taskId) {
            return List.of();
        }

        @Override
        public IngestionTaskPage page(long current, long size, String status) {
            return new IngestionTaskPage(List.of(), 0, size, current, 0);
        }
    }
}
