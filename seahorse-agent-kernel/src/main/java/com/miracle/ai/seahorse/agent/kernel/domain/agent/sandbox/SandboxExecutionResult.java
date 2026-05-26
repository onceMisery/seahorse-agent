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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.util.List;
import java.util.Objects;

public record SandboxExecutionResult(SandboxExecution execution,
                                     List<SandboxArtifact> artifacts,
                                     SandboxPolicyReasonCode reasonCode) {

    public SandboxExecutionResult {
        execution = Objects.requireNonNull(execution, "execution must not be null");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        reasonCode = Objects.requireNonNullElse(reasonCode, execution.reasonCode());
    }

    public static SandboxExecutionResult succeeded(SandboxExecution execution, List<SandboxArtifact> artifacts) {
        return new SandboxExecutionResult(execution, artifacts, SandboxPolicyReasonCode.VALID_REQUEST);
    }

    public static SandboxExecutionResult failed(SandboxExecution execution, SandboxPolicyReasonCode reasonCode) {
        return new SandboxExecutionResult(execution, List.of(), reasonCode);
    }
}
