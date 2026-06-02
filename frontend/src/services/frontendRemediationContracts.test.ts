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

    expect(source).toContain("Pilot readiness unavailable");
    expect(source).toContain("Eval regression unavailable");
    expect(source).toContain("Rollout unavailable");
    expect(source).toContain('disabled={!readinessFeatureState.enabled || actionLoading === "readiness:generate"}');
    expect(source).toContain('disabled={!feedbackFeatureState.enabled || actionLoading === "eval:regression"}');
    expect(source).toContain('disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:create"}');
  });

  it("does not expose the missing single agent template endpoint", () => {
    expect("getAgentTemplate" in agentFactoryService).toBe(false);
    expect(readSource("services/agentFactoryService.ts")).not.toContain("/api/agent-templates/${");
  });

  it("does not call external GitHub APIs from the frontend", () => {
    const sourceFiles = [
      "pages/admin/AdminLayout.tsx",
      "components/layout/Header.tsx",
      "components/layout/Sidebar.tsx"
    ];

    for (const sourceFile of sourceFiles) {
      const source = readSource(sourceFile);
      expect(source, sourceFile).not.toContain("api.github.com");
      expect(source, sourceFile).not.toContain("onceMisery/seahorse-agent");
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
