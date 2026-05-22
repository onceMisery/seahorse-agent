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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerFeedbackExportRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;

import java.util.List;

public interface MemoryReviewInboundPort {

    MemoryReviewPage page(String tenantId,
                          String userId,
                          MemoryReviewStatus status,
                          String targetKind,
                          String targetKey,
                          long current,
                          long size);

    MemoryReviewRecord queryById(String candidateId);

    MemoryReviewRecord approve(String candidateId, MemoryReviewDecisionCommand command);

    MemoryReviewRecord modify(String candidateId, MemoryReviewDecisionCommand command);

    MemoryReviewRecord reject(String candidateId, MemoryReviewDecisionCommand command);

    List<MemoryReviewFeedbackSample> listFeedbackSamples(String candidateId, int limit);

    List<MemoryReviewFeedbackSample> listFeedbackSamples(String tenantId,
                                                         String userId,
                                                         MemoryReviewStatus status,
                                                         String targetKind,
                                                         String targetKey,
                                                         int limit);

    List<MemoryRefinerFeedbackExportRecord> exportRefinerFeedbackSamples(String tenantId,
                                                                         String userId,
                                                                         MemoryReviewStatus status,
                                                                         String targetKind,
                                                                         String targetKey,
                                                                         int limit);
}
