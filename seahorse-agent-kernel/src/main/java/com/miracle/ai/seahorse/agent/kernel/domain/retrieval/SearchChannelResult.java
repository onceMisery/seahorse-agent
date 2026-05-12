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

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个检索通道的执行结果。
 */
@Data
@Builder
public class SearchChannelResult {

    /**
     * 通道类型。
     */
    private SearchChannelType channelType;

    /**
     * 通道名称。
     */
    private String channelName;

    /**
     * 通道返回的分片。
     */
    private List<RetrievedChunk> chunks;

    /**
     * 通道耗时，单位毫秒。
     */
    private long latencyMs;

    /**
     * 扩展元数据。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
