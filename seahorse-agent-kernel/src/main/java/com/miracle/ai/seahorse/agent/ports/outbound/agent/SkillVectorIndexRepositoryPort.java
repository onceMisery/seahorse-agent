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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;

import java.util.List;
import java.util.Optional;

/**
 * Skill 向量索引仓储接口。
 *
 * <p>负责管理 Skill 的向量索引，支持向量相似度搜索。
 */
public interface SkillVectorIndexRepositoryPort {

    /**
     * 保存或更新 Skill 向量索引。
     *
     * @param index 向量索引
     */
    void save(SkillVectorIndex index);

    /**
     * 批量保存向量索引。
     *
     * @param indices 向量索引列表
     */
    void saveBatch(List<SkillVectorIndex> indices);

    /**
     * 根据 Skill 名称查找向量索引。
     *
     * @param tenantId 租户 ID
     * @param skillName Skill 名称
     * @return 向量索引（如果存在）
     */
    Optional<SkillVectorIndex> findBySkillName(String tenantId, String skillName);

    /**
     * 向量相似度搜索。
     *
     * @param tenantId 租户 ID
     * @param queryVector 查询向量
     * @param topK 返回前 K 个结果
     * @return 相似 Skill 列表，按相似度降序排列
     */
    List<SkillSearchResult> searchSimilar(String tenantId, float[] queryVector, int topK);

    /**
     * 删除 Skill 向量索引。
     *
     * @param tenantId 租户 ID
     * @param skillName Skill 名称
     */
    void delete(String tenantId, String skillName);

    /**
     * 删除租户的所有向量索引。
     *
     * @param tenantId 租户 ID
     */
    void deleteByTenant(String tenantId);

    /**
     * 检查集合是否存在。
     *
     * @return 是否存在
     */
    boolean collectionExists();

    /**
     * Ensure the vector collection exists and is compatible with the expected dimension.
     *
     * @param dimension expected vector dimension
     * @return true when the collection was created or recreated and existing indexes should be rebuilt
     */
    default boolean ensureCollection(int dimension) {
        boolean existed = collectionExists();
        createCollection(dimension);
        return !existed;
    }

    /**
     * 创建向量集合。
     *
     * @param dimension 向量维度
     */
    void createCollection(int dimension);

    /**
     * Skill 搜索结果。
     */
    record SkillSearchResult(
            String skillName,
            String revisionId,
            float score,
            String content) {
    }

    /**
     * No-op 实现。
     */
    static SkillVectorIndexRepositoryPort noop() {
        return new SkillVectorIndexRepositoryPort() {
            @Override
            public void save(SkillVectorIndex index) {
            }

            @Override
            public void saveBatch(List<SkillVectorIndex> indices) {
            }

            @Override
            public Optional<SkillVectorIndex> findBySkillName(String tenantId, String skillName) {
                return Optional.empty();
            }

            @Override
            public List<SkillSearchResult> searchSimilar(String tenantId, float[] queryVector, int topK) {
                return List.of();
            }

            @Override
            public void delete(String tenantId, String skillName) {
            }

            @Override
            public void deleteByTenant(String tenantId) {
            }

            @Override
            public boolean collectionExists() {
                return false;
            }

            @Override
            public void createCollection(int dimension) {
            }
        };
    }
}
