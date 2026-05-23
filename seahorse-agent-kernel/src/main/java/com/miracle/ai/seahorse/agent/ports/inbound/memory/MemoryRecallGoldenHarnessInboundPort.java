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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import java.util.List;

/**
 * Drives recall quality benchmarks against curated golden case profiles.
 *
 * <p>Sits one layer above {@link MemoryRecallEvaluationInboundPort}: callers reference a
 * profile by name (typically curated in source control) and the harness loads the cases via a
 * repository port and delegates to the evaluation service. This keeps the heavy benchmark
 * payload out of HTTP request bodies and CI scripts.
 */
public interface MemoryRecallGoldenHarnessInboundPort {

    MemoryRecallEvaluationReport runProfile(String profileName);

    List<String> listProfiles();
}
