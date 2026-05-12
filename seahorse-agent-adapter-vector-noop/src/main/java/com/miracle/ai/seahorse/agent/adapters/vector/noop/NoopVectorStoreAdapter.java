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

package com.miracle.ai.seahorse.agent.adapters.vector.noop;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空向量库 adapter。
 *
 * <p>该实现用于禁用向量检索的场景。写入操作只记录集合存在性，检索永远返回空结果。
 */
public class NoopVectorStoreAdapter implements VectorSearchPort, VectorIndexPort, VectorCollectionAdminPort {

    private final Set<String> collections = ConcurrentHashMap.newKeySet();

    @Override
    public List<RetrievedChunk> search(VectorSearchRequest request) {
        return List.of();
    }

    @Override
    public boolean collectionExists(String collectionName) {
        return collections.contains(collectionName);
    }

    @Override
    public void ensureCollection(String collectionName) {
        if (collectionName != null && !collectionName.isBlank()) {
            collections.add(collectionName);
        }
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        ensureCollection(collectionName);
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        ensureCollection(collectionName);
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        ensureCollection(collectionName);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        ensureCollection(collectionName);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        ensureCollection(collectionName);
    }
}
