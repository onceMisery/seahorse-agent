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

import java.util.Optional;
import java.util.List;

/**
 * 知识库文档仓储端口。
 */
public interface KnowledgeDocumentRepositoryPort {

    /**
     * 创建待处理文档。
     *
     * @param command 创建命令
     * @return 文档记录
     */
    KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command);

    /**
     * 查询文档。
     *
     * @param docId 文档 ID
     * @return 文档记录
     */
    Optional<KnowledgeDocumentRecord> findById(Long docId);

    /**
     * 查询文档管理详情。
     *
     * @param docId 文档 ID
     * @return 文档详情
     */
    default Optional<KnowledgeDocumentDetail> findDetailById(Long docId) {
        return Optional.empty();
    }

    /**
     * 分页查询文档。
     *
     * @param kbId    知识库 ID
     * @param current 当前页
     * @param size    每页数量
     * @param status  状态过滤
     * @param keyword 关键字过滤
     * @return 分页结果
     */
    default KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
        return new KnowledgeDocumentPage(List.of(), 0, size, current, 0);
    }

    /**
     * 查询文档分块日志。
     *
     * @param docId   文档 ID
     * @param current 当前页
     * @param size    每页数量
     * @return 日志分页
     */
    default KnowledgeDocumentChunkLogPage chunkLogs(Long docId, long current, long size) {
        return new KnowledgeDocumentChunkLogPage(List.of(), 0, size, current, 0);
    }

    /**
     * 将文档置为运行中。
     *
     * @param docId    文档 ID
     * @param operator 操作人
     * @return 是否更新成功
     */
    boolean markRunning(Long docId, String operator);

    /**
     * 将文档置为成功。
     *
     * @param docId      文档 ID
     * @param chunkCount 分片数量
     * @param operator   操作人
     */
    void markSuccess(Long docId, int chunkCount, String operator);

    /**
     * 将文档置为失败。
     *
     * @param docId        文档 ID
     * @param operator     操作人
     * @param errorMessage 错误信息
     */
    void markFailed(Long docId, String operator, String errorMessage);

    /**
     * 更新文档管理字段。
     *
     * @param docId  文档 ID
     * @param values 更新值
     * @return 是否更新成功
     */
    default boolean update(Long docId, KnowledgeDocumentUpdateValues values) {
        return false;
    }

    /**
     * 启用或禁用文档及其分块。
     *
     * @param docId    文档 ID
     * @param enabled  是否启用
     * @param operator 操作人
     * @return 是否更新成功
     */
    default boolean updateEnabled(Long docId, boolean enabled, String operator) {
        return false;
    }

    default boolean replaceFileForRefresh(Long docId, KnowledgeDocumentFileRef file, String operator) {
        return false;
    }

    /**
     * 删除文档及关系型存储中的关联记录。
     *
     * @param docId    文档 ID
     * @param operator 操作人
     * @return 是否删除成功
     */
    default boolean delete(Long docId, String operator) {
        return false;
    }

    /**
     * 查询文档下仍启用的分块。
     *
     * @param docId 文档 ID
     * @return 分块列表
     */
    default List<KnowledgeChunkRecord> listEnabledChunks(Long docId) {
        return List.of();
    }
}
