import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const srcRoot = resolve(__dirname, "../../..");

function readSource(path: string) {
  return readFileSync(resolve(srcRoot, path), "utf8");
}

describe("Run Profile admin route", () => {
  it("registers the run profile and run experiment admin routes behind agent run management", () => {
    const routerSource = readSource("router.tsx");
    const adminLayoutSource = readSource("pages/admin/AdminLayout.tsx");

    expect(routerSource).toContain('import { RunProfilePage } from "@/pages/admin/run-profiles/RunProfilePage";');
    expect(routerSource).toContain('import { RunExperimentPage } from "@/pages/admin/run-profiles/RunExperimentPage";');
    expect(routerSource).toContain(
      'path: "run-profiles", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "运行方案", <RunProfilePage />)'
    );
    expect(routerSource).toContain(
      'path: "run-experiments", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "对话实验", <RunExperimentPage />)'
    );
    expect(adminLayoutSource).toContain(
      'path: "/admin/run-profiles", feature: "AGENT_RUN_MANAGEMENT", label: "运行方案"'
    );
    expect(adminLayoutSource).toContain(
      'path: "/admin/run-experiments", feature: "AGENT_RUN_MANAGEMENT", label: "对话实验"'
    );
    expect(adminLayoutSource).toContain('"run-profiles": "运行方案"');
    expect(adminLayoutSource).toContain('"run-experiments": "对话实验"');
  });
});
