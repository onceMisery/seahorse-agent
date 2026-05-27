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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;

/**
 * 统一来源快照，同时支持 RAG 向量检索和 Web 搜索/爬取来源。
 */
public record AgentRunSnapshotSource(String itemId,
                                     String contextPackId,
                                     ContextItemSourceType sourceType,
                                     String sourceId,
                                     String summary,
                                     double score,
                                     double confidence,
                                     ContextSensitivity sensitivity,
                                     String citationJson,
                                     String title,
                                     String url,
                                     String snippet,
                                     String confidenceLevel,
                                     String supportingConclusion,
                                     String fetchedAt,
                                     int citationIndex) {

    /**
     * 兼容旧构造（不含扩展字段）。
     */
    public AgentRunSnapshotSource(String itemId,
                                  String contextPackId,
                                  ContextItemSourceType sourceType,
                                  String sourceId,
                                  String summary,
                                  double score,
                                  double confidence,
                                  ContextSensitivity sensitivity,
                                  String citationJson) {
        this(itemId, contextPackId, sourceType, sourceId, summary, score, confidence,
                sensitivity, citationJson, null, null, null,
                confidenceLevelFromScore(score), null, null, 0);
    }

    private static String confidenceLevelFromScore(double score) {
        if (score >= 0.85) return "HIGH";
        if (score >= 0.7) return "MEDIUM";
        if (score > 0) return "LOW";
        return "UNKNOWN";
    }
}
