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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class ReActAgentScopeAgentClient implements AgentScopeAgentClient {

    private final AgentScopeModelFactory modelFactory;
    private final AgentScopeProperties properties;
    private final AgentScopeToolFactory toolFactory;

    public ReActAgentScopeAgentClient(Model model, AgentScopeProperties properties) {
        this(request -> Objects.requireNonNull(model, "model must not be null"), properties);
    }

    public ReActAgentScopeAgentClient(Model model, AgentScopeProperties properties, AgentScopeToolFactory toolFactory) {
        this(request -> Objects.requireNonNull(model, "model must not be null"), properties, toolFactory);
    }

    public ReActAgentScopeAgentClient(AgentScopeModelFactory modelFactory, AgentScopeProperties properties) {
        this(modelFactory, properties, null);
    }

    public ReActAgentScopeAgentClient(
            AgentScopeModelFactory modelFactory,
            AgentScopeProperties properties,
            AgentScopeToolFactory toolFactory) {
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.toolFactory = toolFactory;
    }

    @Override
    public Msg call(AgentLoopRequest request, List<Msg> messages) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        ReActAgent agent = agent(safeRequest);
        try (agent) {
            return agent.call(messages).block(timeout(properties.getExecutor().getTimeout()));
        }
    }

    @Override
    public Flux<AgentEvent> stream(AgentLoopRequest request, List<Msg> messages) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        ReActAgent agent = agent(safeRequest);
        return agent.streamEvents(messages, RuntimeContext.empty())
                .doFinally(ignored -> agent.close());
    }

    private ReActAgent agent(AgentLoopRequest request) {
        AgentScopeProperties.Executor executor = properties.getExecutor();
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(textOrDefault(executor.getAgentName(), request.agentId()))
                .sysPrompt(executor.getSystemPrompt())
                .model(modelFactory.modelFor(request))
                .maxIters(request.maxSteps())
                .generateOptions(generateOptions(request));
        if (toolFactory != null) {
            Toolkit toolkit = toolFactory.toolkitFor(request);
            if (!toolkit.getToolNames().isEmpty()) {
                builder.toolkit(toolkit);
            }
        }
        return builder.build();
    }

    private GenerateOptions generateOptions(AgentLoopRequest request) {
        GenerateOptions.Builder builder = GenerateOptions.builder()
                .modelName(request.modelId());
        if (request.samplingOptions() != null) {
            builder.temperature(request.samplingOptions().getTemperature())
                    .topP(request.samplingOptions().getTopP())
                    .topK(request.samplingOptions().getTopK())
                    .maxTokens(request.samplingOptions().getMaxTokens());
        }
        return builder.build();
    }

    private Duration timeout(Duration value) {
        return value == null || value.isNegative() || value.isZero() ? Duration.ofMinutes(2) : value;
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "seahorse-agent") : value;
    }
}
