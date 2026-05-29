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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * WRITE_REPORT step: generate and persist a Markdown research report.
 */
public class WriteReportStepHandler implements ResearchStepHandler {

    private static final String WRITE_SYSTEM_PROMPT = """
            你是一个研究报告撰写助手。根据提供的研究大纲、证据和来源信息，
            撰写一份完整的 Markdown 格式研究报告。要求：
            1. 包含标题、摘要、正文和参考来源
            2. 正文中使用 [1], [2] 等标注引用来源
            3. 末尾列出所有参考来源的标题和 URL
            4. 语言流畅，逻辑清晰
            """;

    private static final String ARTIFACT_BUCKET = "research-artifacts";
    private static final String ARTIFACT_TITLE = "研究报告";
    private static final String ARTIFACT_MIME_TYPE = "text/markdown";
    private static final String ARTIFACT_STREAM_TYPE = "MARKDOWN_REPORT";
    private static final int ARTIFACT_CONTENT_CHUNK_CHARS = 350;
    private static final long ARTIFACT_STREAM_TIMEOUT_MINUTES = 10;

    private final ChatModelPort chatModel;
    private final StreamingChatModelPort streamingChatModel;
    private final ObjectStoragePort objectStorage;
    private final AgentArtifactRepositoryPort artifactRepository;

    public WriteReportStepHandler(ChatModelPort chatModel,
                                  ObjectStoragePort objectStorage,
                                  AgentArtifactRepositoryPort artifactRepository) {
        this(chatModel, null, objectStorage, artifactRepository);
    }

    public WriteReportStepHandler(ChatModelPort chatModel,
                                  StreamingChatModelPort streamingChatModel,
                                  ObjectStoragePort objectStorage,
                                  AgentArtifactRepositoryPort artifactRepository) {
        this.chatModel = Objects.requireNonNull(chatModel);
        this.streamingChatModel = streamingChatModel;
        this.objectStorage = Objects.requireNonNull(objectStorage);
        this.artifactRepository = Objects.requireNonNull(artifactRepository);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.WRITE_REPORT;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        execute(task, context, ResearchEventPublisher.noop());
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context, ResearchEventPublisher events) {
        ChatRequest request = buildRequest(context);
        String artifactId = UUID.randomUUID().toString();
        String report = streamingChatModel == null
                ? generateBlockingReport(request, task, context, events, artifactId)
                : generateStreamingReport(request, task, context, events, artifactId);

        if (report != null && !report.isBlank()) {
            context.setReportContent(report);
            AgentArtifact artifact = publishArtifact(task.runId(), context, artifactId, report);
            emitArtifactEnd(task, context, events, artifact, report.length());
        }
    }

