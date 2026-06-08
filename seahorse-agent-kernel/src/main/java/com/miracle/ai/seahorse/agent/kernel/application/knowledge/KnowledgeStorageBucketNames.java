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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import java.util.Locale;
import java.util.Objects;

final class KnowledgeStorageBucketNames {

    private static final int MAX_BUCKET_LENGTH = 63;
    private static final String PREFIX = "kb";
    private static final String FALLBACK_BASE = "collection";

    private KnowledgeStorageBucketNames() {
    }

    static String fromCollectionName(String collectionName) {
        String source = Objects.requireNonNullElse(collectionName, "").trim();
        String hash = Integer.toUnsignedString(source.hashCode(), 16);
        String base = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = FALLBACK_BASE;
        }
        String suffix = "-" + hash;
        int maxBaseLength = MAX_BUCKET_LENGTH - PREFIX.length() - 1 - suffix.length();
        if (base.length() > maxBaseLength) {
            base = base.substring(0, Math.max(1, maxBaseLength)).replaceAll("-+$", "");
            if (base.isBlank()) {
                base = FALLBACK_BASE;
            }
        }
        return PREFIX + "-" + base + suffix;
    }
}
