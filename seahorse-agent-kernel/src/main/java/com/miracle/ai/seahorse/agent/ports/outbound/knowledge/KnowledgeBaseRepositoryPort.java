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

/**
 * 知识库管理仓储端口。
 */
public interface KnowledgeBaseRepositoryPort {

    String create(CreateKnowledgeBaseValues values);

    boolean nameExists(String normalizedName, String excludedKbId);

    Optional<KnowledgeBaseRecord> findById(String kbId);

    KnowledgeBasePage page(long current, long size, String name);

    boolean hasDocuments(String kbId);

    boolean hasVectorizedDocuments(String kbId);

    boolean update(String kbId, KnowledgeBaseUpdateValues values);

    boolean delete(String kbId, String operator);
}
