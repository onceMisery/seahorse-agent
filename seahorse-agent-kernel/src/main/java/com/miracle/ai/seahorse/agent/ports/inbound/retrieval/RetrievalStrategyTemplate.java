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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;

import java.util.Objects;

/**
 * 知识库检索策略模板。
 *
 * @param templateKey 唯一模板键，供管理端保存或发起评测时引用
 * @param displayName 展示名称
 * @param description 模板适用场景说明
 * @param options     强类型检索参数，不承载动态 metadata 过滤条件
 */
public record RetrievalStrategyTemplate(
        String templateKey,
        String displayName,
        String description,
        RetrievalOptions options,
        boolean recommended
) {

    public RetrievalStrategyTemplate(String templateKey,
                                     String displayName,
                                     String description,
                                     RetrievalOptions options) {
        this(templateKey, displayName, description, options, false);
    }

    public RetrievalStrategyTemplate {
        templateKey = Objects.requireNonNullElse(templateKey, "").trim();
        displayName = Objects.requireNonNullElse(displayName, "").trim();
        description = Objects.requireNonNullElse(description, "").trim();
        options = options == null ? RetrievalOptions.defaults(5) : options;
    }
}
