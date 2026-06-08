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

package com.miracle.ai.seahorse.agent.ports.outbound.source;

import java.util.Objects;

public record GitHubRepositoryRequest(String repositoryUrl,
                                      String branch,
                                      int maxFiles,
                                      int maxCharsPerFile) {

    public GitHubRepositoryRequest {
        repositoryUrl = requireText(repositoryUrl, "repositoryUrl");
        branch = trimToNull(branch);
        maxFiles = maxFiles <= 0 ? 20 : maxFiles;
        maxCharsPerFile = maxCharsPerFile <= 0 ? 8_000 : maxCharsPerFile;
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
