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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import io.agentscope.core.studio.StudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.Objects;

@SuppressWarnings("removal")
public class AgentScopeStudioLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(AgentScopeStudioLifecycle.class);

    private final AgentScopeProperties properties;
    private volatile Disposable initialization;
    private volatile boolean startedBySeahorse;

    public AgentScopeStudioLifecycle(AgentScopeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public synchronized void start() {
        AgentScopeProperties.Studio studio = properties.getStudio();
        if (!studio.isAutoInitialize()) {
            return;
        }
        if (StudioManager.isInitialized()) {
            updateRuntimeRunId(studio);
            return;
        }
        studio.setRuntimeRunId("");
        StudioManager.Builder builder = StudioManager.init()
                .project(textOrDefault(studio.getProject(), "seahorse-agent"))
                .runName(textOrDefault(studio.getRunName(), "seahorse-agent"))
                .maxRetries(nonNegative(studio.getMaxRetries()))
                .reconnectAttempts(nonNegative(studio.getReconnectAttempts()));
        if (!isBlank(studio.getStudioUrl())) {
            builder.studioUrl(studio.getStudioUrl().trim());
        }
        if (!isBlank(studio.getTracingUrl())) {
            builder.tracingUrl(studio.getTracingUrl().trim());
        }
        startedBySeahorse = true;
        initialization = builder.initialize()
                .doOnSuccess(ignored -> {
                    updateRuntimeRunId(studio);
                    LOG.info("AgentScope Studio initialized for project {}", textOrDefault(studio.getProject(),
                            "seahorse-agent"));
                })
                .doOnError(error -> {
                    startedBySeahorse = false;
                    studio.setRuntimeRunId("");
                    LOG.warn("AgentScope Studio initialization failed; continuing without Studio", error);
                })
                .onErrorComplete()
                .subscribe();
        // The SDK creates its immutable run id before the asynchronous registration completes.
        updateRuntimeRunId(studio);
    }

    public synchronized void stop() {
        Disposable pendingInitialization = initialization;
        initialization = null;
        if (pendingInitialization != null) {
            pendingInitialization.dispose();
        }
        if (startedBySeahorse && StudioManager.isInitialized()) {
            StudioManager.shutdown();
        }
        startedBySeahorse = false;
        properties.getStudio().setRuntimeRunId("");
    }

    private static void updateRuntimeRunId(AgentScopeProperties.Studio studio) {
        var config = StudioManager.getConfig();
        studio.setRuntimeRunId(config == null ? "" : config.getRunId());
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    private static String textOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
