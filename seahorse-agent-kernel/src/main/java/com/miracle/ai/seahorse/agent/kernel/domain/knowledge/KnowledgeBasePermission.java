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
 * 知识库权限控制，支持 OWNER/EDITOR/VIEWER 三种角色。
 *
 * @param id         权限记录主键
 * @param kbId       知识库 ID
 * @param tenantId   租户 ID
 * @param userId     用户 ID
 * @param permission 权限级别：OWNER/EDITOR/VIEWER
 * @param grantedAt  授权时间
 */
public record KnowledgeBasePermission(Long id,
                                      Long kbId,
                                      String tenantId,
                                      Long userId,
                                      String permission,
                                      Instant grantedAt) {

    public static final String OWNER = "OWNER";
    public static final String EDITOR = "EDITOR";
    public static final String VIEWER = "VIEWER";

    public KnowledgeBasePermission {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission 不能为空");
        }
        if (!OWNER.equals(permission) && !EDITOR.equals(permission) && !VIEWER.equals(permission)) {
            throw new IllegalArgumentException("无效的权限级别：" + permission);
        }
        grantedAt = Objects.requireNonNull(grantedAt, "grantedAt 不能为空");
    }

    /**
     * 是否可编辑（OWNER 或 EDITOR）。
     */
    public boolean canEdit() {
        return OWNER.equals(permission) || EDITOR.equals(permission);
    }

    /**
     * 是否可删除（仅 OWNER）。
     */
    public boolean canDelete() {
        return OWNER.equals(permission);
    }

    /**
     * 更新权限级别。
     */
    public KnowledgeBasePermission withPermission(String newPermission, Instant now) {
        return new KnowledgeBasePermission(id, kbId, tenantId, userId, newPermission, now);
    }
}
