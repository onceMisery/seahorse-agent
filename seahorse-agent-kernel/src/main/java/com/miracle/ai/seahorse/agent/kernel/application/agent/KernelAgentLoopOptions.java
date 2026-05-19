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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import java.time.Duration;
import java.util.Objects;

/**
 * KernelAgentLoop 配置项：循环上限、单工具超时、最大并发工具数。
 */
public final class KernelAgentLoopOptions {

    private static final int DEFAULT_MAX_STEPS = 6;
    private static final Duration DEFAULT_PER_TOOL_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_PARALLEL_TOOLS = 4;

    private final int maxSteps;
    private final Duration perToolTimeout;
    private final int maxParallelTools;

    private KernelAgentLoopOptions(Builder b) {
        if (b.maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps 必须 > 0");
        }
        if (b.maxParallelTools <= 0) {
            throw new IllegalArgumentException("maxParallelTools 必须 > 0");
        }
        Objects.requireNonNull(b.perToolTimeout, "perToolTimeout 不能为 null");
        if (b.perToolTimeout.isZero() || b.perToolTimeout.isNegative()) {
            throw new IllegalArgumentException("perToolTimeout 必须 > 0");
        }
        this.maxSteps = b.maxSteps;
        this.perToolTimeout = b.perToolTimeout;
        this.maxParallelTools = b.maxParallelTools;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public Duration perToolTimeout() {
        return perToolTimeout;
    }

    public int maxParallelTools() {
        return maxParallelTools;
    }

    public static KernelAgentLoopOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxSteps = DEFAULT_MAX_STEPS;
        private Duration perToolTimeout = DEFAULT_PER_TOOL_TIMEOUT;
        private int maxParallelTools = DEFAULT_MAX_PARALLEL_TOOLS;

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder perToolTimeout(Duration perToolTimeout) {
            this.perToolTimeout = perToolTimeout;
            return this;
        }

        public Builder maxParallelTools(int maxParallelTools) {
            this.maxParallelTools = maxParallelTools;
            return this;
        }

        public KernelAgentLoopOptions build() {
            return new KernelAgentLoopOptions(this);
        }
    }
}
