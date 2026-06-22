import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const srcRoot = resolve(__dirname, "../../..");

function readSource(path: string) {
  return readFileSync(resolve(srcRoot, path), "utf8");
}

describe("Role Card admin route", () => {
  it("registers the role card admin route behind agent run management", () => {
    const routerSource = readSource("router.tsx");
    const adminLayoutSource = readSource("pages/admin/AdminLayout.tsx");

    expect(routerSource).toContain('import { RoleCardPage } from "@/pages/admin/role-cards/RoleCardPage";');
    expect(routerSource).toContain(
      'path: "role-cards", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "角色卡", <RoleCardPage />)'
    );
    expect(adminLayoutSource).toContain(
      'path: "/admin/role-cards", feature: "AGENT_RUN_MANAGEMENT", label: "角色卡"'
    );
    expect(adminLayoutSource).toContain('"role-cards": "角色卡"');
  });
});
