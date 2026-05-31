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

package com.miracle.ai.seahorse.agent.kernel.domain.stream;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import java.time.Instant;
import java.util.Objects;

public record StreamEventEnvelope(
    String eventId,
    long eventSeq,
    StreamEventType eventType,
    String runId,
    String stepId,
    Instant timestamp,
    Object typedPayload
) {

    public StreamEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static StreamEventEnvelope of(long seq, StreamEventType type, String runId, Object payload) {
        return new StreamEventEnvelope(
            SnowflakeIds.nextIdString(), seq, type, runId, null, Instant.now(), payload);
    }

    public static StreamEventEnvelope of(long seq, StreamEventType type, String runId, String stepId, Object payload) {
        return new StreamEventEnvelope(
            SnowflakeIds.nextIdString(), seq, type, runId, stepId, Instant.now(), payload);
    }
}
