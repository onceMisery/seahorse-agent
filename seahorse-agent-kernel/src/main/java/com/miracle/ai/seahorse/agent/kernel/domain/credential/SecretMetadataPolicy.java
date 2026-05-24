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

package com.miracle.ai.seahorse.agent.kernel.domain.credential;

import java.util.Objects;

/**
 * Credential metadata invariants shared by management and persistence commands.
 */
public final class SecretMetadataPolicy {

    public static final String SECRET_PLAINTEXT_IN_METADATA_MESSAGE =
            "metadataJson must not contain secret plaintext";

    private SecretMetadataPolicy() {
    }

    public static String normalizeMetadataJson(String metadataJson, String secretPlaintext) {
        String normalized = Objects.requireNonNullElse(metadataJson, "");
        if (hasText(secretPlaintext) && normalized.contains(secretPlaintext)) {
            throw new IllegalArgumentException(SECRET_PLAINTEXT_IN_METADATA_MESSAGE);
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
