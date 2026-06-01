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

package com.miracle.ai.seahorse.agent.ports.outbound.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;

import java.util.List;
import java.util.Optional;

/**
 * 知识库 Chunk 写入仓储端口。
 *
 * <p>入库索引节点通过该端口写入 chunk 元数据，避免 L1/L2 直接依赖旧 KnowledgeChunkService
 * 或具体 ORM 技术。
 */
public interface KnowledgeChunkRepositoryPort {

    /**
     * 替换文档下的全部 chunk。
     *
     * @param kbId   知识库 ID
     * @param docId  文档 ID
     * @param chunks 待持久化 chunk
     */
    void replaceDocumentChunks(Long kbId, Long docId, List<VectorChunk> chunks);

    default Optional<KnowledgeDocumentChunkContext> findDocumentContext(Long docId) {
        return Optional.empty();
    }

    default KnowledgeChunkPage page(Long docId, long current, long size, Boolean enabled) {
        return new KnowledgeChunkPage(List.of(), 0, size, current, 0);
    }

    default KnowledgeChunkRecord create(Long docId, CreateKnowledgeChunkValues values) {
        throw new UnsupportedOperationException("create chunk is not supported");
    }

    default Optional<KnowledgeChunkRecord> findChunk(Long docId, Long chunkId) {
        return Optional.empty();
    }

    default boolean update(Long docId, Long chunkId, UpdateKnowledgeChunkValues values) {
        return false;
    }

    default boolean delete(Long docId, Long chunkId) {
        return false;
    }

    default List<KnowledgeChunkRecord> findChunksByIds(Long docId, List<Long> chunkIds) {
        return List.of();
    }

    default boolean updateEnabled(Long docId, List<Long> chunkIds, boolean enabled, String operator) {
        return false;
    }
}
