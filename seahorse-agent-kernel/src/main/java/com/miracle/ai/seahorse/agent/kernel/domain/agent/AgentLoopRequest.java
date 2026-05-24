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

package com.miracle.ai.seahorse.agent.kernel.domain.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;

import java.util.List;
import java.util.Objects;

/**
 * Agent ReAct 循环的入参对象。
 *
 * <p>不可变；通过 {@link #builder()} 构造。
 */
public final class AgentLoopRequest {

    private static final int DEFAULT_MAX_STEPS = 6;

    private final String question;
    private final List<ChatMessage> history;
    private final List<String> allowedToolIds;
    private final ChatSamplingOptions samplingOptions;
    private final int maxSteps;
    private final ContextPack contextPack;
    private final MemoryContext memoryContext;
    // 以下上下文字段由 Agent Runtime 传入 Tool Gateway，用于策略、审计和资源权限判断。
    private final String runId;
    private final String agentId;
    private final String versionId;
    private final String tenantId;
    private final String userId;
    private final String agentIdentityId;
    // Phase D Slice 1a 起：通过 expected* 字段驱动输出治理；默认 null 表示按 PLAIN_TEXT 走，不做结构校验。
    private final OutputArtifactType expectedOutputArtifactType;
    private final String expectedOutputSchemaJson;

    private AgentLoopRequest(Builder b) {
        if (b.question == null || b.question.isBlank()) {
            throw new IllegalArgumentException("AgentLoopRequest.question 不能为空");
        }
        Objects.requireNonNull(b.samplingOptions, "AgentLoopRequest.samplingOptions 不能为空");
        this.question = b.question;
        this.history = b.history == null ? List.of() : List.copyOf(b.history);
        this.allowedToolIds = b.allowedToolIds == null ? List.of() : List.copyOf(b.allowedToolIds);
        this.samplingOptions = b.samplingOptions;
        this.maxSteps = b.maxSteps <= 0 ? DEFAULT_MAX_STEPS : b.maxSteps;
        this.contextPack = b.contextPack;
        this.memoryContext = b.memoryContext;
        this.runId = trimToNull(b.runId);
        this.agentId = defaultText(b.agentId, AgentRuntimeConstants.LEGACY_REACT_AGENT_ID);
        this.versionId = trimToNull(b.versionId);
        this.tenantId = defaultText(b.tenantId, AgentDefinition.DEFAULT_TENANT_ID);
        this.userId = defaultText(b.userId, defaultUserId(contextPack, memoryContext));
        this.agentIdentityId = defaultText(b.agentIdentityId, this.userId);
        this.expectedOutputArtifactType = b.expectedOutputArtifactType;
        this.expectedOutputSchemaJson = trimToNull(b.expectedOutputSchemaJson);
    }

    public String question() {
        return question;
    }

    public List<ChatMessage> history() {
        return history;
    }

    public List<String> allowedToolIds() {
        return allowedToolIds;
    }

    public ChatSamplingOptions samplingOptions() {
        return samplingOptions;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public ContextPack contextPack() {
        return contextPack;
    }

    public MemoryContext memoryContext() {
        return memoryContext;
    }

    public String runId() {
        return runId;
    }

    public String agentId() {
        return agentId;
    }

    public String versionId() {
        return versionId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public String agentIdentityId() {
        return agentIdentityId;
    }

    public OutputArtifactType expectedOutputArtifactType() {
        return expectedOutputArtifactType;
    }

    public String expectedOutputSchemaJson() {
        return expectedOutputSchemaJson;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String question;
        private List<ChatMessage> history;
        private List<String> allowedToolIds;
        private ChatSamplingOptions samplingOptions;
        private int maxSteps;
        private ContextPack contextPack;
        private MemoryContext memoryContext;
        private String runId;
        private String agentId;
        private String versionId;
        private String tenantId;
        private String userId;
        private String agentIdentityId;
        private OutputArtifactType expectedOutputArtifactType;
        private String expectedOutputSchemaJson;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder history(List<ChatMessage> history) {
            this.history = history;
            return this;
        }

        public Builder allowedToolIds(List<String> allowedToolIds) {
            this.allowedToolIds = allowedToolIds;
            return this;
        }

        public Builder samplingOptions(ChatSamplingOptions samplingOptions) {
            this.samplingOptions = samplingOptions;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder contextPack(ContextPack contextPack) {
            this.contextPack = contextPack;
            return this;
        }

        public Builder memoryContext(MemoryContext memoryContext) {
            this.memoryContext = memoryContext;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder versionId(String versionId) {
            this.versionId = versionId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentIdentityId(String agentIdentityId) {
            this.agentIdentityId = agentIdentityId;
            return this;
        }

        public Builder expectedOutputArtifactType(OutputArtifactType expectedOutputArtifactType) {
            this.expectedOutputArtifactType = expectedOutputArtifactType;
            return this;
        }

        public Builder expectedOutputSchemaJson(String expectedOutputSchemaJson) {
            this.expectedOutputSchemaJson = expectedOutputSchemaJson;
            return this;
        }

        public AgentLoopRequest build() {
            return new AgentLoopRequest(this);
        }
    }

    private static String defaultUserId(ContextPack contextPack, MemoryContext memoryContext) {
        if (memoryContext != null) {
            return memoryContext.getUserId();
        }
        return contextPack == null ? "" : contextPack.userId();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? Objects.requireNonNullElse(fallback, "") : trimmed;
    }
}
