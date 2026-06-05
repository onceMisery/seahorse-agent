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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBasePermission;

import java.util.List;
import java.util.Optional;

/**
 * 知识库权限管理仓储端口。
 */
public interface KnowledgeBasePermissionRepositoryPort {

    /**
     * 保存权限记录。
     */
    Long save(KnowledgeBasePermission permission);

    /**
     * 查询指定知识库和用户的权限。
     */
    Optional<KnowledgeBasePermission> findByKbIdAndUserId(Long kbId, Long userId);

    /**
     * 查询指定知识库的所有权限记录。
     */
    List<KnowledgeBasePermission> findByKbId(Long kbId);

    /**
     * 删除权限记录。
     */
    boolean deleteById(Long id);

    /**
     * 删除指定知识库的所有权限。
     */
    int deleteByKbId(Long kbId);
}
