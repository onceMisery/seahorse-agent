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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentArtifactTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAllowInlinePreviewOnlyForCleanPassiveTextArtifacts() {
        AgentArtifact markdown = artifact("artifact-md", AgentArtifactType.MARKDOWN, "text/markdown",
                AgentArtifactScanStatus.CLEAN);
        AgentArtifact html = artifact("artifact-html", AgentArtifactType.HTML, "text/html",
                AgentArtifactScanStatus.CLEAN);
        AgentArtifact svg = artifact("artifact-svg", AgentArtifactType.FILE, "image/svg+xml",
                AgentArtifactScanStatus.CLEAN);
        AgentArtifact pending = artifact("artifact-pending", AgentArtifactType.MARKDOWN, "text/markdown",
                AgentArtifactScanStatus.PENDING);

        assertTrue(markdown.canPreview());
        assertEquals(AgentArtifactDisposition.INLINE_PREVIEW, markdown.disposition());
        assertFalse(html.canPreview());
        assertEquals(AgentArtifactDisposition.ATTACHMENT_DOWNLOAD, html.disposition());
        assertFalse(svg.canPreview());
        assertEquals(AgentArtifactDisposition.ATTACHMENT_DOWNLOAD, svg.disposition());
        assertFalse(pending.canPreview());
    }

    @Test
    void shouldRejectBlankRequiredFields() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AgentArtifact(
                        " ",
                        "run-1",
                        null,
                        "tenant-a",
                        "user-1",
                        AgentArtifactType.REPORT,
                        "Report",
                        "text/markdown",
                        "s3://bucket/report.md",
                        "preview",
                        "{}",
                        AgentArtifactScanStatus.CLEAN,
                        NOW));

        assertEquals("artifactId must not be blank", error.getMessage());
    }

    private static AgentArtifact artifact(String artifactId,
                                          AgentArtifactType artifactType,
                                          String mimeType,
                                          AgentArtifactScanStatus scanStatus) {
        return new AgentArtifact(
                artifactId,
                "run-1",
                "message-1",
                "tenant-a",
                "user-1",
                artifactType,
                "Research report",
                mimeType,
                "s3://agent-artifacts/" + artifactId,
                "Safe preview",
                "{\"source\":\"test\"}",
                scanStatus,
                NOW);
    }
}
