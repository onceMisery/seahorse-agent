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

package com.miracle.ai.seahorse.agent.kernel.domain.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * seahorse-agent 自有向量分片写入契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorChunk {

    /**
     * 分片唯一标识。
     */
    private String chunkId;

    /**
     * 分片在文档中的顺序。
     */
    private Integer index;

    /**
     * 分片文本内容。
     */
    private String content;

    /**
     * 业务元数据。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 分片向量。
     */
    private float[] embedding;
}
