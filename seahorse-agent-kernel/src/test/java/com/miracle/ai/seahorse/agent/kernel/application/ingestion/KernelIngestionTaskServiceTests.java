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
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelIngestionTaskServiceTests {

    @Test
    void shouldReplayFailedTaskWithOriginalSourceAndRetryEvidence() {
        CapturingIngestionEngine engine = new CapturingIngestionEngine();
        FakePipelineRepository pipelineRepository = new FakePipelineRepository(PipelineDefinition.builder()
                .id("pipeline-1")
                .nodes(List.of(NodeConfig.builder().nodeId("fetch").nodeType("fetch").build()))
                .build());
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        IngestionTaskRecord original = task("task-1", "failed");
        original.setMetadata(Map.of("topic", "rag", "vectorSpaceId", "kb-1"));
        taskRepository.records.put("task-1", original);
        KernelIngestionTaskService service =
                new KernelIngestionTaskService(engine, pipelineRepository, taskRepository);

        IngestionTaskExecutionResult result = service.retry("task-1", "operator-1");

        assertEquals("task-2", result.taskId());
        assertEquals("completed", result.status());
        assertEquals("pipeline-1", taskRepository.createdValues.get(0).pipelineId());
        assertEquals("text", taskRepository.createdValues.get(0).sourceType());
        assertEquals("Seahorse roadmap", taskRepository.createdValues.get(0).sourceLocation());
        assertEquals("task-2", engine.contexts.get(0).getTaskId());
        assertEquals("kb-1", engine.contexts.get(0).getVectorSpaceId());
        assertEquals("rag", engine.contexts.get(0).getMetadata().get("topic"));
        assertEquals("task-1", engine.contexts.get(0).getMetadata().get("retryOfTaskId"));
        assertEquals("operator-1", taskRepository.updatedValues.get(0).operator());
    }

    @Test
    void shouldRejectRetryWhenTaskIsNotFailed() {
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        taskRepository.records.put("task-1", task("task-1", "completed"));
        KernelIngestionTaskService service = new KernelIngestionTaskService(
                new CapturingIngestionEngine(),
                new FakePipelineRepository(PipelineDefinition.builder().id("pipeline-1").build()),
                taskRepository);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.retry("task-1", "operator-1"));

        assertTrue(error.getMessage().contains("only failed ingestion tasks can be retried"));
        assertTrue(taskRepository.createdValues.isEmpty());
    }

    private static IngestionTaskRecord task(String id, String status) {
        IngestionTaskRecord record = new IngestionTaskRecord();
        record.setId(id);
        record.setPipelineId("pipeline-1");
        record.setSourceType("text");
        record.setSourceLocation("Seahorse roadmap");
        record.setSourceFileName("roadmap.txt");
        record.setStatus(status);
        return record;
    }

    private static final class CapturingIngestionEngine extends KernelIngestionEngine {
        private final List<IngestionContext> contexts = new ArrayList<>();

        private CapturingIngestionEngine() {
            super(ExtensionRegistry.empty(), FeatureActivationContext.empty());
        }

        @Override
        public IngestionContext execute(PipelineDefinition pipeline, IngestionContext context) {
            contexts.add(context);
            context.setStatus(IngestionStatus.COMPLETED);
            context.setLogs(List.of(NodeLog.builder()
                    .nodeId("fetch")
                    .nodeType("fetch")
                    .success(true)
                    .message("retried")
                    .build()));
            return context;
        }
    }

    private record FakePipelineRepository(PipelineDefinition pipeline) implements PipelineDefinitionRepositoryPort {
        @Override
        public Optional<PipelineDefinition> findById(String pipelineId) {
            return Optional.ofNullable(pipeline);
        }
    }

    private static final class FakeTaskRepository implements IngestionTaskRepositoryPort {
        private final Map<String, IngestionTaskRecord> records = new LinkedHashMap<>();
        private final List<IngestionTaskCreateValues> createdValues = new ArrayList<>();
        private final List<IngestionTaskUpdateValues> updatedValues = new ArrayList<>();

        @Override
        public String createRunningTask(IngestionTaskCreateValues values) {
            createdValues.add(values);
            return "task-" + (createdValues.size() + 1);
        }

        @Override
        public void updateTask(String taskId, IngestionTaskUpdateValues values) {
            updatedValues.add(values);
        }

        @Override
        public void replaceNodeLogs(String taskId, List<IngestionTaskNodeValues> nodes) {
        }

        @Override
        public Optional<IngestionTaskRecord> findById(String taskId) {
            return Optional.ofNullable(records.get(taskId));
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
