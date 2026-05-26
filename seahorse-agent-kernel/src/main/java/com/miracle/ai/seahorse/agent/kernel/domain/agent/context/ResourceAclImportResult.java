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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ResourceAclImportResult(ResourceAclImportMode mode,
                                      ResourceAclImportDryRunReport dryRunReport,
                                      List<String> createdRuleIds,
                                      Map<ResourceAclImportReasonCode, Integer> reasonCounts,
                                      boolean failed) {

    public ResourceAclImportResult {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        dryRunReport = Objects.requireNonNull(dryRunReport, "dryRunReport must not be null");
        createdRuleIds = List.copyOf(Objects.requireNonNull(createdRuleIds, "createdRuleIds must not be null"));
        EnumMap<ResourceAclImportReasonCode, Integer> safeReasonCounts =
                new EnumMap<>(ResourceAclImportReasonCode.class);
        Objects.requireNonNull(reasonCounts, "reasonCounts must not be null")
                .forEach((reasonCode, count) -> safeReasonCounts.put(
                        Objects.requireNonNull(reasonCode, "reasonCode must not be null"),
                        Objects.requireNonNull(count, "count must not be null")));
        reasonCounts = Map.copyOf(safeReasonCounts);
    }

    public int createdCount() {
        return createdRuleIds.size();
    }

    public int skippedCount() {
        return dryRunReport.items().size() - createdCount();
    }
}
