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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentEvidenceGateScriptContractTests {

    @Test
    void deploymentEvidenceGateShouldRunAllP2DeploymentSmokesAndFailClosed() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "deployment-evidence-gate.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content).contains(
                "e2e-s3-storage-smoke.ps1",
                "e2e-pulsar-mq-smoke.ps1",
                "e2e-rag-strategy-promotion-smoke.ps1",
                "e2e-agent-rollout-smoke.ps1");
        assertThat(content).contains(
                "s3-storage-switch",
                "pulsar-consume-loop",
                "rag-strategy-promotion",
                "agent-rollout-promote");
        assertThat(content).contains(
                "DEPLOYMENT_EVIDENCE_GATE=PASS",
                "DEPLOYMENT_EVIDENCE_GATE=FAIL",
                "DEPLOYMENT_EVIDENCE_GATE_FAIL_COUNT",
                "no steps selected");
        assertThat(content).contains("powershell.exe", "-NoProfile", "-ExecutionPolicy", "-File");
        assertThat(content).contains(
                "[switch]$SkipS3",
                "[switch]$SkipPulsar",
                "[switch]$SkipRagPromotion",
                "[switch]$SkipAgentRollout");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("scripts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + Path.of("").toAbsolutePath());
    }
}
