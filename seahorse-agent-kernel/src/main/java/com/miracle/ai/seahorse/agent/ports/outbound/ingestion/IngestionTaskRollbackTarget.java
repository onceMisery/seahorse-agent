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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import java.util.Objects;

/**
 * The concrete document target that an ingestion task rollback may compensate.
 */
public record IngestionTaskRollbackTarget(
        String taskId,
        Long kbId,
        Long docId,
        String collectionName
) {

    public IngestionTaskRollbackTarget {
        taskId = requireText(taskId, "taskId");
        kbId = Objects.requireNonNull(kbId, "kbId must not be null");
        docId = Objects.requireNonNull(docId, "docId must not be null");
        collectionName = Objects.requireNonNullElse(collectionName, "");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
