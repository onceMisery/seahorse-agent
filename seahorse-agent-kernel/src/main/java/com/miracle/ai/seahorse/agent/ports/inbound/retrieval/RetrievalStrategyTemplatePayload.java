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
 * 检索策略模板管理请求。
 *
 * <p>这里只承载策略参数模板，不承载动态 metadata 过滤条件；过滤仍必须经过 Schema 和 Filter Compiler。
 */
public record RetrievalStrategyTemplatePayload(
        String templateKey,
        String displayName,
        String description,
        RetrievalOptions options,
        Integer sortOrder,
        Boolean enabled
) {

    public RetrievalStrategyTemplatePayload {
        templateKey = Objects.requireNonNullElse(templateKey, "").trim();
        displayName = Objects.requireNonNullElse(displayName, "").trim();
        description = Objects.requireNonNullElse(description, "").trim();
        options = options == null ? RetrievalOptions.defaults(5) : options;
        sortOrder = sortOrder == null ? 0 : sortOrder;
        enabled = enabled == null ? Boolean.TRUE : enabled;
    }

    public RetrievalStrategyTemplatePayload withTemplateKey(String nextTemplateKey) {
        return new RetrievalStrategyTemplatePayload(
                nextTemplateKey, displayName, description, options, sortOrder, enabled);
    }

    public RetrievalStrategyTemplate toTemplate() {
        return new RetrievalStrategyTemplate(templateKey, displayName, description, options);
    }
}
