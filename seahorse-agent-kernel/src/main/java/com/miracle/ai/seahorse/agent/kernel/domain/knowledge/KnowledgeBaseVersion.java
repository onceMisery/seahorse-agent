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

package com.miracle.ai.seahorse.agent.kernel.domain.knowledge;

import java.time.Instant;
import java.util.Objects;

/**
 * 知识库版本快照，用于版本控制和回滚。
 *
 * @param id                版本记录主键
 * @param kbId              知识库 ID
 * @param tenantId          租户 ID
 * @param versionNumber     版本号，递增
 * @param snapshotJson      知识库快照（JSONB 格式）
 * @param createdBy         创建人
 * @param createdAt         创建时间
 * @param changeDescription 变更描述
 */
public record KnowledgeBaseVersion(Long id,
                                   Long kbId,
                                   String tenantId,
                                   int versionNumber,
                                   String snapshotJson,
                                   String createdBy,
                                   Instant createdAt,
                                   String changeDescription) {

    public KnowledgeBaseVersion {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber 必须大于 0");
        }
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshotJson 不能为空");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        changeDescription = changeDescription == null ? "" : changeDescription;
    }

    /**
     * 创建下一个版本。
     */
    public KnowledgeBaseVersion next(String newSnapshotJson, String operator, String description, Instant now) {
        return new KnowledgeBaseVersion(
                null,
                kbId,
                tenantId,
                versionNumber + 1,
                newSnapshotJson,
                operator,
                now,
                description
        );
    }
}
