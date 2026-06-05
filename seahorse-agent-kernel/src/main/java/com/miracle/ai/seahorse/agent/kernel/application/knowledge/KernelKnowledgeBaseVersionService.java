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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseVersion;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseVersionRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 知识库版本管理服务，支持快照创建、分页查询和版本回滚。
 */
public class KernelKnowledgeBaseVersionService {

    private final KnowledgeBaseVersionRepositoryPort repositoryPort;

    public KernelKnowledgeBaseVersionService(KnowledgeBaseVersionRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    /**
     * 创建新版本。
     *
     * @param kbId              知识库 ID
     * @param snapshotJson      快照 JSON
     * @param changeDescription 变更描述
     * @param operator          操作人
     * @return 新版本 ID
     */
    public Long createVersion(Long kbId, String snapshotJson, String changeDescription, String operator) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshotJson 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        String tenantId = TenantContext.get();
        int nextVersionNumber = repositoryPort.findMaxVersionNumber(kbId).orElse(0) + 1;

        KnowledgeBaseVersion version = new KnowledgeBaseVersion(
                null,
                kbId,
                tenantId,
                nextVersionNumber,
                snapshotJson,
                operator,
                Instant.now(),
                changeDescription == null ? "" : changeDescription
        );

        return repositoryPort.save(version);
    }

    /**
     * 分页查询版本列表。
     *
     * @param kbId 知识库 ID
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 版本列表
     */
    public List<KnowledgeBaseVersion> listVersions(Long kbId, int page, int size) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        return repositoryPort.findByKbId(kbId, page, size);
    }

    /**
     * 查询版本总数。
     */
    public long countVersions(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        return repositoryPort.countByKbId(kbId);
    }

    /**
     * 查询指定版本。
     */
    public KnowledgeBaseVersion getVersion(Long kbId, int versionNumber) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber 必须大于 0");
        }
        return repositoryPort.findByKbIdAndVersion(kbId, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在：" + versionNumber));
    }

    /**
     * 回滚到指定版本：先创建保护性快照，然后恢复目标版本。
     *
     * @param kbId         知识库 ID
     * @param targetVersion 目标版本号
     * @param operator     操作人
     * @return 新版本 ID（保护性快照）
     */
    public Long rollbackToVersion(Long kbId, int targetVersion, String operator) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (targetVersion < 1) {
            throw new IllegalArgumentException("targetVersion 必须大于 0");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }

        // 查询目标版本
        KnowledgeBaseVersion target = repositoryPort.findByKbIdAndVersion(kbId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在：" + targetVersion));

        // 查询当前最新版本
        int currentVersionNumber = repositoryPort.findMaxVersionNumber(kbId).orElse(0);

        // 创建保护性快照（回滚前的当前状态）
        String tenantId = TenantContext.get();
        KnowledgeBaseVersion protectiveSnapshot = new KnowledgeBaseVersion(
                null,
                kbId,
                tenantId,
                currentVersionNumber + 1,
                target.snapshotJson(), // 使用目标版本的快照作为保护点
                operator,
                Instant.now(),
                "保护性快照：回滚到版本 " + targetVersion + " 之前"
        );
        Long snapshotId = repositoryPort.save(protectiveSnapshot);

        return snapshotId;
    }
}
