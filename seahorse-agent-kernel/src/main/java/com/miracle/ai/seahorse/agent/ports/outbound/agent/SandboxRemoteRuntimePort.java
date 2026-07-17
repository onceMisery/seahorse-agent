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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;

import java.util.List;

public interface SandboxRemoteRuntimePort {

    SandboxSession createSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSessionRequest request);

    SandboxExecutionResult execute(SandboxRuntimeNodeEndpoint endpoint, SandboxExecutionRequest request);

    default SandboxRuntimeSessionOwnership inspectSessionOwnership(SandboxRuntimeNodeEndpoint endpoint,
                                                                    String sessionId) {
        return SandboxRuntimeSessionOwnership.UNSUPPORTED;
    }

    SandboxSession closeSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSession session);

    default void releaseArtifacts(SandboxSession session,
                                  List<SandboxArtifact> sourceArtifacts,
                                  List<SandboxArtifact> retainedArtifacts) {
    }
}
