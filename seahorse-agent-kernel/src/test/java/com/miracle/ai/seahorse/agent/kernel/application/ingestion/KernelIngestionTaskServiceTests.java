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
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRollbackResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRollbackTarget;
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
    void shouldRollbackCompletedTaskDocumentTargetWithCompensationEvidence() {
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        IngestionTaskRecord original = task("task-1", "completed");
        original.setChunkCount(3);
        original.setMetadata(Map.of(
                "kbId", 123L,
                "docId", 456L,
                "collectionName", "kb_123"));
        taskRepository.records.put("task-1", original);
        CapturingCompensationPort compensationPort = new CapturingCompensationPort();
        KernelIngestionTaskService service = new KernelIngestionTaskService(
                new CapturingIngestionEngine(),
                new FakePipelineRepository(PipelineDefinition.builder().id("pipeline-1").build()),
                taskRepository,
                compensationPort);

        IngestionTaskRollbackResult result = service.rollback("task-1", "operator-1");

        assertEquals("task-1", result.taskId());
        assertEquals("rolled_back", result.status());
        assertEquals(123L, result.kbId());
        assertEquals(456L, result.docId());
        assertEquals(1, compensationPort.targets.size());
        IngestionTaskRollbackTarget target = compensationPort.targets.get(0);
        assertEquals("task-1", target.taskId());
        assertEquals(123L, target.kbId());
        assertEquals(456L, target.docId());
        assertEquals("kb_123", target.collectionName());
        assertEquals("rolled_back", taskRepository.updatedValues.get(0).status());
        assertEquals("operator-1", taskRepository.updatedValues.get(0).operator());
        assertEquals(456L, taskRepository.updatedValues.get(0).metadata().get("rollbackDocId"));
        assertEquals("operator-1", taskRepository.updatedValues.get(0).metadata().get("rollbackOperator"));
    }

    @Test
    void shouldRejectRollbackWhenTaskHasNoDocumentTarget() {
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        IngestionTaskRecord original = task("task-1", "completed");
        original.setMetadata(Map.of("topic", "rag"));
        taskRepository.records.put("task-1", original);
        CapturingCompensationPort compensationPort = new CapturingCompensationPort();
        KernelIngestionTaskService service = new KernelIngestionTaskService(
                new CapturingIngestionEngine(),
                new FakePipelineRepository(PipelineDefinition.builder().id("pipeline-1").build()),
                taskRepository,
                compensationPort);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.rollback("task-1", "operator-1"));

        assertTrue(error.getMessage().contains("rollback requires docId and kbId"));
        assertTrue(compensationPort.targets.isEmpty());
        assertTrue(taskRepository.updatedValues.isEmpty());
    }

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
        original.setPipelineVersion(9);
        original.setPipelineSnapshot(pipelineSnapshot("pipeline-1", "Snapshot Pipeline", 9, "fetch", "fetch"));
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
        assertEquals("Snapshot Pipeline", engine.pipelines.get(0).getName());
        assertEquals(9, engine.pipelines.get(0).getVersion());
        assertEquals("rag", engine.contexts.get(0).getMetadata().get("topic"));
        assertEquals("task-1", engine.contexts.get(0).getMetadata().get("retryOfTaskId"));
        assertEquals("operator-1", taskRepository.updatedValues.get(0).operator());
        assertEquals(1, taskRepository.replacedNodes.get(0).get(0).getRetryCount());
    }

    @Test
    void shouldRetryFailedTaskFromRequestedNodeWithRestoredUpstreamOutput() {
        CapturingIngestionEngine engine = new CapturingIngestionEngine();
        FakePipelineRepository pipelineRepository = new FakePipelineRepository(PipelineDefinition.builder()
                .id("pipeline-1")
                .nodes(List.of(
                        NodeConfig.builder().nodeId("fetch").nodeType("fetch").nextNodeId("parse").build(),
                        NodeConfig.builder().nodeId("parse").nodeType("parse").nextNodeId("chunk").build(),
                        NodeConfig.builder().nodeId("chunk").nodeType("chunk").build()))
                .build());
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        IngestionTaskRecord original = task("task-1", "failed");
        original.setMetadata(Map.of("topic", "rag"));
        original.setPipelineSnapshot(Map.of(
                "id", "pipeline-1",
                "version", 2,
                "nodes", List.of(
                        Map.of("nodeId", "fetch", "nodeType", "fetch", "nextNodeId", "parse"),
                        Map.of("nodeId", "parse", "nodeType", "parse", "nextNodeId", "chunk"),
                        Map.of("nodeId", "chunk", "nodeType", "chunk"))));
        taskRepository.records.put("task-1", original);
        taskRepository.nodes.put("task-1", List.of(
                nodeRecord("fetch", 1, "success", Map.of("rawText", "Fetched text")),
                nodeRecord("parse", 2, "success", Map.of(
                        "rawText", "Parsed text",
                        "keywords", List.of("rag", "pipeline"),
                        "metadata", Map.of("language", "en"))),
                nodeRecord("chunk", 3, "failed", Map.of())));
        KernelIngestionTaskService service =
                new KernelIngestionTaskService(engine, pipelineRepository, taskRepository);

        service.retry("task-1", "chunk", "operator-1");

        IngestionContext retryContext = engine.contexts.get(0);
        assertEquals("chunk", retryContext.getStartNodeId());
        assertEquals("Parsed text", retryContext.getRawText());
        assertEquals(List.of("rag", "pipeline"), retryContext.getKeywords());
        assertEquals("en", retryContext.getMetadata().get("language"));
        assertEquals("rag", retryContext.getMetadata().get("topic"));
        assertEquals("task-1", retryContext.getMetadata().get("retryOfTaskId"));
        assertEquals("chunk", retryContext.getMetadata().get("retryFromNodeId"));
        assertEquals(List.of("fetch", "parse"), retryContext.getMetadata().get("restoredNodeIds"));
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

    @Test
    void shouldAttachGovernanceEvidenceToGeneratedNodeLogs() {
        CapturingIngestionEngine engine = new CapturingIngestionEngine(
                IngestionStatus.FAILED,
                new IllegalStateException("pipeline failed"),
                List.of(NodeLog.builder()
                        .nodeId("parse")
                        .nodeType("parse")
                        .success(false)
                        .durationMs(20)
                        .error("cannot parse pdf")
                        .output(Map.of("chunks", 0))
                        .build()));
        FakePipelineRepository pipelineRepository = new FakePipelineRepository(PipelineDefinition.builder()
                .id("pipeline-1")
                .version(4)
                .nodes(List.of(
                        NodeConfig.builder().nodeId("parse").nodeType("parse").nextNodeId("chunk").build(),
                        NodeConfig.builder().nodeId("chunk").nodeType("chunk").nextNodeId("embed").build(),
                        NodeConfig.builder().nodeId("embed").nodeType("embed").build()))
                .build());
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        KernelIngestionTaskService service =
                new KernelIngestionTaskService(engine, pipelineRepository, taskRepository);

        service.execute(new IngestionTaskCreateCommand(
                "pipeline-1",
                new IngestionDocumentSource("text", "broken pdf content", "broken.pdf", Map.of()),
                Map.of(),
                null,
                "operator-1"));

        assertEquals(4, taskRepository.createdValues.get(0).pipelineVersion());
        assertTrue(taskRepository.createdValues.get(0).pipelineSnapshot().containsKey("nodes"));
        IngestionTaskNodeValues node = taskRepository.replacedNodes.get(0).get(0);
        assertTrue(node.getInputSummary().contains("source=text"));
        assertTrue(node.getInputSummary().contains("file=broken.pdf"));
        assertTrue(node.getOutputSummary().contains("chunks=0"));
        assertEquals("PARSE_FAILED", node.getErrorCode());
        assertEquals(0, node.getRetryCount());
        assertEquals("chunk/embed skipped", node.getDownstreamImpact());
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

    private static Map<String, Object> pipelineSnapshot(String id,
                                                        String name,
                                                        int version,
                                                        String nodeId,
                                                        String nodeType) {
        return Map.of(
                "id", id,
                "name", name,
                "version", version,
                "nodes", List.of(Map.of("nodeId", nodeId, "nodeType", nodeType)));
    }

    private static IngestionTaskNodeRecord nodeRecord(String nodeId,
                                                      int nodeOrder,
                                                      String status,
                                                      Map<String, Object> output) {
        IngestionTaskNodeRecord record = new IngestionTaskNodeRecord();
        record.setNodeId(nodeId);
        record.setNodeOrder(nodeOrder);
        record.setStatus(status);
        record.setOutput(output);
        return record;
    }

    private static final class CapturingIngestionEngine extends KernelIngestionEngine {
        private final List<IngestionContext> contexts = new ArrayList<>();
        private final List<PipelineDefinition> pipelines = new ArrayList<>();
        private final IngestionStatus status;
        private final Throwable error;
        private final List<NodeLog> logs;

        private CapturingIngestionEngine() {
            this(IngestionStatus.COMPLETED, null, List.of(NodeLog.builder()
                    .nodeId("fetch")
                    .nodeType("fetch")
                    .success(true)
                    .message("retried")
                    .build()));
        }

        private CapturingIngestionEngine(IngestionStatus status, Throwable error, List<NodeLog> logs) {
            super(ExtensionRegistry.empty(), FeatureActivationContext.empty());
            this.status = status;
            this.error = error;
            this.logs = logs;
        }

        @Override
        public IngestionContext execute(PipelineDefinition pipeline, IngestionContext context) {
            pipelines.add(pipeline);
            contexts.add(context);
            context.setStatus(status);
            context.setError(error);
            context.setLogs(logs);
            return context;
        }
    }

    private static final class CapturingCompensationPort implements IngestionTaskCompensationPort {
        private final List<IngestionTaskRollbackTarget> targets = new ArrayList<>();

        @Override
        public void rollbackDocument(IngestionTaskRollbackTarget target, String operator) {
            targets.add(target);
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
        private final Map<String, List<IngestionTaskNodeRecord>> nodes = new LinkedHashMap<>();
        private final List<IngestionTaskCreateValues> createdValues = new ArrayList<>();
        private final List<IngestionTaskUpdateValues> updatedValues = new ArrayList<>();
        private final List<List<IngestionTaskNodeValues>> replacedNodes = new ArrayList<>();

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
            replacedNodes.add(nodes);
        }

        @Override
        public Optional<IngestionTaskRecord> findById(String taskId) {
            return Optional.ofNullable(records.get(taskId));
        }

        @Override
        public List<IngestionTaskNodeRecord> listNodes(String taskId) {
            return nodes.getOrDefault(taskId, List.of());
        }

        @Override
        public IngestionTaskPage page(long current, long size, String status) {
            return new IngestionTaskPage(List.of(), 0, size, current, 0);
        }
    }
}
