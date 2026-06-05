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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow;

import java.time.Instant;
import java.util.Map;

/**
 * Aggregate representing a single execution step within a workflow run.
 *
 * <p>Each step captures its type, lifecycle status, timing, result data,
 * and optional visual position for DAG rendering.
 *
 * <p>Step type constants:
 * <ul>
 *   <li>{@link #STEP_TYPE_RETRIEVAL} — knowledge-base retrieval</li>
 *   <li>{@link #STEP_TYPE_REASONING} — LLM reasoning / chain-of-thought</li>
 *   <li>{@link #STEP_TYPE_TOOL_CALL} — external tool invocation</li>
 *   <li>{@link #STEP_TYPE_HTTP_REQUEST} — outbound HTTP call</li>
 *   <li>{@link #STEP_TYPE_DB_QUERY} — database query</li>
 * </ul>
 *
 * <p>Status constants:
 * <ul>
 *   <li>{@link #STATUS_PENDING} — not yet started</li>
 *   <li>{@link #STATUS_RUNNING} — currently executing</li>
 *   <li>{@link #STATUS_SUCCESS} — completed successfully</li>
 *   <li>{@link #STATUS_FAILED} — completed with error</li>
 *   <li>{@link #STATUS_SKIPPED} — bypassed by workflow logic</li>
 * </ul>
 */
public record ExecutionStepAggregate(
        String stepId,
        String runId,
        String stepType,
        String status,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        Map<String, Object> resultData,
        Integer positionX,
        Integer positionY) {

    // ── Step type constants ──────────────────────────────────────────
    public static final String STEP_TYPE_RETRIEVAL = "RETRIEVAL";
    public static final String STEP_TYPE_REASONING = "REASONING";
    public static final String STEP_TYPE_TOOL_CALL = "TOOL_CALL";
    public static final String STEP_TYPE_HTTP_REQUEST = "HTTP_REQUEST";
    public static final String STEP_TYPE_DB_QUERY = "DB_QUERY";

    // ── Status constants ─────────────────────────────────────────────
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
}
