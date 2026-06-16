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

import java.util.List;
import java.util.Optional;

/**
 * 知识库只读查询端口。
 * <p>
 * 检索 Feature 和入库治理逻辑通过该端口查询文档与 Chunk 元数据，避免直接耦合旧 Controller VO、
 * MyBatis Plus 分页对象或具体 Mapper。
 */
public interface KnowledgeBaseQueryPort {

    default Optional<KnowledgeBaseRef> findById(Long kbId) {
        return Optional.empty();
    }

    /**
     * 查询可参与全局检索的知识库。
     *
     * @return 知识库引用列表
     */
    default List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
        return List.of();
    }

    /**
     * 查询与当前向量模型兼容的可检索知识库。
     *
     * @param embeddingModel 当前检索使用的向量模型
     * @return 知识库引用列表
     */
    default List<KnowledgeBaseRef> listSearchableKnowledgeBases(String embeddingModel) {
        return listSearchableKnowledgeBases();
    }

    /**
     * 根据关键词搜索文档。
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回数量
     * @return 文档摘要列表
     */
    List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit);

    /**
     * 查询文档下的 Chunk 列表。
     *
     * @param docId 文档 ID
     * @return Chunk 摘要列表
     */
    List<KnowledgeChunkSummary> listChunksByDocId(Long docId);
}
