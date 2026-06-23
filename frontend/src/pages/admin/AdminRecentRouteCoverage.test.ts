import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const srcRoot = resolve(__dirname, "../..");

function readSource(path: string) {
  return readFileSync(resolve(srcRoot, path), "utf8");
}

const recentAdminEntries = [
  {
    label: "Skill 管理",
    route: 'path: "skills"',
    menu: 'path: "/admin/skills", feature: "SKILL_MANAGEMENT", label: "Skill 管理"',
    breadcrumb: 'skills: "Skill 管理"'
  },
  {
    label: "Agent 控制台",
    route: 'path: "ai-infra"',
    menu: 'path: "/admin/ai-infra", feature: "AI_INFRA_CONSOLE", label: "Agent 控制台"',
    breadcrumb: '"ai-infra": "AI Infra 控制台"'
  },
  {
    label: "审批中心",
    route: 'path: "approvals"',
    menu: 'path: "/admin/approvals", feature: "AGENT_RUN_MANAGEMENT", label: "审批中心"',
    breadcrumb: 'approvals: "审批中心"'
  },
  {
    label: "工具目录",
    route: 'path: "tools"',
    menu: 'path: "/admin/tools", feature: "TOOL_CATALOG_MANAGEMENT", label: "工具目录"',
    breadcrumb: 'tools: "工具目录"'
  },
  {
    label: "工具调用审计",
    route: 'path: "tool-invocations"',
    menu: 'path: "/admin/tool-invocations", feature: "TOOL_CATALOG_MANAGEMENT", label: "工具调用审计"',
    breadcrumb: '"tool-invocations": "工具调用审计"'
  },
  {
    label: "OpenAPI 连接器",
    route: 'path: "integrations/connectors"',
    menu: 'path: "/admin/integrations/connectors", feature: "CONNECTOR_MANAGEMENT", label: "OpenAPI 连接器"',
    breadcrumb: 'connectors: "OpenAPI 连接器"'
  },
  {
    label: "资源 ACL",
    route: 'path: "security/resource-acl"',
    menu: 'path: "/admin/security/resource-acl", feature: "RESOURCE_ACL_MANAGEMENT", label: "资源 ACL"',
    breadcrumb: '"resource-acl": "资源 ACL"'
  },
  {
    label: "访问决策",
    route: 'path: "security/access-decisions"',
    menu: 'path: "/admin/security/access-decisions", feature: "RESOURCE_ACL_MANAGEMENT", label: "访问决策"',
    breadcrumb: '"access-decisions": "访问决策"'
  },
  {
    label: "计费管理",
    route: 'path: "billing"',
    menu: 'path: "/admin/billing", label: "计费管理"',
    breadcrumb: 'billing: "计费管理"'
  },
  {
    label: "成本分析",
    route: 'path: "cost"',
    menu: 'path: "/admin/cost", feature: "COST_ANALYTICS", label: "成本分析"',
    breadcrumb: 'cost: "成本分析"'
  },
  {
    label: "审计日志",
    route: 'path: "audit"',
    menu: 'path: "/admin/audit", feature: "AUDIT_LOG", label: "审计日志"',
    breadcrumb: 'audit: "审计日志"'
  },
  {
    label: "运营审计",
    route: 'path: "audit-logs"',
    menu: 'path: "/admin/audit-logs", feature: "TENANT_MANAGEMENT", label: "运营审计"',
    breadcrumb: '"audit-logs": "运营审计"'
  }
];

describe("recent admin route coverage", () => {
  it("keeps recent governance pages reachable from router, menu, and breadcrumb", () => {
    const routerSource = readSource("router.tsx");
    const adminLayoutSource = readSource("pages/admin/AdminLayout.tsx");

    for (const entry of recentAdminEntries) {
      expect(routerSource, `${entry.label} route`).toContain(entry.route);
      expect(adminLayoutSource, `${entry.label} menu`).toContain(entry.menu);
      expect(adminLayoutSource, `${entry.label} breadcrumb`).toContain(entry.breadcrumb);
    }
  });
});
