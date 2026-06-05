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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseShare;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库分享管理仓储端口。
 */
public interface KnowledgeBaseShareRepositoryPort {

    /**
     * 保存分享记录。
     */
    Long save(KnowledgeBaseShare share);

    /**
     * 根据分享令牌查询。
     */
    Optional<KnowledgeBaseShare> findByToken(String token);

    /**
     * 查询指定知识库的所有分享记录。
     */
    List<KnowledgeBaseShare> findByKbId(Long kbId);

    /**
     * 增加访问次数。
     */
    boolean incrementAccessCount(Long id);

    /**
     * 删除过期分享。
     */
    int deleteExpired(Instant now);

    /**
     * 删除分享记录。
     */
    boolean deleteById(Long id);

    /**
     * 删除指定知识库的所有分享。
     */
    int deleteByKbId(Long kbId);
}
