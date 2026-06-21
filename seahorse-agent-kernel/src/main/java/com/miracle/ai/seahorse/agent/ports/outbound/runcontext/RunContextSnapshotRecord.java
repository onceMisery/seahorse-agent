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

package com.miracle.ai.seahorse.agent.ports.outbound.runcontext;

import lombok.Data;

import java.time.Instant;

/**
 * Persisted run context snapshot for chat and agent runs.
 */
@Data
public class RunContextSnapshotRecord {

    private Long id;
    private String tenantId;
    private String runId;
    private Long conversationId;
    private Long branchLeafMessageId;
    private Long roleCardId;
    private Long runProfileId;
    private String executorEngine;
    private String executorConfigJson;
    private String traceContextJson;
    private String snapshotJson;
    private Instant createTime;
    private Integer deleted;
}
