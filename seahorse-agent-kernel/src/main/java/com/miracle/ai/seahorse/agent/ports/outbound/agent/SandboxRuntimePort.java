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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;

import java.time.Instant;

public interface SandboxRuntimePort {

    SandboxSession createSession(SandboxSessionRequest request);

    SandboxExecutionResult execute(SandboxExecutionRequest request);

    static SandboxRuntimePort unsupported() {
        return new SandboxRuntimePort() {
            @Override
            public SandboxSession createSession(SandboxSessionRequest request) {
                return SandboxSession.created(
                        "sandbox_unsupported_" + request.runId(),
                        request.tenantId(),
                        request.runId(),
                        request.runtimeType(),
                        Instant.now());
            }

            @Override
            public SandboxExecutionResult execute(SandboxExecutionRequest request) {
                Instant now = Instant.now();
                SandboxExecution execution = SandboxExecution.failed(
                        "sandbox_exec_unsupported_" + request.session().sessionId(),
                        request.session().sessionId(),
                        request.session().runtimeType(),
                        now,
                        SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED);
                return SandboxExecutionResult.failed(execution, SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED);
            }
        };
    }
}
