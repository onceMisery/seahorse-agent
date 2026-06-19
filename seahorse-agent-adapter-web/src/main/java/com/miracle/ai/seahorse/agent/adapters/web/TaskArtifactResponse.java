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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;

import java.time.Instant;
import java.util.Locale;

/**
 * 任务级统一产物 DTO（路线图 Phase 5 Artifact 模型）。
 * <p>
 * 第一版包装现有 Agent Artifact，把 {@link AgentArtifactType} 归一化为
 * 路线图产物类型 markdown / mermaid / file / citation / trace / image，
 * 并内联可预览的小产物文本（previewText）。
 *
 * @param artifactId   产物 ID
 * @param taskId       所属任务
 * @param runId        关联 Agent run
 * @param type         归一化类型: markdown | mermaid | file | citation | trace | image
 * @param title        展示标题
 * @param mimeType     MIME 类型
 * @param content      小产物内联内容（可预览时填充，否则为 null）
 * @param canPreview   是否可内联预览
 * @param downloadUrl  下载地址（大文件/附件）
 * @param createdAt    创建时间
 */
public record TaskArtifactResponse(
        String artifactId,
        String taskId,
        String runId,
        String type,
        String title,
        String mimeType,
        String content,
        boolean canPreview,
        String downloadUrl,
        Instant createdAt
) {

    public static TaskArtifactResponse from(String taskId, AgentArtifact a) {
        return new TaskArtifactResponse(
                a.artifactId(),
                taskId,
                a.runId(),
                normalizeType(a),
                a.title(),
                a.mimeType(),
                a.canPreview() ? a.previewText() : null,
                a.canPreview(),
                "/api/agent-artifacts/" + a.artifactId() + "/download",
                a.createdAt()
        );
    }

    /**
     * 把 Agent 产物类型 + MIME 映射为路线图统一产物类型。
     * Mermaid 通过 MIME（text/vnd.mermaid / x-mermaid）或标题/正文特征识别。
     */
    private static String normalizeType(AgentArtifact a) {
        String mime = a.mimeType() == null ? "" : a.mimeType().toLowerCase(Locale.ROOT);
        if (mime.contains("mermaid")) {
            return "mermaid";
        }
        AgentArtifactType t = a.artifactType();
        return switch (t) {
            case MARKDOWN, REPORT -> {
                // Markdown 产物若正文以 mermaid 代码块为主，仍标记 markdown（前端渲染器内联识别 mermaid 块）
                yield "markdown";
            }
            case IMAGE, CHART -> "image";
            case TABLE -> "markdown";
            case HTML, FILE -> "file";
        };
    }
}
