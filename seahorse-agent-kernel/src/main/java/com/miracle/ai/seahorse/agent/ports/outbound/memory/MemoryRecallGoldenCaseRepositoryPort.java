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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.util.List;
import java.util.Optional;

/**
 * Loads named bundles of golden recall cases for offline benchmarking.
 *
 * <p>The harness service uses profiles to drive recall quality evaluations against the live
 * pipeline without forcing callers to embed cases in the request payload. Implementations may
 * read from the classpath, a database, or a remote service; the kernel only cares about the
 * profile contract.
 */
public interface MemoryRecallGoldenCaseRepositoryPort {

    Optional<MemoryRecallGoldenCaseProfile> findByName(String profileName);

    List<String> listProfileNames();

    static MemoryRecallGoldenCaseRepositoryPort empty() {
        return new MemoryRecallGoldenCaseRepositoryPort() {
            @Override
            public Optional<MemoryRecallGoldenCaseProfile> findByName(String profileName) {
                return Optional.empty();
            }

            @Override
            public List<String> listProfileNames() {
                return List.of();
            }
        };
    }
}
