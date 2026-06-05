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

package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages subscriptions for real-time workflow step updates.
 *
 * <p>Each workflow run can have multiple subscribers. When a step
 * status changes, all subscribers for that run receive an event via
 * the {@link WorkflowStepEventListener} callback.
 *
 * <p>Subscribers are automatically removed when their listener signals
 * a closed connection (by returning {@code false} from
 * {@link WorkflowStepEventListener#onStepUpdate}).
 */
public class WorkflowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventPublisher.class);

    private final ConcurrentHashMap<String, List<WorkflowStepEventListener>> subscribers =
            new ConcurrentHashMap<>();

    /**
     * Register a listener as a subscriber for the given workflow run.
     *
     * @param runId    the workflow run identifier
     * @param listener the event listener to add
     */
    public void addSubscriber(String runId, WorkflowStepEventListener listener) {
        subscribers.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("Subscriber added for runId [{}], total: {}",
                runId, subscribers.getOrDefault(runId, List.of()).size());
    }

    /**
     * Remove a listener from the subscriber list for the given run.
     *
     * @param runId    the workflow run identifier
     * @param listener the event listener to remove
     */
    public void removeSubscriber(String runId, WorkflowStepEventListener listener) {
        List<WorkflowStepEventListener> listeners = subscribers.get(runId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                subscribers.remove(runId);
            }
        }
        log.debug("Subscriber removed for runId [{}]", runId);
    }

    /**
     * Publish a step status update to all subscribers of the given run.
     *
     * @param runId  the workflow run identifier
     * @param stepId the step that changed
     * @param status the new status of the step
     */
    public void publishStepUpdate(String runId, String stepId, String status) {
        List<WorkflowStepEventListener> listeners = subscribers.get(runId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        Map<String, String> payload = Map.of(
                "runId", runId,
                "stepId", stepId,
                "status", status
        );

        for (WorkflowStepEventListener listener : listeners) {
            try {
                boolean alive = listener.onStepUpdate(runId, stepId, status, payload);
                if (!alive) {
                    removeSubscriber(runId, listener);
                }
            } catch (Exception ex) {
                log.warn("Failed to notify listener for runId [{}], stepId [{}]: {}",
                        runId, stepId, ex.getMessage());
                removeSubscriber(runId, listener);
            }
        }
    }

    /**
     * Return the number of active subscribers for the given run.
     *
     * @param runId the workflow run identifier
     * @return the subscriber count
     */
    public int subscriberCount(String runId) {
        List<WorkflowStepEventListener> listeners = subscribers.get(runId);
        return listeners != null ? listeners.size() : 0;
    }

    /**
     * Callback interface for workflow step events.
     *
     * <p>Implementations in adapter layers bridge to SSE, WebSocket, or
     * other push mechanisms.
     */
    @FunctionalInterface
    public interface WorkflowStepEventListener {

        /**
         * Called when a step status changes.
         *
         * @param runId   the workflow run identifier
         * @param stepId  the step that changed
         * @param status  the new status
         * @param payload the full event payload
         * @return {@code true} if the listener is still alive, {@code false} to unsubscribe
         */
        boolean onStepUpdate(String runId, String stepId, String status,
                             Map<String, String> payload);
    }
}
