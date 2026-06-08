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

import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowEventPublisher;
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort.WorkflowVisualization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * REST controller for workflow visualization and SSE streaming.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code GET /api/workflows/runs/{runId}/visualization} — full DAG snapshot</li>
 *   <li>{@code GET /api/workflows/runs/{runId}/stream} — real-time SSE stream</li>
 * </ul>
 */
@RestController
public class SeahorseWorkflowVisualizationController {

    private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

    private final ObjectProvider<WorkflowVisualizationInboundPort> visualizationPortProvider;
    private final ObjectProvider<WorkflowEventPublisher> eventPublisherProvider;

    public SeahorseWorkflowVisualizationController(
            ObjectProvider<WorkflowVisualizationInboundPort> visualizationPortProvider,
            ObjectProvider<WorkflowEventPublisher> eventPublisherProvider) {
        this.visualizationPortProvider = visualizationPortProvider;
        this.eventPublisherProvider = eventPublisherProvider;
    }

    /**
     * Retrieve the full workflow visualization (nodes + edges) for a run.
     */
    @GetMapping("/api/workflows/runs/{runId}/visualization")
    public WorkflowVisualization getVisualization(@PathVariable String runId) {
        WorkflowVisualizationInboundPort port = visualizationPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("Workflow visualization is not available");
        }
        return port.getVisualization(runId);
    }

    /**
     * SSE endpoint that streams real-time workflow step updates.
     *
     * <p>The client receives {@code step-update} events whenever a step
     * transitions between statuses. The connection is kept alive until
     * the workflow completes or the client disconnects.
     */
    @GetMapping(value = "/api/workflows/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWorkflowUpdates(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        WorkflowEventPublisher publisher = eventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("error", "Workflow streaming is not available")));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
            return emitter;
        }

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("runId", runId)));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        WorkflowEventPublisher.WorkflowStepEventListener listener =
                (evtRunId, stepId, status, payload) -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("step-update")
                                .data(payload));
                        return true;
                    } catch (IOException ex) {
                        return false;
                    }
                };

        publisher.addSubscriber(runId, listener);

        emitter.onCompletion(() -> publisher.removeSubscriber(runId, listener));
        emitter.onTimeout(() -> publisher.removeSubscriber(runId, listener));
        emitter.onError(ex -> publisher.removeSubscriber(runId, listener));

        return emitter;
    }
}
