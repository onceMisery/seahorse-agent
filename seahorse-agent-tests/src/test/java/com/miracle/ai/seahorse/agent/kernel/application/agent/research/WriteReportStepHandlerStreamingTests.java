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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WriteReportStepHandlerStreamingTests {

    @Test
    void streamingReportPublishesArtifactLifecycleBeforePersistedArtifactConfirmation() {
        CapturingStorage storage = new CapturingStorage();
        CapturingArtifactRepository repository = new CapturingArtifactRepository();
        StreamingChatModelPort streamingModel = (request, callback) -> {
            callback.onContent("## Summary\n\n");
            callback.onContent("Evidence-backed answer.");
            callback.onComplete();
            return () -> {
            };
        };
        WriteReportStepHandler handler = new WriteReportStepHandler(
                ChatModelPort.noop(), streamingModel, storage, repository);
        ResearchStepContext context = new ResearchStepContext("run-stream", "q", 0);
        context.setTenantId("tenant");
        context.setUserId("user");
        List<PublishedEvent> events = new ArrayList<>();

        handler.execute(task("run-stream"), context, (runId, ctx, type, payload) ->
                events.add(new PublishedEvent(type, payload)));

        assertEquals("## Summary\n\nEvidence-backed answer.", context.reportContent());
        assertEquals(context.artifactId(), repository.saved.artifactId());
        assertEquals(storage.stored.url(), repository.saved.storageRef());
        assertEquals(List.of(
                StreamEventType.ARTIFACT_START,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_END), events.stream().map(PublishedEvent::type).toList());
        assertEquals(context.artifactId(), stringPayload(events.get(0), "artifactId"));
        assertEquals(context.artifactId(), stringPayload(events.get(1), "artifactId"));
        assertEquals(context.artifactId(), stringPayload(events.get(2), "artifactId"));
        assertEquals("## Summary\n\nEvidence-backed answer.", stringPayload(events.get(1), "delta"));
        assertEquals(storage.stored.url(), stringPayload(events.get(2), "storageRef"));
        assertEquals(35, numericPayload(events.get(2), "totalChars"));
    }

    @Test
    void blockingReportStillPublishesArtifactLifecycleForCompatibility() {
        CapturingStorage storage = new CapturingStorage();
        CapturingArtifactRepository repository = new CapturingArtifactRepository();
        ChatModelPort chatModel = (request, modelId) -> "final report";
        WriteReportStepHandler handler = new WriteReportStepHandler(chatModel, storage, repository);
        ResearchStepContext context = new ResearchStepContext("run-blocking", "q", 0);
        List<PublishedEvent> events = new ArrayList<>();

        handler.execute(task("run-blocking"), context, (runId, ctx, type, payload) ->
                events.add(new PublishedEvent(type, payload)));

        assertEquals("final report", context.reportContent());
        assertNotNull(context.artifactId());
        assertEquals(List.of(
                StreamEventType.ARTIFACT_START,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_END), events.stream().map(PublishedEvent::type).toList());
        assertEquals("final report", stringPayload(events.get(1), "delta"));
    }

    private static DurableTask task(String runId) {
        return new DurableTask("task-1", runId, "WRITE_REPORT", 0, Instant.now(), null, null);
    }

    private static String stringPayload(PublishedEvent event, String key) {
        assertInstanceOf(Map.class, event.payload());
        Object value = ((Map<?, ?>) event.payload()).get(key);
        assertNotNull(value, "missing payload key " + key);
        return String.valueOf(value);
    }

    private static Number numericPayload(PublishedEvent event, String key) {
        assertInstanceOf(Map.class, event.payload());
        Object value = ((Map<?, ?>) event.payload()).get(key);
        assertInstanceOf(Number.class, value);
        return (Number) value;
    }

    private record PublishedEvent(StreamEventType type, Object payload) {
    }

    private static final class CapturingStorage implements ObjectStoragePort {

        private StoredObject stored;

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
            stored = new StoredObject("storage://" + originalFilename, contentType, size, originalFilename);
            return stored;
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }

    private static final class CapturingArtifactRepository implements AgentArtifactRepositoryPort {

        private AgentArtifact saved;

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            saved = artifact;
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.ofNullable(saved).filter(artifact -> artifact.artifactId().equals(artifactId));
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return saved != null && saved.runId().equals(runId) ? List.of(saved) : List.of();
        }
    }
}
