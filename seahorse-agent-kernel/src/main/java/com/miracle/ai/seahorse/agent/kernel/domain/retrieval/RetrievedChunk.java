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

package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * seahorse-agent 自有检索命中分片契约。
 * <p>
 * L1/L2 只依赖该模型，外部检索命中结果在 L3 adapter 内映射为该契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedChunk {

    /**
     * 分片唯一标识。
     */
    private String id;

    /**
     * 分片文本内容。
     */
    private String text;

    /**
     * 检索或重排得分。
     */
    private Float score;

    private String tenantId;

    private String kbId;

    private String docId;

    private String collectionName;

    private Integer chunkIndex;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Float> channelScores = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Integer> channelRanks = new LinkedHashMap<>();

    /**
     * 检索融合解释，记录 RRF 参数、通道排名和通道贡献，便于管理端排查排序来源。
     */
    @Builder.Default
    private Map<String, Object> fusionExplanation = new LinkedHashMap<>();

    private Float fusionScore;

    private Float rerankScore;
}
