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

import java.util.List;
import java.util.Objects;

public record ResourceAclImportDryRunReport(List<ResourceAclImportDryRunItem> items) {

    public ResourceAclImportDryRunReport {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    public int validCount() {
        return count(ResourceAclImportItemStatus.VALID);
    }

    public int invalidCount() {
        return count(ResourceAclImportItemStatus.INVALID);
    }

    public int duplicateInBatchCount() {
        return count(ResourceAclImportItemStatus.DUPLICATE_IN_BATCH);
    }

    public int duplicateExistingCount() {
        return count(ResourceAclImportItemStatus.DUPLICATE_EXISTING);
    }

    public int conflictCount() {
        return count(ResourceAclImportItemStatus.CONFLICT);
    }

    public int unsupportedScopeCount() {
        return count(ResourceAclImportItemStatus.UNSUPPORTED_SCOPE);
    }

    private int count(ResourceAclImportItemStatus status) {
        return (int) items.stream()
                .filter(item -> item.status() == status)
                .count();
    }
}
