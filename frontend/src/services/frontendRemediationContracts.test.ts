import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import * as agentFactoryService from "@/services/agentFactoryService";

const srcRoot = resolve(__dirname, "..");

function readSource(path: string) {
  return readFileSync(resolve(srcRoot, path), "utf8");
}

describe("frontend remediation contracts", () => {
  it("guards the approval center with agent run management", () => {
    const routerSource = readSource("router.tsx");
    const adminLayoutSource = readSource("pages/admin/AdminLayout.tsx");
    const approvalPageSource = readSource("pages/admin/approvals/ApprovalCenterPage.tsx");

    expect(routerSource).toContain(
      'path: "approvals", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "审批中心"'
    );
    expect(adminLayoutSource).toMatch(
      /path: "\/admin\/approvals",\s+feature: "AGENT_RUN_MANAGEMENT",/
    );
    expect(approvalPageSource).toContain(
      "getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT)"
    );
  });

  it("keeps AI Infra child capabilities on their backend features", () => {
    const source = readSource("pages/admin/ai-infra/AiInfraConsolePage.tsx");

    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_EVALUATION)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.ENTERPRISE_PILOT_READINESS)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_ROLLOUT_MANAGEMENT)");
    expect(source).toContain("getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.COST_ANALYTICS)");
  });

  it("guards AI Infra operations panels before disabled backend APIs can be triggered", () => {
    const source = readSource("pages/admin/ai-infra/AiInfraConsolePage.tsx");

    expect(source).toContain('label="试点就绪度"');
    expect(source).toContain('label="评估回归"');
    expect(source).toContain('label="发布管理"');
    expect(source).toContain('disabled={!readinessFeatureState.enabled || actionLoading === "readiness:generate"}');
    expect(source).toContain('disabled={!feedbackFeatureState.enabled || actionLoading === "eval:regression"}');
    expect(source).toContain('disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:create"}');
  });

  it("keeps the Agent console chrome localized for Chinese operators", () => {
    const source = readSource("pages/admin/ai-infra/AiInfraConsolePage.tsx");

    expect(source).toContain("<h1 className=\"admin-page-title\">Agent 控制台</h1>");
    expect(source).toContain('label: "总览"');
    expect(source).toContain('label: "审批"');
    expect(source).toContain('label: "反馈评估"');
    expect(source).toContain('title="审批收件箱"');
    expect(source).toContain('title="工具目录"');
    expect(source).toContain("<TableHead>审批 ID</TableHead>");
    expect(source).toContain("刷新");
    expect(source).not.toContain("AI Infra Console");
    expect(source).not.toContain("Approval Inbox");
    expect(source).not.toContain("Tool Catalog");
  });

  it("does not expose the missing single agent template endpoint", () => {
    expect("getAgentTemplate" in agentFactoryService).toBe(false);
    expect(readSource("services/agentFactoryService.ts")).not.toContain("/api/agent-templates/${");
  });

  it("links official docs buttons to the official repository without GitHub API calls", () => {
    const sourceFiles = [
      "pages/admin/AdminLayout.tsx",
      "components/layout/Header.tsx",
      "components/layout/Sidebar.tsx"
    ];
    const officialRepositoryUrl = "https://github.com/onceMisery/seahorse-agent";

    for (const sourceFile of sourceFiles) {
      const source = readSource(sourceFile);
      expect(source, sourceFile).not.toContain("api.github.com");
      expect(source, sourceFile).toContain(officialRepositoryUrl);
    }
  });

  it("keeps metadata governance components behind the service layer", () => {
    const componentFiles = [
      "pages/admin/metadata-governance/components/MetadataDictionaryPanel.tsx",
      "pages/admin/metadata-governance/components/MetadataExtractionResultDrawer.tsx",
      "pages/admin/metadata-governance/components/MetadataQuarantinePanel.tsx",
      "pages/admin/metadata-governance/components/MetadataBackfillPanel.tsx",
      "pages/admin/metadata-governance/components/MetadataSchemaManager.tsx",
      "pages/admin/metadata-governance/components/MetadataReviewDetailDrawer.tsx"
    ];

    for (const componentFile of componentFiles) {
      const source = readSource(componentFile);
      expect(source, componentFile).not.toContain("@/services/api");
      expect(source, componentFile).not.toMatch(/\bapi\.(get|post|put|delete|patch)\b/);
    }
  });

  it("exposes existing knowledge operations from the documents page", () => {
    const source = readSource("pages/admin/knowledge/KnowledgeDocumentsPage.tsx");

    expect(source).toContain("refreshDueDocuments");
    expect(source).toContain("rebuildKbKeywordIndex");
    expect(source).toContain("refreshDocument");
    expect(source).toContain("rebuildDocumentKeywordIndex");
    expect(source).toContain("setKnowledgeOperationTarget");
    expect(source).toContain("handleConfirmKnowledgeOperation");
  });

  it("renders SRE health items instead of only raw AI Infra JSON", () => {
    const source = readSource("pages/admin/ai-infra/AiInfraConsolePage.tsx");

    expect(source).toContain("function SreHealthItems");
    expect(source).toContain("asRecordArray(sreHealth?.items)");
    expect(source).toContain("contributorName");
    expect(source).toContain("evidenceRef");
  });
});
