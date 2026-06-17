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

package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Skill 向量索引管理服务。
 *
 * <p>负责管理 Skill 的向量索引生命周期：
 * <ul>
 *   <li>创建索引：Skill 创建或更新时自动生成向量索引</li>
 *   <li>更新索引：Skill 内容变更时重新生成向量</li>
 *   <li>删除索引：Skill 删除时清理向量索引</li>
 *   <li>全量重建：支持批量重建所有 Skill 的向量索引</li>
 * </ul>
 *
 * <p>异步处理：向量化操作异步执行，不阻塞主流程。
 */
public class SkillVectorIndexService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SkillVectorIndexService.class);

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int BATCH_SIZE = 50;

    private final EmbeddingPort embeddingPort;
    private final SkillVectorIndexRepositoryPort vectorRepository;
    private final AgentSkillRepositoryPort skillRepository;
    private final ExecutorService executorService;

    public SkillVectorIndexService(EmbeddingPort embeddingPort,
                                     SkillVectorIndexRepositoryPort vectorRepository,
                                     AgentSkillRepositoryPort skillRepository) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.vectorRepository = Objects.requireNonNull(vectorRepository, "vectorRepository must not be null");
        this.skillRepository = Objects.requireNonNull(skillRepository, "skillRepository must not be null");
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread thread = new Thread(r, "skill-vector-indexer");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 为单个 Skill 创建或更新向量索引（异步）。
     *
     * @param skill Skill 实体
     * @param revision Skill 版本内容
     */
    public void indexSkillAsync(AgentSkill skill, AgentSkillRevision revision) {
        if (!shouldIndex(skill)) {
            LOG.debug("Skip indexing disabled or deleted skill: {}", skill.name());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                indexSkill(skill, revision);
            } catch (Exception ex) {
                LOG.error("Failed to index skill: {} (tenant: {})", skill.name(), skill.tenantId(), ex);
            }
        }, executorService);
    }

    /**
     * 为单个 Skill 创建或更新向量索引（同步）。
     */
    public void indexSkill(AgentSkill skill, AgentSkillRevision revision) {
        if (!shouldIndex(skill)) {
            return;
        }

        long startTime = System.currentTimeMillis();

        // 1. 构建用于向量化的文本
        String content = SkillVectorIndex.buildContentForEmbedding(skill, revision.content());

        // 2. 生成向量
        float[] embedding = embeddingPort.embed(content);
        if (embedding == null || embedding.length == 0) {
            LOG.warn("Embedding service returned empty vector for skill: {}", skill.name());
            return;
        }

        // 3. 保存向量索引
        SkillVectorIndex index = new SkillVectorIndex(
                skill.name(),
                skill.tenantId(),
                revision.revisionId(),
                embedding,
                content,
                System.currentTimeMillis()
        );

        vectorRepository.save(index);

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("Indexed skill: {} (tenant: {}, dimension: {}, elapsed: {}ms)",
                skill.name(), skill.tenantId(), embedding.length, elapsed);
    }

    /**
     * 删除 Skill 的向量索引（异步）。
     *
     * @param tenantId 租户 ID
     * @param skillName Skill 名称
     */
    public void deleteIndexAsync(String tenantId, String skillName) {
        CompletableFuture.runAsync(() -> {
            try {
                vectorRepository.delete(tenantId, skillName);
                LOG.info("Deleted skill vector index: {} (tenant: {})", skillName, tenantId);
            } catch (Exception ex) {
                LOG.error("Failed to delete skill vector index: {} (tenant: {})", skillName, tenantId, ex);
            }
        }, executorService);
    }

    /**
     * 全量重建租户的所有 Skill 向量索引。
     *
     * @param tenantId 租户 ID
     * @return 重建任务的 Future
     */
    public CompletableFuture<RebuildResult> rebuildAllAsync(String tenantId) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            int successCount = 0;
            int failureCount = 0;

            try {
                LOG.info("Starting skill vector index rebuild for tenant: {}", tenantId);

                // 1. 获取所有可用 Skill
                AgentSkillPage page = skillRepository.page(tenantId, 1, 10000, null);
                List<AgentSkill> skills = page.records().stream()
                        .filter(this::shouldIndex)
                        .toList();

                LOG.info("Found {} skills to index for tenant: {}", skills.size(), tenantId);

                // 2. 批量处理
                for (int i = 0; i < skills.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, skills.size());
                    List<AgentSkill> batch = skills.subList(i, end);

                    List<SkillVectorIndex> indices = new ArrayList<>();

                    for (AgentSkill skill : batch) {
                        try {
                            // 加载 revision
                            AgentSkillRevision revision = skillRepository
                                    .findRevision(skill.tenantId(), skill.latestRevisionId())
                                    .orElse(null);

                            if (revision == null) {
                                LOG.warn("Skill revision not found: {} (revisionId: {})",
                                        skill.name(), skill.latestRevisionId());
                                failureCount++;
                                continue;
                            }

                            // 构建内容并生成向量
                            String content = SkillVectorIndex.buildContentForEmbedding(skill, revision.content());
                            float[] embedding = embeddingPort.embed(content);

                            if (embedding != null && embedding.length > 0) {
                                indices.add(new SkillVectorIndex(
                                        skill.name(),
                                        skill.tenantId(),
                                        revision.revisionId(),
                                        embedding,
                                        content,
                                        System.currentTimeMillis()
                                ));
                                successCount++;
                            } else {
                                failureCount++;
                            }

                        } catch (Exception ex) {
                            LOG.error("Failed to index skill in batch: {}", skill.name(), ex);
                            failureCount++;
                        }
                    }

                    // 批量保存
                    if (!indices.isEmpty()) {
                        vectorRepository.saveBatch(indices);
                        LOG.info("Saved batch of {} skill vectors (progress: {}/{})",
                                indices.size(), end, skills.size());
                    }
                }

                long elapsed = System.currentTimeMillis() - startTime;
                LOG.info("Skill vector index rebuild completed for tenant: {} (success: {}, failure: {}, elapsed: {}ms)",
                        tenantId, successCount, failureCount, elapsed);

                return new RebuildResult(successCount, failureCount, elapsed);

            } catch (Exception ex) {
                LOG.error("Skill vector index rebuild failed for tenant: {}", tenantId, ex);
                long elapsed = System.currentTimeMillis() - startTime;
                return new RebuildResult(successCount, failureCount, elapsed);
            }
        }, executorService);
    }

    /**
     * 初始化向量集合（如果不存在）。
     */
    public void initializeCollection() {
        try {
            int dimension = embeddingPort.dimension();
            if (dimension > 0) {
                boolean shouldRebuild = vectorRepository.ensureCollection(dimension);
                LOG.info("Ensured skill vector collection with dimension: {}", dimension);
                if (shouldRebuild) {
                    rebuildAllTenantsAsync();
                }
            } else {
                LOG.warn("Cannot create collection: embedding dimension is 0");
            }
        } catch (Exception ex) {
            LOG.error("Failed to initialize skill vector collection", ex);
        }
    }

    /**
     * Rebuild vector indexes for every tenant known to the skill repository.
     */
    public List<CompletableFuture<RebuildResult>> rebuildAllTenantsAsync() {
        return tenantIdsForRebuild().stream()
                .map(this::rebuildAllAsync)
                .toList();
    }

    /**
     * 关闭服务。
     */
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    private boolean shouldIndex(AgentSkill skill) {
        return skill.enabled()
                && skill.status() == AgentSkillStatus.ACTIVE
                && skill.latestRevisionId() != null
                && !skill.latestRevisionId().isBlank();
    }

    private List<String> tenantIdsForRebuild() {
        try {
            List<String> tenants = skillRepository.listTenants().stream()
                    .filter(tenantId -> tenantId != null && !tenantId.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            return tenants.isEmpty() ? List.of(DEFAULT_TENANT_ID) : tenants;
        } catch (Exception ex) {
            LOG.warn("Failed to list skill tenants for vector rebuild, falling back to default tenant", ex);
            return List.of(DEFAULT_TENANT_ID);
        }
    }

    /**
     * 重建结果。
     */
    public record RebuildResult(int successCount, int failureCount, long elapsedMs) {
    }
}