    private ChatRequest buildRequest(ResearchStepContext context) {
        String outline = Objects.requireNonNullElse(context.reportContent(), "");
        String evidenceText = context.evidence().stream()
                .map(e -> "[" + e.citationIndex() + "] " + e.claim())
                .collect(Collectors.joining("\n"));
        String sourcesText = context.sources().stream()
                .map(s -> "- " + s.title() + " (" + s.url() + ")")
                .collect(Collectors.joining("\n"));

        String prompt = "研究问题：" + context.query()
                + "\n\n研究大纲：\n" + outline
                + "\n\n证据：\n" + evidenceText
                + "\n\n来源：\n" + sourcesText;

        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(WRITE_SYSTEM_PROMPT),
                        ChatMessage.user(prompt)))
                .build();
    }

    private String generateBlockingReport(ChatRequest request, DurableTask task, ResearchStepContext context,
                                          ResearchEventPublisher events, String artifactId) {
        String report = chatModel.chat(request, request.getModelId());
        if (report != null && !report.isBlank()) {
            emitArtifactStart(task, context, events, artifactId);
            emitArtifactContent(task, context, events, artifactId, report);
        }
        return report;
    }

    private String generateStreamingReport(ChatRequest request, DurableTask task, ResearchStepContext context,
                                           ResearchEventPublisher events, String artifactId) {
        Object lock = new Object();
        StringBuilder report = new StringBuilder();
        StringBuilder pendingChunk = new StringBuilder();
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        StreamCancellationHandle cancellationHandle = streamingChatModel.streamChat(request, new StreamCallback() {
            @Override
            public void onContent(String content) {
                if (content == null || content.isBlank()) {
                    return;
                }
                synchronized (lock) {
                    if (started.compareAndSet(false, true)) {
                        emitArtifactStart(task, context, events, artifactId);
                    }
                    report.append(content);
                    pendingChunk.append(content);
                    if (pendingChunk.length() >= ARTIFACT_CONTENT_CHUNK_CHARS) {
                        flushArtifactContent(task, context, events, artifactId, pendingChunk);
                    }
                }
            }

            @Override
            public void onComplete() {
                synchronized (lock) {
                    flushArtifactContent(task, context, events, artifactId, pendingChunk);
                }
                completed.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }
        });

        try {
            if (!completed.await(ARTIFACT_STREAM_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                if (cancellationHandle != null) {
                    cancellationHandle.cancel();
                }
                throw new RetryableResearchException("Timed out while streaming research report");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (cancellationHandle != null) {
                cancellationHandle.cancel();
            }
            throw new RetryableResearchException("Interrupted while streaming research report", e);
        }

        Throwable failure = error.get();
        if (failure != null) {
            throw new RetryableResearchException(
                    "Research report streaming failed: " + Objects.requireNonNullElse(
                            failure.getMessage(), failure.getClass().getSimpleName()), failure);
        }
        return report.toString();
    }

    private void emitArtifactStart(DurableTask task, ResearchStepContext context, ResearchEventPublisher events,
                                   String artifactId) {
        events.publish(task.runId(), context, StreamEventType.ARTIFACT_START, Map.of(
                "artifactId", artifactId,
                "artifactType", ARTIFACT_STREAM_TYPE,
                "title", ARTIFACT_TITLE,
                "mimeType", ARTIFACT_MIME_TYPE));
    }

    private void flushArtifactContent(DurableTask task, ResearchStepContext context, ResearchEventPublisher events,
                                      String artifactId, StringBuilder pendingChunk) {
        if (pendingChunk.isEmpty()) {
            return;
        }
        emitArtifactContent(task, context, events, artifactId, pendingChunk.toString());
        pendingChunk.setLength(0);
    }

    private void emitArtifactContent(DurableTask task, ResearchStepContext context, ResearchEventPublisher events,
                                     String artifactId, String delta) {
        events.publish(task.runId(), context, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", artifactId,
                "delta", delta));
    }

    private void emitArtifactEnd(DurableTask task, ResearchStepContext context, ResearchEventPublisher events,
                                 AgentArtifact artifact, int totalChars) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactId", artifact.artifactId());
        payload.put("artifactType", ARTIFACT_STREAM_TYPE);
        payload.put("title", artifact.title());
        payload.put("mimeType", artifact.mimeType());
        payload.put("storageRef", artifact.storageRef());
        payload.put("scanStatus", artifact.scanStatus().name());
        payload.put("totalChars", totalChars);
        events.publish(task.runId(), context, StreamEventType.ARTIFACT_END, payload);
    }

    private AgentArtifact publishArtifact(String runId, ResearchStepContext context, String artifactId, String report) {
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
        String filename = "research-report-" + runId + ".md";
        StoredObject stored = objectStorage.upload(
                ARTIFACT_BUCKET,
                new ByteArrayInputStream(bytes),
                bytes.length,
                filename,
                ARTIFACT_MIME_TYPE);

        String previewText = report.length() > 200 ? report.substring(0, 200) + "..." : report;
        AgentArtifact artifact = new AgentArtifact(
                artifactId,
                runId,
                null,
                Objects.requireNonNullElse(context.tenantId(), "default"),
                Objects.requireNonNullElse(context.userId(), "system"),
                AgentArtifactType.REPORT,
                ARTIFACT_TITLE,
                ARTIFACT_MIME_TYPE,
                stored.url(),
                previewText,
                null,
                AgentArtifactScanStatus.CLEAN,
                Instant.now());

        AgentArtifact saved = artifactRepository.save(artifact);
        context.setArtifactId(artifactId);
        return saved;
    }
}
