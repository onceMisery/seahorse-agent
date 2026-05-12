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

package com.miracle.ai.seahorse.agent.ports.outbound.vector;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;

import java.util.List;

/**
 * 向量索引写入端口。
 * <p>
 * 入库链路通过该端口管理 Chunk 向量的新增、更新和删除，避免 L1/L2 直接依赖 Milvus、pgvector
 * 或旧 {@code VectorStoreService} 的具体实现。
 */
public interface VectorIndexPort {

    /**
     * 批量写入文档 Chunk 向量索引。
     *
     * @param collectionName 目标集合名称
     * @param docId          文档 ID
     * @param chunks         已完成 embedding 的 Chunk 列表
     */
    void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks);

    /**
     * 更新单个 Chunk 的向量索引。
     *
     * @param collectionName 目标集合名称
     * @param docId          文档 ID
     * @param chunk          待更新 Chunk
     */
    void updateChunk(String collectionName, String docId, VectorChunk chunk);

    /**
     * 删除文档下所有 Chunk 的向量索引。
     *
     * @param collectionName 目标集合名称
     * @param docId          文档 ID
     */
    void deleteDocumentVectors(String collectionName, String docId);

    /**
     * 删除单个 Chunk 的向量索引。
     *
     * @param collectionName 目标集合名称
     * @param chunkId        Chunk ID
     */
    void deleteChunkById(String collectionName, String chunkId);

    /**
     * 批量删除 Chunk 向量索引。
     *
     * @param collectionName 目标集合名称
     * @param chunkIds       Chunk ID 列表
     */
    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
