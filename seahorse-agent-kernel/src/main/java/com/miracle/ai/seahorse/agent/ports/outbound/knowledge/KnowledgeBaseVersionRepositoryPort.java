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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseVersion;

import java.util.List;
import java.util.Optional;

/**
 * 知识库版本管理仓储端口。
 */
public interface KnowledgeBaseVersionRepositoryPort {

    /**
     * 保存版本记录。
     */
    Long save(KnowledgeBaseVersion version);

    /**
     * 分页查询指定知识库的版本列表。
     */
    List<KnowledgeBaseVersion> findByKbId(Long kbId, int page, int size);

    /**
     * 查询指定知识库的版本总数。
     */
    long countByKbId(Long kbId);

    /**
     * 查询指定知识库的特定版本。
     */
    Optional<KnowledgeBaseVersion> findByKbIdAndVersion(Long kbId, int versionNumber);

    /**
     * 查询指定知识库的最新版本号。
     */
    Optional<Integer> findMaxVersionNumber(Long kbId);
}
