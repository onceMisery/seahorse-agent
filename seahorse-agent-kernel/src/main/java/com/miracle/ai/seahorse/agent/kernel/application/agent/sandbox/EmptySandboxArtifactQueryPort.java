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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;

import java.util.List;
import java.util.Optional;

/**
 * 空工件查询端口（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class EmptySandboxArtifactQueryPort implements SandboxArtifactQueryPort {

    @Override
    public Optional<SandboxArtifact> findArtifactById(String artifactId) {
        return Optional.empty();
    }

    @Override
    public List<SandboxArtifact> listArtifactsBySession(String sessionId) {
        return List.of();
    }

    @Override
    public List<SandboxArtifact> listPromptVisibleBySession(String sessionId) {
        return List.of();
    }
}
