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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyPromotionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;

import java.util.Objects;

public record RetrievalStrategyPromotionRequest(
        String tenantId,
        String operatorId,
        String templateKey,
        String displayName,
        String description,
        RetrievalOptions options,
        Integer sortOrder,
        Boolean enabled,
        String comment
) {

    public RetrievalStrategyPromotionCommand toCommand(String datasetId, String comparisonId) {
        return new RetrievalStrategyPromotionCommand(
                tenantId,
                datasetId,
                comparisonId,
                operatorId,
                new RetrievalStrategyTemplatePayload(
                        Objects.requireNonNullElse(templateKey, "").trim(),
                        Objects.requireNonNullElse(displayName, "").trim(),
                        Objects.requireNonNullElse(description, "").trim(),
                        options,
                        sortOrder,
                        enabled),
                comment);
    }
}
