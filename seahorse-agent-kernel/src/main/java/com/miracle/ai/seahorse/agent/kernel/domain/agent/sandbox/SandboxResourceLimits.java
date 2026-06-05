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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

/**
 * Hard resource limits enforced by the sandbox runtime.
 *
 * @param maxMemoryBytes   maximum heap/off-heap memory in bytes
 * @param maxCpuMillis     maximum CPU time in milliseconds
 * @param maxWallClockMillis maximum wall-clock duration in milliseconds
 */
public record SandboxResourceLimits(long maxMemoryBytes, long maxCpuMillis, long maxWallClockMillis) {

    /**
     * Returns sensible defaults: 512 MB memory, 30 s CPU, 60 s wall-clock.
     */
    public static SandboxResourceLimits defaults() {
        return new SandboxResourceLimits(512L * 1024 * 1024, 30_000L, 60_000L);
    }
}
