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
    private final List<String> tools;
    private final ChatSamplingOptions samplingOptions;
    private final int maxSteps;
    private final MemoryContext memoryContext;

    private AgentLoopRequest(Builder b) {
        if (b.question == null || b.question.isBlank()) {
            throw new IllegalArgumentException("AgentLoopRequest.question 不能为空");
        }
        Objects.requireNonNull(b.samplingOptions, "AgentLoopRequest.samplingOptions 不能为空");
        this.question = b.question;
        this.history = b.history == null ? List.of() : List.copyOf(b.history);
        this.tools = b.tools == null ? List.of() : List.copyOf(b.tools);
        this.samplingOptions = b.samplingOptions;
        this.maxSteps = b.maxSteps <= 0 ? DEFAULT_MAX_STEPS : b.maxSteps;
        this.memoryContext = b.memoryContext;
    }

    public String question() {
        return question;
    }

    public List<ChatMessage> history() {
        return history;
    }

    public List<String> tools() {
        return tools;
    }

    public ChatSamplingOptions samplingOptions() {
        return samplingOptions;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public MemoryContext memoryContext() {
        return memoryContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String question;
        private List<ChatMessage> history;
        private List<String> tools;
        private ChatSamplingOptions samplingOptions;
        private int maxSteps;
        private MemoryContext memoryContext;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder history(List<ChatMessage> history) {
            this.history = history;
            return this;
        }

        public Builder tools(List<String> tools) {
            this.tools = tools;
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

        public Builder memoryContext(MemoryContext memoryContext) {
            this.memoryContext = memoryContext;
            return this;
        }

        public AgentLoopRequest build() {
            return new AgentLoopRequest(this);
        }
    }
}
