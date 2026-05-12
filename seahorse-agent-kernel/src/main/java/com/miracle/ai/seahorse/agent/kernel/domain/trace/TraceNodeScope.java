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

package com.miracle.ai.seahorse.agent.kernel.domain.trace;

import java.time.Instant;

/**
 * Trace node 生命周期句柄。
 */
public record TraceNodeScope(String traceId, String nodeId, Instant startTime, boolean active) {

    private static final TraceNodeScope DISABLED = new TraceNodeScope(null, null, null, false);

    public static TraceNodeScope active(String traceId, String nodeId, Instant startTime) {
        return new TraceNodeScope(traceId, nodeId, startTime,
                traceId != null && !traceId.isBlank() && nodeId != null && !nodeId.isBlank());
    }

    public static TraceNodeScope disabled() {
        return DISABLED;
    }
}
