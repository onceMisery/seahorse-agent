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

package com.miracle.ai.seahorse.agent.adapters.spring.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;

import java.util.List;

/**
 * 关键词索引异步事件。
 *
 * <p>事件体只描述内核端口语义，不绑定 Elasticsearch、PostgreSQL FTS 等具体后端。
 */
public record KeywordIndexEvent(
        String operation,
        String kbId,
        String docId,
        List<VectorChunk> chunks) {

    public KeywordIndexEvent {
        chunks = List.copyOf(chunks == null ? List.of() : chunks);
    }
}
