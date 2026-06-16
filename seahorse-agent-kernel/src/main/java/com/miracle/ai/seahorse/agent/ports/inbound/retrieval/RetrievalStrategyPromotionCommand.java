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

import java.util.Objects;

/**
 * Explicit request to promote a retrieval strategy template from a saved comparison.
 */
public record RetrievalStrategyPromotionCommand(
        String tenantId,
        String datasetId,
        String comparisonId,
        String operatorId,
        RetrievalStrategyTemplatePayload template,
        String comment
) {

    public RetrievalStrategyPromotionCommand {
        tenantId = defaultText(tenantId, "default");
        datasetId = Objects.requireNonNullElse(datasetId, "").trim();
        comparisonId = Objects.requireNonNullElse(comparisonId, "").trim();
        operatorId = defaultText(operatorId, "system");
        template = Objects.requireNonNull(template, "template must not be null");
        comment = Objects.requireNonNullElse(comment, "").trim();
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = Objects.requireNonNullElse(value, "").trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
