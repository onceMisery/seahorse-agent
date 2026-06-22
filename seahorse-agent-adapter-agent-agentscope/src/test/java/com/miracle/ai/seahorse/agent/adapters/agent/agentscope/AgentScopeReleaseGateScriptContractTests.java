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

class AgentScopeReleaseGateScriptContractTests {

    @Test
    void releaseGateScriptShouldRunUnitApplicationBuildAndOptionalLiveChecks() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "agentscope-release-gate.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content).contains(
                "[string]$MainUrl = \"http://127.0.0.1:9090/a2a\"",
                "[string]$NacosServer = \"127.0.0.1:8848\"");
        assertThat(content).contains("mvn -pl seahorse-agent-adapter-agent-agentscope -am test");
        assertThat(content).contains("AgentScopeReActExecutorTests", "A2aAgentRemoteInvokerTests");
        assertThat(content).contains("\"-DfailIfNoTests=false\"", "\"-Dsurefire.failIfNoSpecifiedTests=false\"");
        assertThat(content).contains("agentscope-kernel-run-contracts");
        assertThat(content).contains("KernelChatAgentRunStoreTests");
        assertThat(content).contains("KernelChatInboundServiceAgentScopeEngineSmokeTests");
        assertThat(content).contains("mvn -pl seahorse-agent-bootstrap -am package");
        assertThat(content).contains("\"-DforkCount=0\"");
        assertThat(content).contains("$LASTEXITCODE", "failed with exit");
        assertThat(content).contains("agentscope-a2a-e2e.ps1");
        assertThat(content).contains("-AuthMode shared-secret");
        assertThat(content).contains("-AuthMode tenant-signed");
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
