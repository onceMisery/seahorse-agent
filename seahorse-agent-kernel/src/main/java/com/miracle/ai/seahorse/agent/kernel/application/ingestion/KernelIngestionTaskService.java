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
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskUploadCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Seahorse 原生入库任务应用服务。
 */
public class KernelIngestionTaskService implements IngestionTaskInboundPort {

    private static final String DEFAULT_UPLOAD_FILE_NAME = "upload.bin";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String MESSAGE_OK = "OK";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_QUESTIONS = "questions";
    private static final String KEY_VECTOR_SPACE_ID = "vectorSpaceId";
    private static final String KEY_RETRY_OF_TASK_ID = "retryOfTaskId";

    private final KernelIngestionEngine ingestionEngine;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;
    private final IngestionTaskRepositoryPort taskRepositoryPort;

    public KernelIngestionTaskService(KernelIngestionEngine ingestionEngine,
                                      PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                      IngestionTaskRepositoryPort taskRepositoryPort) {
        this.ingestionEngine = Objects.requireNonNull(ingestionEngine, "ingestionEngine must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
        this.taskRepositoryPort = Objects.requireNonNull(taskRepositoryPort, "taskRepositoryPort must not be null");
    }

    @Override
    public IngestionTaskExecutionResult execute(IngestionTaskCreateCommand command) {
        IngestionTaskCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        IngestionDocumentSource source = requireSource(safeCommand.source());
        String pipelineId = requireText(safeCommand.pipelineId(), "pipelineId");
        byte[] rawBytes = resolveInlineBytes(source);
        return executeInternal(new ExecutionInput(
                pipelineId,
                source,
                new ExecutionContent(rawBytes, "", safeCommand.metadata(), safeCommand.vectorSpaceId()),
                safeCommand.operator()));
    }

    @Override
    public IngestionTaskExecutionResult upload(IngestionTaskUploadCommand command) {
        IngestionTaskUploadCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String pipelineId = requireText(safeCommand.pipelineId(), "pipelineId");
        String fileName = hasText(safeCommand.originalFilename())
                ? safeCommand.originalFilename().trim()
                : DEFAULT_UPLOAD_FILE_NAME;
        IngestionDocumentSource source = new IngestionDocumentSource("file", fileName, fileName, Map.of());
        return executeInternal(new ExecutionInput(
                pipelineId,
                source,
                new ExecutionContent(
                        requireContent(safeCommand.content()),
                        contentType(safeCommand.contentType()),
                        Map.of(KEY_FILE_NAME, fileName),
                        null),
                safeCommand.operator()));
    }

    @Override
    public IngestionTaskExecutionResult retry(String taskId, String operator) {
        String safeTaskId = requireText(taskId, "taskId");
        IngestionTaskRecord record = taskRepositoryPort.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("入库任务不存在：" + safeTaskId));
        if (!STATUS_FAILED.equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("only failed ingestion tasks can be retried: " + safeTaskId);
        }
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(record.getMetadata(), Map.of()));
        metadata.put(KEY_RETRY_OF_TASK_ID, safeTaskId);
        IngestionDocumentSource source = new IngestionDocumentSource(
                record.getSourceType(),
                record.getSourceLocation(),
                record.getSourceFileName(),
                Map.of());
        return executeInternal(new ExecutionInput(
                requireText(record.getPipelineId(), "pipelineId"),
                requireSource(source),
                new ExecutionContent(resolveInlineBytes(source), "", metadata, metadata.get(KEY_VECTOR_SPACE_ID)),
                operator));
    }

    @Override
    public IngestionTaskRecord get(String taskId) {
        String safeTaskId = requireText(taskId, "taskId");
        return taskRepositoryPort.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("入库任务不存在：" + safeTaskId));
    }

    @Override
    public List<IngestionTaskNodeRecord> listNodes(String taskId) {
        return taskRepositoryPort.listNodes(requireText(taskId, "taskId"));
    }

    @Override
    public IngestionTaskPage page(long current, long size, String status) {
        return taskRepositoryPort.page(current, size, normalizeStatus(status));
    }

    private IngestionTaskExecutionResult executeInternal(ExecutionInput input) {
        PipelineDefinition pipeline = requirePipeline(input.pipelineId());
        String taskId = taskRepositoryPort.createRunningTask(toCreateValues(input));
        IngestionContext context = buildContext(taskId, input);
        IngestionContext result = runPipeline(pipeline, context);
        List<NodeLog> logs = Objects.requireNonNullElse(result.getLogs(), List.of());
        IngestionTaskUpdateValues values = toUpdateValues(result, input.operator());
        taskRepositoryPort.updateTask(taskId, values);
        taskRepositoryPort.replaceNodeLogs(taskId, toNodeValues(taskId, input.pipelineId(), pipeline, logs));
        return toExecutionResult(taskId, input.pipelineId(), result);
    }

    private IngestionContext runPipeline(PipelineDefinition pipeline, IngestionContext context) {
        try {
            return ingestionEngine.execute(pipeline, context);
        } catch (Exception ex) {
            context.setStatus(IngestionStatus.FAILED);
            context.setError(ex);
            return context;
        }
    }

    private IngestionTaskCreateValues toCreateValues(ExecutionInput input) {
        IngestionDocumentSource source = input.source();
        return new IngestionTaskCreateValues(
                input.pipelineId(),
                source.type(),
                source.location(),
                source.fileName(),
                input.operator());
    }

    private IngestionContext buildContext(String taskId, ExecutionInput input) {
        return IngestionContext.builder()
                .taskId(taskId)
                .pipelineId(input.pipelineId())
                .source(input.source())
                .rawBytes(input.content().rawBytes())
                .mimeType(input.content().mimeType())
                .metadata(buildInitialMetadata(input))
                .vectorSpaceId(input.content().vectorSpaceId())
                .logs(new ArrayList<>())
                .build();
    }

    private Map<String, Object> buildInitialMetadata(ExecutionInput input) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                Objects.requireNonNullElse(input.content().metadata(), Map.of()));
        metadata.putIfAbsent(KEY_SOURCE, input.source());
        if (hasText(input.source().fileName())) {
            metadata.putIfAbsent(KEY_FILE_NAME, input.source().fileName());
        }
        if (input.content().vectorSpaceId() != null) {
            metadata.putIfAbsent(KEY_VECTOR_SPACE_ID, input.content().vectorSpaceId());
        }
        return metadata;
    }

    private IngestionTaskUpdateValues toUpdateValues(IngestionContext context, String operator) {
        return new IngestionTaskUpdateValues(
                status(context),
                chunkCount(context),
                errorMessage(context),
                buildLogSummary(context.getLogs()),
                buildTaskMetadata(context),
                operator);
    }

    private IngestionTaskExecutionResult toExecutionResult(String taskId, String pipelineId, IngestionContext context) {
        return new IngestionTaskExecutionResult(
                taskId,
                pipelineId,
                status(context),
                chunkCount(context),
                errorMessage(context) == null ? MESSAGE_OK : errorMessage(context));
    }

    private List<IngestionTaskNodeValues> toNodeValues(String taskId,
                                                       String pipelineId,
                                                       PipelineDefinition pipeline,
                                                       List<NodeLog> logs) {
        Map<String, Integer> nodeOrderMap = buildNodeOrderMap(pipeline);
        return logs.stream()
                .map(log -> toNodeValue(taskId, pipelineId, nodeOrderMap, log))
                .toList();
    }

    private IngestionTaskNodeValues toNodeValue(String taskId,
                                                String pipelineId,
                                                Map<String, Integer> nodeOrderMap,
                                                NodeLog log) {
        IngestionTaskNodeValues values = new IngestionTaskNodeValues();
        values.setTaskId(taskId);
        values.setPipelineId(pipelineId);
        values.setNodeId(log.getNodeId());
        values.setNodeType(log.getNodeType());
        values.setNodeOrder(nodeOrderMap.getOrDefault(log.getNodeId(), 0));
        values.setStatus(nodeStatus(log));
        values.setDurationMs(log.getDurationMs());
        values.setMessage(log.getMessage());
        values.setErrorMessage(log.getError());
        values.setOutput(log.getOutput());
        return values;
    }

    private List<NodeLog> buildLogSummary(List<NodeLog> logs) {
        return Objects.requireNonNullElse(logs, List.<NodeLog>of())
                .stream()
                .map(this::withoutOutput)
                .toList();
    }

    private NodeLog withoutOutput(NodeLog log) {
        if (log == null) {
            return NodeLog.builder().success(false).build();
        }
        return NodeLog.builder()
                .nodeId(log.getNodeId())
                .nodeType(log.getNodeType())
                .message(log.getMessage())
                .durationMs(log.getDurationMs())
                .success(log.isSuccess())
                .error(log.getError())
                .build();
    }

    private Map<String, Object> buildTaskMetadata(IngestionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (context.getMetadata() != null) {
            metadata.putAll(context.getMetadata());
        }
        putIfNotEmpty(metadata, KEY_KEYWORDS, context.getKeywords());
        putIfNotEmpty(metadata, KEY_QUESTIONS, context.getQuestions());
        return metadata;
    }

    private void putIfNotEmpty(Map<String, Object> metadata, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            metadata.put(key, values);
        }
    }

    private Map<String, Integer> buildNodeOrderMap(PipelineDefinition pipeline) {
        Map<String, NodeConfig> nodeMap = buildNodeMap(pipeline);
        Map<String, Integer> orderMap = new HashMap<>();
        Set<String> referenced = referencedNodes(nodeMap);
        Set<String> visited = new HashSet<>();
        int order = 1;
        for (String nodeId : nodeMap.keySet()) {
            if (!referenced.contains(nodeId)) {
                order = appendChain(nodeId, order, nodeMap, orderMap, visited);
            }
        }
        for (String nodeId : nodeMap.keySet()) {
            if (!visited.contains(nodeId)) {
                orderMap.put(nodeId, order++);
            }
        }
        return orderMap;
    }

    private Map<String, NodeConfig> buildNodeMap(PipelineDefinition pipeline) {
        Map<String, NodeConfig> nodeMap = new LinkedHashMap<>();
        List<NodeConfig> nodes = pipeline == null ? List.of() : Objects.requireNonNullElse(pipeline.getNodes(), List.of());
        for (NodeConfig node : nodes) {
            if (node != null && hasText(node.getNodeId())) {
                nodeMap.putIfAbsent(node.getNodeId(), node);
            }
        }
        return nodeMap;
    }

    private Set<String> referencedNodes(Map<String, NodeConfig> nodeMap) {
        Set<String> referenced = new HashSet<>();
        for (NodeConfig node : nodeMap.values()) {
            if (hasText(node.getNextNodeId())) {
                referenced.add(node.getNextNodeId());
            }
        }
        return referenced;
    }

    private int appendChain(String startNodeId,
                            int initialOrder,
                            Map<String, NodeConfig> nodeMap,
                            Map<String, Integer> orderMap,
                            Set<String> visited) {
        String current = startNodeId;
        int order = initialOrder;
        while (hasText(current) && !visited.contains(current)) {
            orderMap.put(current, order++);
            visited.add(current);
            NodeConfig config = nodeMap.get(current);
            current = config == null ? null : config.getNextNodeId();
        }
        return order;
    }

    private PipelineDefinition requirePipeline(String pipelineId) {
        return pipelineRepositoryPort.findById(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("入库 Pipeline 不存在：" + pipelineId));
    }

    private IngestionDocumentSource requireSource(IngestionDocumentSource source) {
        IngestionDocumentSource safeSource = Objects.requireNonNull(source, "source must not be null");
        if (!hasText(safeSource.type())) {
            throw new IllegalArgumentException("source.type must not be blank");
        }
        return safeSource;
    }

    private byte[] resolveInlineBytes(IngestionDocumentSource source) {
        if (!"text".equalsIgnoreCase(source.type())) {
            return null;
        }
        return source.location().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] requireContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content must not be empty");
        }
        return content.clone();
    }

    private String contentType(String contentType) {
        return hasText(contentType) ? contentType.trim() : DEFAULT_CONTENT_TYPE;
    }

    private String status(IngestionContext context) {
        IngestionStatus status = context.getStatus() == null ? IngestionStatus.FAILED : context.getStatus();
        return status.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        return hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : status;
    }

    private String nodeStatus(NodeLog log) {
        if (log == null || !log.isSuccess()) {
            return STATUS_FAILED;
        }
        String message = log.getMessage();
        return message != null && message.startsWith("Skipped:") ? STATUS_SKIPPED : STATUS_SUCCESS;
    }

    private int chunkCount(IngestionContext context) {
        return context.getChunks() == null ? 0 : context.getChunks().size();
    }

    private String errorMessage(IngestionContext context) {
        Throwable error = context.getError();
        return error == null ? null : error.getMessage();
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ExecutionInput(
            String pipelineId,
            IngestionDocumentSource source,
            ExecutionContent content,
            String operator
    ) {
    }

    private record ExecutionContent(
            byte[] rawBytes,
            String mimeType,
            Map<String, Object> metadata,
            Object vectorSpaceId
    ) {
    }
}
