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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

/**
 * 短期记忆衰减维护策略。
 */
public record MemoryDecayOptions(int scanLimit,
                                 double decayThreshold,
                                 boolean dryRun) {

    private static final int DEFAULT_SCAN_LIMIT = 500;
    private static final double DEFAULT_DECAY_THRESHOLD = 0.1D;

    public MemoryDecayOptions {
        scanLimit = scanLimit > 0 ? scanLimit : DEFAULT_SCAN_LIMIT;
        decayThreshold = decayThreshold >= 0D ? decayThreshold : DEFAULT_DECAY_THRESHOLD;
    }

    public static MemoryDecayOptions defaults() {
        return new MemoryDecayOptions(DEFAULT_SCAN_LIMIT, DEFAULT_DECAY_THRESHOLD, false);
    }
}
