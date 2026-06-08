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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record GitHubRepositorySnapshot(String owner,
                                       String repository,
                                       String defaultBranch,
                                       String htmlUrl,
                                       String description,
                                       List<GitHubRepositoryFile> files,
                                       boolean truncated,
                                       Instant fetchedAt) {

    public GitHubRepositorySnapshot {
        owner = Objects.requireNonNullElse(owner, "").trim();
        repository = Objects.requireNonNullElse(repository, "").trim();
        defaultBranch = Objects.requireNonNullElse(defaultBranch, "").trim();
        htmlUrl = Objects.requireNonNullElse(htmlUrl, "").trim();
        description = Objects.requireNonNullElse(description, "");
        files = files == null ? List.of() : List.copyOf(files);
        fetchedAt = Objects.requireNonNullElseGet(fetchedAt, Instant::now);
    }
}
