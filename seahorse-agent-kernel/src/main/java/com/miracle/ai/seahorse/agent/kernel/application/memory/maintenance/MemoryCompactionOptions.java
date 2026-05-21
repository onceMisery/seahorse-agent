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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import java.util.Objects;

public record MemoryCompactionOptions(
        int scanLimit,
        int minGroupSize,
        boolean vectorIndexEnabled,
        boolean keywordIndexEnabled,
        boolean graphIndexEnabled,
        String embeddingModel
) {

    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final int DEFAULT_MIN_GROUP_SIZE = 3;
    private static final String DEFAULT_EMBEDDING_MODEL = "default";

    public MemoryCompactionOptions {
        scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
        minGroupSize = minGroupSize <= 1 ? DEFAULT_MIN_GROUP_SIZE : minGroupSize;
        embeddingModel = Objects.requireNonNullElse(embeddingModel, DEFAULT_EMBEDDING_MODEL).trim();
        if (embeddingModel.isBlank()) {
            embeddingModel = DEFAULT_EMBEDDING_MODEL;
        }
    }

    public static MemoryCompactionOptions defaults() {
        return new MemoryCompactionOptions(
                DEFAULT_SCAN_LIMIT,
                DEFAULT_MIN_GROUP_SIZE,
                true,
                true,
                true,
                DEFAULT_EMBEDDING_MODEL);
    }
}
