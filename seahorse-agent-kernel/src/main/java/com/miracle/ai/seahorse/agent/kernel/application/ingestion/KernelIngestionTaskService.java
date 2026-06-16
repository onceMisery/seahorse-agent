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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskUploadCommand;
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

import java.lang.reflect.Array;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_UPLOAD_FILE_NAME = "upload.bin";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_ROLLED_BACK = "rolled_back";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String MESSAGE_OK = "OK";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_QUESTIONS = "questions";
    private static final String KEY_VECTOR_SPACE_ID = "vectorSpaceId";
    private static final String KEY_RETRY_OF_TASK_ID = "retryOfTaskId";
    private static final String KEY_RETRY_FROM_NODE_ID = "retryFromNodeId";
    private static final String KEY_RESTORED_NODE_IDS = "restoredNodeIds";
    private static final String KEY_DOC_ID = "docId";
    private static final String KEY_DOCUMENT_ID = "documentId";
    private static final String KEY_KB_ID = "kbId";
    private static final String KEY_KNOWLEDGE_BASE_ID = "knowledgeBaseId";
    private static final String KEY_COLLECTION_NAME = "collectionName";
    private static final String KEY_ROLLBACK_DOC_ID = "rollbackDocId";
    private static final String KEY_ROLLBACK_KB_ID = "rollbackKbId";
    private static final String KEY_ROLLBACK_OPERATOR = "rollbackOperator";
    private static final String KEY_ROLLBACK_STATUS = "rollbackStatus";

    private final KernelIngestionEngine ingestionEngine;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;
    private final IngestionTaskRepositoryPort taskRepositoryPort;
    private final IngestionTaskCompensationPort compensationPort;

    public KernelIngestionTaskService(KernelIngestionEngine ingestionEngine,
                                      PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                      IngestionTaskRepositoryPort taskRepositoryPort) {
        this(ingestionEngine, pipelineRepositoryPort, taskRepositoryPort,
                IngestionTaskCompensationPort.unsupported());
    }

    public KernelIngestionTaskService(KernelIngestionEngine ingestionEngine,
                                      PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                      IngestionTaskRepositoryPort taskRepositoryPort,
                                      IngestionTaskCompensationPort compensationPort) {
        this.ingestionEngine = Objects.requireNonNull(ingestionEngine, "ingestionEngine must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
        this.taskRepositoryPort = Objects.requireNonNull(taskRepositoryPort, "taskRepositoryPort must not be null");
        this.compensationPort = Objects.requireNonNullElseGet(compensationPort,
                IngestionTaskCompensationPort::unsupported);
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
                safeCommand.operator(),
                null,
                null,
                Map.of()));
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
                safeCommand.operator(),
                null,
                null,
                Map.of()));
    }

    @Override
    public IngestionTaskExecutionResult retry(String taskId, String operator) {
        return retry(taskId, null, operator);
    }

    @Override
    public IngestionTaskExecutionResult retry(String taskId, String fromNodeId, String operator) {
        String safeTaskId = requireText(taskId, "taskId");
        IngestionTaskRecord record = taskRepositoryPort.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("入库任务不存在：" + safeTaskId));
        if (!STATUS_FAILED.equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("only failed ingestion tasks can be retried: " + safeTaskId);
        }
        PipelineDefinition pipeline = pipelineFromSnapshot(record);
        String retryFromNodeId = normalizeOptionalText(fromNodeId);
        RestoredNodeOutputs restored = restoredNodeOutputs(safeTaskId, pipeline, retryFromNodeId);
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(record.getMetadata(), Map.of()));
        metadata.put(KEY_RETRY_OF_TASK_ID, safeTaskId);
        if (hasText(retryFromNodeId)) {
            metadata.put(KEY_RETRY_FROM_NODE_ID, retryFromNodeId);
            metadata.put(KEY_RESTORED_NODE_IDS, restored.nodeIds());
        }
        IngestionDocumentSource source = new IngestionDocumentSource(
                record.getSourceType(),
                record.getSourceLocation(),
                record.getSourceFileName(),
                Map.of());
        return executeInternal(new ExecutionInput(
                requireText(record.getPipelineId(), "pipelineId"),
                requireSource(source),
                new ExecutionContent(resolveInlineBytes(source), "", metadata, metadata.get(KEY_VECTOR_SPACE_ID)),
                operator,
                pipeline,
                retryFromNodeId,
                restored.outputs()));
    }

    @Override
    public IngestionTaskRollbackResult rollback(String taskId, String operator) {
        String safeTaskId = requireText(taskId, "taskId");
        IngestionTaskRecord record = taskRepositoryPort.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("鍏ュ簱浠诲姟涓嶅瓨鍦細" + safeTaskId));
        if (STATUS_RUNNING.equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("running ingestion tasks cannot be rolled back: " + safeTaskId);
        }
        IngestionTaskRollbackTarget target = rollbackTarget(record);
        String safeOperator = Objects.requireNonNullElse(operator, "");
        compensationPort.rollbackDocument(target, safeOperator);
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(record.getMetadata(), Map.of()));
        metadata.put(KEY_ROLLBACK_STATUS, STATUS_ROLLED_BACK);
        metadata.put(KEY_ROLLBACK_DOC_ID, target.docId());
        metadata.put(KEY_ROLLBACK_KB_ID, target.kbId());
        metadata.put(KEY_ROLLBACK_OPERATOR, safeOperator);
        taskRepositoryPort.updateTask(safeTaskId, new IngestionTaskUpdateValues(
                STATUS_ROLLED_BACK,
                0,
                null,
                record.getLogs(),
                metadata,
                safeOperator));
        return new IngestionTaskRollbackResult(
                safeTaskId,
                STATUS_ROLLED_BACK,
                target.kbId(),
                target.docId(),
                "ingestion task rollback completed");
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
        PipelineDefinition pipeline = input.pipelineSnapshot() == null
                ? requirePipeline(input.pipelineId())
                : input.pipelineSnapshot();
        String taskId = taskRepositoryPort.createRunningTask(toCreateValues(input, pipeline));
        IngestionContext context = buildContext(taskId, input);
        IngestionContext result = runPipeline(pipeline, context);
        List<NodeLog> logs = Objects.requireNonNullElse(result.getLogs(), List.of());
        IngestionTaskUpdateValues values = toUpdateValues(result, input.operator());
        taskRepositoryPort.updateTask(taskId, values);
        taskRepositoryPort.replaceNodeLogs(taskId, toNodeValues(taskId, input, pipeline, logs));
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

    private IngestionTaskCreateValues toCreateValues(ExecutionInput input, PipelineDefinition pipeline) {
        IngestionDocumentSource source = input.source();
        return new IngestionTaskCreateValues(
                input.pipelineId(),
                source.type(),
                source.location(),
                source.fileName(),
                pipeline == null ? 0 : pipeline.getVersion(),
                pipelineSnapshot(pipeline),
                input.operator());
    }

    private IngestionContext buildContext(String taskId, ExecutionInput input) {
        IngestionContext context = IngestionContext.builder()
                .taskId(taskId)
                .pipelineId(input.pipelineId())
                .startNodeId(input.startNodeId())
                .source(input.source())
                .rawBytes(input.content().rawBytes())
                .mimeType(input.content().mimeType())
                .metadata(buildInitialMetadata(input))
                .vectorSpaceId(input.content().vectorSpaceId())
                .logs(new ArrayList<>())
                .build();
        applyRestoredOutputs(context, input.restoredNodeOutputs());
        return context;
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

    private RestoredNodeOutputs restoredNodeOutputs(String taskId, PipelineDefinition pipeline, String fromNodeId) {
        if (!hasText(fromNodeId)) {
            return new RestoredNodeOutputs(List.of(), Map.of());
        }
        Map<String, Integer> nodeOrderMap = buildNodeOrderMap(pipeline);
        Integer fromOrder = nodeOrderMap.get(fromNodeId);
        if (fromOrder == null) {
            throw new IllegalArgumentException("retry from node is not in pipeline snapshot: " + fromNodeId);
        }
        List<IngestionTaskNodeRecord> priorNodes = taskRepositoryPort.listNodes(taskId).stream()
                .filter(node -> node != null && node.getNodeOrder() > 0 && node.getNodeOrder() < fromOrder)
                .filter(node -> STATUS_SUCCESS.equalsIgnoreCase(node.getStatus()))
                .sorted((left, right) -> Integer.compare(left.getNodeOrder(), right.getNodeOrder()))
                .toList();
        List<String> nodeIds = priorNodes.stream()
                .map(IngestionTaskNodeRecord::getNodeId)
                .filter(this::hasText)
                .toList();
        Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
        for (IngestionTaskNodeRecord node : priorNodes) {
            Map<String, Object> output = Objects.requireNonNullElse(node.getOutput(), Map.of());
            if (hasText(node.getNodeId()) && !output.isEmpty()) {
                outputs.put(node.getNodeId(), output);
            }
        }
        return new RestoredNodeOutputs(nodeIds, outputs);
    }

    private IngestionTaskRollbackTarget rollbackTarget(IngestionTaskRecord record) {
        Map<String, Object> metadata = Objects.requireNonNullElse(record.getMetadata(), Map.of());
        Long docId = firstLong(metadata, KEY_DOC_ID, KEY_DOCUMENT_ID);
        Long kbId = firstLong(metadata, KEY_KB_ID, KEY_KNOWLEDGE_BASE_ID);
        if (docId == null || kbId == null) {
            throw new IllegalStateException("rollback requires docId and kbId metadata on task: " + record.getId());
        }
        return new IngestionTaskRollbackTarget(
                record.getId(),
                kbId,
                docId,
                textValue(metadata.get(KEY_COLLECTION_NAME), ""));
    }

    private Long firstLong(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Long value = longValue(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void applyRestoredOutputs(IngestionContext context, Map<String, Map<String, Object>> restoredNodeOutputs) {
        if (restoredNodeOutputs == null || restoredNodeOutputs.isEmpty()) {
            return;
        }
        for (Map<String, Object> output : restoredNodeOutputs.values()) {
            applyRestoredOutput(context, output);
        }
    }

    private void applyRestoredOutput(IngestionContext context, Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        String rawText = textValue(output.get("rawText"), null);
        if (hasText(rawText)) {
            context.setRawText(rawText);
        }
        String enhancedText = textValue(output.get("enhancedText"), null);
        if (hasText(enhancedText)) {
            context.setEnhancedText(enhancedText);
        }
        List<String> keywords = stringList(output.get(KEY_KEYWORDS));
        if (!keywords.isEmpty()) {
            context.setKeywords(keywords);
        }
        List<String> questions = stringList(output.get(KEY_QUESTIONS));
        if (!questions.isEmpty()) {
            context.setQuestions(questions);
        }
        Map<String, Object> metadata = mapValue(output.get("metadata"));
        if (!metadata.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(metadata);
            merged.putAll(Objects.requireNonNullElse(context.getMetadata(), Map.of()));
            context.setMetadata(merged);
        }
        Map<String, Object> normalizedMetadata = mapValue(output.get("normalizedMetadata"));
        if (!normalizedMetadata.isEmpty()) {
            context.setNormalizedMetadata(normalizedMetadata);
        }
        List<VectorChunk> chunks = vectorChunks(output.get("chunks"));
        if (!chunks.isEmpty()) {
            context.setChunks(chunks);
        }
        if (output.containsKey(KEY_VECTOR_SPACE_ID)) {
            context.setVectorSpaceId(output.get(KEY_VECTOR_SPACE_ID));
        }
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
                                                       ExecutionInput input,
                                                       PipelineDefinition pipeline,
                                                       List<NodeLog> logs) {
        Map<String, Integer> nodeOrderMap = buildNodeOrderMap(pipeline);
        return logs.stream()
                .map(log -> toNodeValue(taskId, input, nodeOrderMap, log))
                .toList();
    }

    private IngestionTaskNodeValues toNodeValue(String taskId,
                                                ExecutionInput input,
                                                Map<String, Integer> nodeOrderMap,
                                                NodeLog log) {
        NodeLog safeLog = log == null ? NodeLog.builder().success(false).build() : log;
        IngestionTaskNodeValues values = new IngestionTaskNodeValues();
        values.setTaskId(taskId);
        values.setPipelineId(input.pipelineId());
        values.setNodeId(safeLog.getNodeId());
        values.setNodeType(safeLog.getNodeType());
        values.setNodeOrder(nodeOrderMap.getOrDefault(safeLog.getNodeId(), 0));
        values.setStatus(nodeStatus(safeLog));
        values.setDurationMs(safeLog.getDurationMs());
        values.setInputSummary(inputSummary(input));
        values.setOutputSummary(outputSummary(safeLog.getOutput()));
        values.setErrorCode(errorCode(safeLog));
        values.setMessage(safeLog.getMessage());
        values.setErrorMessage(safeLog.getError());
        values.setRetryCount(retryCount(input));
        values.setDownstreamImpact(downstreamImpact(nodeOrderMap, safeLog));
        values.setOutput(safeLog.getOutput());
        return values;
    }

    private String inputSummary(ExecutionInput input) {
        List<String> parts = new ArrayList<>();
        IngestionDocumentSource source = input.source();
        parts.add("source=" + compact(source.type(), 128));
        if (hasText(source.location())) {
            parts.add("location=" + compact(source.location(), 256));
        }
        if (hasText(source.fileName())) {
            parts.add("file=" + compact(source.fileName(), 256));
        }
        byte[] rawBytes = input.content().rawBytes();
        if (rawBytes != null) {
            parts.add("bytes=" + rawBytes.length);
        }
        Map<String, Object> metadata = Objects.requireNonNullElse(input.content().metadata(), Map.of());
        Object retryOfTaskId = metadata.get(KEY_RETRY_OF_TASK_ID);
        if (retryOfTaskId != null) {
            parts.add("retryOf=" + compact(String.valueOf(retryOfTaskId), 128));
        }
        return compact(String.join(" ", parts), 1000);
    }

    private String outputSummary(Map<String, Object> output) {
        Map<String, Object> safeOutput = Objects.requireNonNullElse(output, Map.of());
        if (safeOutput.isEmpty()) {
            return null;
        }
        String summary = safeOutput.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + summarizeValue(entry.getValue()))
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        return compact(summary, 1000);
    }

    private String summarizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return compact(String.valueOf(value), 160);
        }
        if (value instanceof Map<?, ?> map) {
            return "map(" + map.size() + ")";
        }
        if (value instanceof Iterable<?> iterable) {
            int size = 0;
            for (Object ignored : iterable) {
                size++;
            }
            return "list(" + size + ")";
        }
        if (value instanceof byte[] bytes) {
            return "bytes(" + bytes.length + ")";
        }
        return compact(value.getClass().getSimpleName(), 160);
    }

    private String errorCode(NodeLog log) {
        if (log == null || log.isSuccess()) {
            return null;
        }
        String owner = hasText(log.getNodeType()) ? log.getNodeType() : log.getNodeId();
        String normalized = hasText(owner)
                ? owner.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                : "INGESTION";
        if (normalized.endsWith("_FAILED")) {
            return normalized;
        }
        return normalized + "_FAILED";
    }

    private int retryCount(ExecutionInput input) {
        Map<String, Object> metadata = Objects.requireNonNullElse(input.content().metadata(), Map.of());
        Object explicit = metadata.get("retryCount");
        if (explicit instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (explicit instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // Fall back to retry lineage evidence below.
            }
        }
        return metadata.containsKey(KEY_RETRY_OF_TASK_ID) ? 1 : 0;
    }

    private String downstreamImpact(Map<String, Integer> nodeOrderMap, NodeLog log) {
        if (log == null || log.isSuccess()) {
            return null;
        }
        int currentOrder = nodeOrderMap.getOrDefault(log.getNodeId(), 0);
        if (currentOrder <= 0) {
            return null;
        }
        List<String> downstreamNodes = nodeOrderMap.entrySet().stream()
                .filter(entry -> entry.getValue() > currentOrder)
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
        if (downstreamNodes.isEmpty()) {
            return null;
        }
        return compact(String.join("/", downstreamNodes) + " skipped", 1000);
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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

    private Map<String, Object> pipelineSnapshot(PipelineDefinition pipeline) {
        if (pipeline == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putText(snapshot, "id", pipeline.getId());
        putText(snapshot, "name", pipeline.getName());
        putText(snapshot, "description", pipeline.getDescription());
        snapshot.put("version", Math.max(0, pipeline.getVersion()));
        List<Map<String, Object>> nodes = Objects.requireNonNullElse(pipeline.getNodes(), List.<NodeConfig>of())
                .stream()
                .filter(Objects::nonNull)
                .map(this::nodeSnapshot)
                .toList();
        snapshot.put("nodes", nodes);
        return snapshot;
    }

    private Map<String, Object> nodeSnapshot(NodeConfig node) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putText(snapshot, "nodeId", node.getNodeId());
        putText(snapshot, "nodeType", node.getNodeType());
        putText(snapshot, "nextNodeId", node.getNextNodeId());
        if (node.getSettings() != null && !node.getSettings().isNull()) {
            snapshot.put("settings", node.getSettings());
        }
        if (node.getCondition() != null && !node.getCondition().isNull()) {
            snapshot.put("condition", node.getCondition());
        }
        return snapshot;
    }

    private PipelineDefinition pipelineFromSnapshot(IngestionTaskRecord record) {
        Map<String, Object> snapshot = Objects.requireNonNullElse(record.getPipelineSnapshot(), Map.of());
        if (snapshot.isEmpty()) {
            return null;
        }
        return PipelineDefinition.builder()
                .id(textValue(snapshot.get("id"), record.getPipelineId()))
                .name(textValue(snapshot.get("name"), null))
                .description(textValue(snapshot.get("description"), null))
                .version(intValue(snapshot.get("version"), record.getPipelineVersion()))
                .nodes(nodesFromSnapshot(snapshot.get("nodes")))
                .build();
    }

    private List<NodeConfig> nodesFromSnapshot(Object value) {
        if (!(value instanceof Iterable<?> rawNodes)) {
            return List.of();
        }
        List<NodeConfig> nodes = new ArrayList<>();
        for (Object rawNode : rawNodes) {
            if (!(rawNode instanceof Map<?, ?> nodeMap)) {
                continue;
            }
            String nodeId = textValue(nodeMap.get("nodeId"), null);
            if (!hasText(nodeId)) {
                continue;
            }
            nodes.add(NodeConfig.builder()
                    .nodeId(nodeId)
                    .nodeType(textValue(nodeMap.get("nodeType"), null))
                    .nextNodeId(textValue(nodeMap.get("nextNodeId"), null))
                    .settings(jsonNode(nodeMap.get("settings")))
                    .condition(jsonNode(nodeMap.get("condition")))
                    .build());
        }
        return nodes;
    }

    private JsonNode jsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return OBJECT_MAPPER.valueToTree(value);
    }

    private String textValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return hasText(text) ? text : fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return Math.max(0, fallback);
            }
        }
        return Math.max(0, fallback);
    }

    private String normalizeOptionalText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private List<String> stringList(Object value) {
        if (value == null || isNullJson(value)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (value instanceof CharSequence text) {
            addText(values, text);
            return values;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addText(values, item);
            }
            return values;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                addText(values, Array.get(value, index));
            }
            return values;
        }
        try {
            List<String> converted = OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
            });
            for (String item : Objects.requireNonNullElse(converted, List.<String>of())) {
                addText(values, item);
            }
            return values;
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private void addText(List<String> values, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (hasText(text)) {
            values.add(text.trim());
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (value == null || isNullJson(value)) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }
        try {
            Map<String, Object> converted = OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
            });
            return converted == null ? Map.of() : new LinkedHashMap<>(converted);
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private List<VectorChunk> vectorChunks(Object value) {
        if (value == null || isNullJson(value)) {
            return List.of();
        }
        if (value instanceof VectorChunk chunk) {
            return List.of(chunk);
        }
        try {
            List<VectorChunk> converted = OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
            });
            if (converted == null || converted.isEmpty()) {
                return List.of();
            }
            return converted.stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private boolean isNullJson(Object value) {
        return value instanceof JsonNode jsonNode && jsonNode.isNull();
    }

    private void putText(Map<String, Object> values, String key, String value) {
        if (hasText(value)) {
            values.put(key, value.trim());
        }
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
        return Objects.requireNonNullElse(source.location(), "").getBytes(StandardCharsets.UTF_8);
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
            String operator,
            PipelineDefinition pipelineSnapshot,
            String startNodeId,
            Map<String, Map<String, Object>> restoredNodeOutputs
    ) {
    }

    private record RestoredNodeOutputs(
            List<String> nodeIds,
            Map<String, Map<String, Object>> outputs
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
