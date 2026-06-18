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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubVisualAgentSqlAlignmentTests {

    private static final Path DATABASE_DIR = Path.of("..", "resources", "database");
    private static final Path INIT_SQL = DATABASE_DIR.resolve("seahorse_init.sql");
    private static final Path EXECUTION_PLAN_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V23__github_visual_agent_execution_plan.sql");
    private static final Path FINAL_OUTLINE_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V24__github_visual_agent_final_outline.sql");
    private static final Path WHOLE_DOCUMENT_HTML_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V25__github_visual_agent_whole_document_html_preview.sql");
    private static final Path OUTPUT_BUDGET_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V26__github_visual_agent_output_budget.sql");
    private static final Path STRICT_ARTIFACT_E2E_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V27__github_visual_agent_strict_artifact_e2e.sql");
    private static final Path MERMAID_ONLY_MIGRATION =
            DATABASE_DIR.resolve("migrations").resolve("V34__github_visual_agent_mermaid_only.sql");

    @Test
    void githubVisualAgentShouldHaveExecutionPlanContractInInitSqlAndLatestMigration() throws Exception {
        String initSql = Files.readString(INIT_SQL);
        String migrationSql = Files.readString(EXECUTION_PLAN_MIGRATION);
        String finalOutlineSql = Files.readString(FINAL_OUTLINE_MIGRATION);
        String wholeDocumentHtmlSql = Files.readString(WHOLE_DOCUMENT_HTML_MIGRATION);
        String outputBudgetSql = Files.readString(OUTPUT_BUDGET_MIGRATION);
        String strictArtifactE2eSql = Files.readString(STRICT_ARTIFACT_E2E_MIGRATION);
        String mermaidOnlySql = Files.readString(MERMAID_ONLY_MIGRATION);

        assertGithubVisualExecutionPlan(initSql);
        assertGithubVisualLegacyExecutionPlan(migrationSql);
        assertGithubVisualFinalOutline(initSql);
        assertGithubVisualLegacyFinalOutline(finalOutlineSql);
        assertGithubVisualWholeDocumentHtmlPreview(initSql);
        assertGithubVisualWholeDocumentHtmlPreview(wholeDocumentHtmlSql);
        assertGithubVisualInitialOutputBudget(initSql);
        assertGithubVisualOutputBudgetMigration(outputBudgetSql);
        assertGithubVisualStrictArtifactE2e(initSql);
        assertGithubVisualStrictArtifactE2e(strictArtifactE2eSql);
        assertGithubVisualMermaidOnly(mermaidOnlySql);
    }

    private static void assertGithubVisualExecutionPlan(String sql) {
        assertContains(sql, "GitHub visual project intro execution contract");
        assertContains(sql, "禁止连续重复使用相同参数调用 github_repository_reader");
        assertContains(sql, "成功读取仓库后必须停止使用 github_repository_reader");
        assertContains(sql, "必须按顺序推进：web_fetch -> chart_visualization"
                + " -> newsletter_generation -> ppt_generation -> frontend_design");
        assertContains(sql, "每个硬性工具至少成功调用一次后，才能输出最终 Markdown");
        assertContains(sql, "'github_repository_reader', 2, '{}'");
    }

    private static void assertGithubVisualLegacyExecutionPlan(String sql) {
        assertContains(sql, "GitHub visual project intro execution contract");
        assertContains(sql, "必须按顺序推进：web_fetch -> chart_visualization -> image_generation"
                + " -> newsletter_generation -> ppt_generation -> frontend_design");
    }

    private static void assertGithubVisualFinalOutline(String sql) {
        assertContains(sql, "最终 Markdown 必须使用固定大纲并逐节输出");
        assertContains(sql, "## 四、流程图");
        assertContains(sql, "“流程图”必须是独立章节，不能合并到“核心逻辑”");
        assertContains(sql, "至少包含一个 Mermaid sequenceDiagram 或 flowchart");
        assertContains(sql, "“2.1 整体架构分层”必须输出标准 Mermaid flowchart");
        assertContains(sql, "禁止使用 ASCII 文本框图");
        assertContains(sql, "不要调用图片生成工具，也不要要求图片 URL");
        assertContains(sql, "长文稿件摘要");
        assertContains(sql, "演示文稿摘要");
        assertContains(sql, "Web 版式预览摘要");
        assertContains(sql, "<artifact language=\"html\" title=\"项目介绍 Web 预览.html\">");
        assertContains(sql, "完整 Markdown 文档会由系统以 Markdown artifact 形式提供复制和下载");
    }

    private static void assertGithubVisualLegacyFinalOutline(String sql) {
        assertContains(sql, "最终 Markdown 必须使用固定大纲并逐节输出");
        assertContains(sql, "图片引用面向 Web 端，禁止使用本地文件路径、相对路径或 file:// 路径");
        assertContains(sql, "只允许 http/https URL 或 data:image/*;base64 URL");
    }

    private static void assertGithubVisualWholeDocumentHtmlPreview(String sql) {
        assertContains(sql, "HTML 预览 artifact 必须覆盖整篇项目介绍文档");
        assertContains(sql, "用于 Web 端完整阅读预览");
        assertContains(sql, "所有章节");
        assertContains(sql, "Mermaid 图说明");
        assertContains(sql, "关键文件证据和第九章产物摘要");
    }

    private static void assertGithubVisualInitialOutputBudget(String sql) {
        assertContains(sql, "github-visual-project-intro-agent");
        assertContains(sql, "\"maxTokens\":12000");
    }

    private static void assertGithubVisualOutputBudgetMigration(String sql) {
        assertContains(sql, "github-visual-project-intro-agent");
        assertContains(sql, "jsonb_set");
        assertContains(sql, "'{maxTokens}'");
        assertContains(sql, "'12000'::jsonb");
    }

    private static void assertGithubVisualStrictArtifactE2e(String sql) {
        assertContains(sql, "严格产物要求（用于真实 E2E 验证）");
        assertContains(sql, "newsletter.md");
        assertContains(sql, "presentation.md");
        assertContains(sql, "frontend-design-tool-output.html");
        assertContains(sql, "project-intro-web-preview.html");
        assertContains(sql, "工具 observation 是真实产物来源");
    }

    private static void assertGithubVisualMermaidOnly(String sql) {
        assertContains(sql, "Remove unstable image generation");
        assertContains(sql, "DELETE FROM sa_agent_tool_binding");
        assertContains(sql, "tool_id = 'image_generation'");
        assertContains(sql, "不要调用图片生成工具，也不要要求图片 URL");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), () -> "Expected SQL to contain: " + expected);
    }
}
