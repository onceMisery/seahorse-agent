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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactDisposition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentArtifactControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldExposeArtifactMetadataAndRunArtifacts() throws Exception {
        AgentArtifactQueryInboundPort port = mock(AgentArtifactQueryInboundPort.class);
        AgentArtifact artifact = artifact("artifact-1", AgentArtifactType.REPORT, "text/markdown");
        when(port.getById("artifact-1")).thenReturn(artifact);
        when(port.listByRunId("run-1")).thenReturn(List.of(artifact));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseAgentArtifactController(
                provider(AgentArtifactQueryInboundPort.class, port),
                provider(ObjectStoragePort.class, null))).build();

        mvc.perform(get("/api/agent-artifacts/artifact-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artifactId").value("artifact-1"))
                .andExpect(jsonPath("$.data.canPreview").value(true))
                .andExpect(jsonPath("$.data.disposition").value("INLINE_PREVIEW"))
                .andExpect(jsonPath("$.data.storageRef").doesNotExist());

        mvc.perform(get("/api/agent-runs/run-1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].artifactId").value("artifact-1"))
                .andExpect(jsonPath("$.data[0].storageRef").doesNotExist());

        verify(port).getById("artifact-1");
        verify(port).listByRunId("run-1");
    }

    @Test
    void shouldDownloadArtifactWithSafeContentDisposition() throws Exception {
        AgentArtifactQueryInboundPort port = mock(AgentArtifactQueryInboundPort.class);
        ObjectStoragePort storagePort = mock(ObjectStoragePort.class);
        AgentArtifact artifact = artifact("artifact-html", AgentArtifactType.HTML, "text/html");
        when(port.downloadDecision("artifact-html")).thenReturn(new AgentArtifactDownloadDecision(
                artifact,
                AgentArtifactDisposition.ATTACHMENT_DOWNLOAD,
                "text/html",
                "artifact-html.html",
                "s3://agent-artifacts/artifact-html",
                false));
        when(storagePort.openStream("s3://agent-artifacts/artifact-html"))
                .thenReturn(new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseAgentArtifactController(
                provider(AgentArtifactQueryInboundPort.class, port),
                provider(ObjectStoragePort.class, storagePort))).build();

        mvc.perform(get("/api/agent-artifacts/artifact-html/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"artifact-html.html\""))
                .andExpect(content().string("<html></html>"));
    }

    private static AgentArtifact artifact(String artifactId, AgentArtifactType type, String mimeType) {
        return new AgentArtifact(
                artifactId,
                "run-1",
                "message-1",
                "tenant-a",
                "user-1",
                type,
                "Research report",
                mimeType,
                "s3://agent-artifacts/" + artifactId,
                "preview",
                "{}",
                AgentArtifactScanStatus.CLEAN,
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
