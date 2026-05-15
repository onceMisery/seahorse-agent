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

package com.miracle.ai.seahorse.agent.ports.outbound.retrieval;

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;

import java.util.List;

/**
 * 知识库级检索策略模板仓储端口。
 *
 * <p>内核只依赖该端口读取覆盖配置，具体持久化可以由 JDBC、配置中心或租户配置适配器提供。
 */
public interface RetrievalStrategyTemplateRepositoryPort {

    /**
     * 查询指定知识库的策略模板覆盖。
     *
     * @param kbId 知识库 ID
     * @return 覆盖模板；同名 templateKey 会覆盖内置模板，新 templateKey 会追加
     */
    List<RetrievalStrategyTemplate> listTemplates(String kbId);

    /**
     * 新增或更新指定知识库的模板覆盖。
     *
     * <p>默认实现显式声明只读，避免无持久化仓储时误以为写入成功。
     */
    default RetrievalStrategyTemplate upsertTemplate(String kbId, RetrievalStrategyTemplatePayload payload) {
        throw new UnsupportedOperationException("retrieval strategy template repository is read-only");
    }

    /**
     * 软删除指定知识库的模板覆盖。
     */
    default boolean deleteTemplate(String kbId, String templateKey) {
        return false;
    }

    static RetrievalStrategyTemplateRepositoryPort empty() {
        return kbId -> List.of();
    }
}
