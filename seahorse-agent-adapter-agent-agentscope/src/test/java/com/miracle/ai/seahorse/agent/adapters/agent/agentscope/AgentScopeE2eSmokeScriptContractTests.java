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

class AgentScopeE2eSmokeScriptContractTests {

    @Test
    void fullDockerSmokeShouldAssertSseEquivalenceAndTraceSnapshots() throws Exception {
        Path script = repositoryRoot().resolve(Path.of("scripts", "e2e-agentscope-smoke.ps1"));

        assertThat(script).exists();
        String content = Files.readString(script);

        assertThat(content).contains(
                "ConvertFrom-SseContent",
                "Assert-ChatSseContract",
                "Assert-SseEquivalentContract",
                "Assert-SnapshotMatchesChat",
                "Assert-AgentScopeSnapshotTraceContext");
        assertThat(content).contains(
                "meta",
                "message",
                "finish",
                "done",
                "stream_event",
                "recoverable_error");
        assertThat(content).contains(
                "Get-SseRunId",
                "Get-SseResponseText",
                "ResponseChars",
                "StreamEventCount",
                "AgentScope/kernel SSE response text must both be non-empty",
                "AgentScope/kernel SSE must both include stream_event envelopes");
        assertThat(content).contains(
                "trace_context_json",
                "traceId",
                "studioTraceUrl",
                "agentScopeTraceEnabled",
                "agentScopeStudioUrl",
                "SSE/snapshot run_id");
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
