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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfile;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxEgressPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;

import java.util.List;

public interface SandboxRuntimeInboundPort {

    SandboxSession createSession(SandboxSessionCreateCommand command);

    SandboxExecutionResult execute(SandboxExecutionCommand command);

    SandboxSession close(String sessionId);

    List<SandboxSession> listSessions(String tenantId, int limit);

    SandboxSessionSweepResult sweepExpiredSessions(String tenantId, int limit);

    SandboxRuntimeCleanupResult sweepOrphanedRuntimeResources();

    SandboxRuntimeHealth inspectRuntimeHealth();

    default List<SandboxRuntimeNodeHealth> inspectRuntimeNodes() {
        return List.of(SandboxRuntimeNodeHealth.fromHealth(inspectRuntimeHealth()));
    }

    default SandboxArtifactScannerPolicy inspectArtifactScannerPolicy() {
        return SandboxArtifactScannerPolicy.unavailable();
    }

    default SandboxArtifactScannerHealth inspectArtifactScannerHealth() {
        return SandboxArtifactScannerHealth.unavailable();
    }

    SandboxRuntimeContainerReapResult reapOrphanedRuntimeContainers(boolean dryRun);

    default List<SandboxRuntimeProfilePolicy> listRuntimeProfilePolicies(String tenantId) {
        return List.of();
    }

    default SandboxRuntimeProfilePolicy upsertRuntimeProfilePolicy(SandboxRuntimeProfilePolicyUpsertCommand command) {
        throw new UnsupportedOperationException("Sandbox runtime profile policy writes are not available");
    }

    default SandboxEgressPolicy inspectSandboxEgressPolicy(String tenantId) {
        throw new UnsupportedOperationException("Sandbox egress policy inspection is not available");
    }

    default SandboxEgressPolicy upsertSandboxEgressPolicy(SandboxEgressPolicyUpsertCommand command) {
        throw new UnsupportedOperationException("Sandbox egress policy writes are not available");
    }

    default List<SandboxBrowserProfile> listSandboxBrowserProfiles(String tenantId, int limit) {
        return List.of();
    }

    default SandboxBrowserProfile upsertSandboxBrowserProfile(SandboxBrowserProfileUpsertCommand command) {
        throw new UnsupportedOperationException("Sandbox browser profile writes are not available");
    }

    default SandboxBrowserProfile disableSandboxBrowserProfile(String tenantId, String profileId) {
        throw new UnsupportedOperationException("Sandbox browser profile writes are not available");
    }

    default String readSandboxBrowserProfileSessionState(String tenantId, String profileId) {
        throw new UnsupportedOperationException("Sandbox browser profile replay is not available");
    }

    List<SandboxExecution> listExecutions(String sessionId);

    List<SandboxArtifact> listArtifacts(String sessionId);

    SandboxArtifactDetailDecision describeArtifact(String artifactId);

    SandboxArtifactDownloadDecision downloadArtifact(String artifactId);

    default String readBrowserSessionStateArtifact(String artifactId) {
        throw new UnsupportedOperationException("Sandbox browser session-state artifact replay is not available");
    }
}
