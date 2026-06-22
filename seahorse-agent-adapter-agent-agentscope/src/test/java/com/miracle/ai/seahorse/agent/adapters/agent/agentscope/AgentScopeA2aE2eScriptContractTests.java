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

class AgentScopeA2aE2eScriptContractTests {

    @Test
    void powershellE2eScriptCoversRequiredM0Checks() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "agentscope-a2a-e2e.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content).contains(
                "param(",
                "[string]$MainUrl = \"http://127.0.0.1:9090/a2a\"",
                "[string]$NacosServer = \"127.0.0.1:8848\"",
                "$MainUrl",
                "$RemotePort",
                "$NacosServer",
                "$TenantId",
                "$SharedSecret",
                "[string]$MainAgentName = \"seahorse-a\"",
                "seahorse-agent-backend:latest",
                "SEAHORSE_LIVE_A2A_SMOKE",
                "SEAHORSE_LIVE_NACOS_SERVER",
                "SEAHORSE_LIVE_A2A_AGENT_NAME",
                "SEAHORSE_LIVE_A2A_EXPECTED_URL",
                "SEAHORSE_LIVE_A2A_SHARED_SECRET");
        assertThat(content).contains(
                "E2E_AGENT=",
                "MAIN_CARD_OK",
                "MAIN_POST_NO_AUTH=",
                "MAIN_POST_WRONG_TOKEN=",
                "MAIN_POST_AUTH=",
                "REMOTE_DIRECT_OK",
                "NACOS_CONNECTOR_SMOKE_OK",
                "E2E_RESULT=PASS");
        assertThat(content).contains("finally", "docker", "rm", "-f");
    }

    @Test
    void powershellE2eScriptUsesExplicitHmacSha256Constructor() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "agentscope-a2a-e2e.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content)
                .contains("[System.Security.Cryptography.HMACSHA256]::new(")
                .doesNotContain("[System.Security.Cryptography.HMACSHA256]::Create()");
    }

    @Test
    void powershellE2eScriptDisablesMavenForksForLiveSmokeStability() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "agentscope-a2a-e2e.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content).contains("\"-DforkCount=0\"");
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
