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

package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

/**
 * 元数据回填任务仓储端口。
 */
public interface MetadataBackfillJobRepositoryPort {

    String create(MetadataBackfillJobRecord job);

    Optional<MetadataBackfillJobRecord> findById(String jobId);

    default MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
        MetadataBackfillJobQuery safeQuery = query == null
                ? new MetadataBackfillJobQuery("", "", null, 1, 20)
                : query;
        return MetadataBackfillJobPage.empty(safeQuery.current(), safeQuery.size());
    }

    default MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
        // 兼容外部旧仓储实现：未覆盖运维总览时返回空视图，不阻断 backfill 主流程。
        return MetadataBackfillOperationsOverview.empty(tenantId, knowledgeBaseId);
    }

    void save(MetadataBackfillJobRecord job);
}
