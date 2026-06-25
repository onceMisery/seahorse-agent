import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

function readArg(name, fallback) {
  const index = process.argv.indexOf(name);
  if (index >= 0 && process.argv[index + 1]) {
    return process.argv[index + 1];
  }
  return fallback;
}

function hasFlag(name) {
  return process.argv.includes(name);
}

function loadPlaywright() {
  const localRequire = createRequire(import.meta.url);
  try {
    return localRequire("playwright");
  } catch {
    const runtimeDir = process.env.PLAYWRIGHT_RUNTIME_DIR || path.join(repoRoot, "output", "playwright");
    const runtimePackage = path.join(runtimeDir, "package.json");
    return createRequire(runtimePackage)("playwright");
  }
}

const { chromium } = loadPlaywright();

const baseUrl = readArg("--base-url", process.env.E2E_BASE_URL || "http://127.0.0.1").replace(/\/$/, "");
const username = readArg("--username", process.env.E2E_USERNAME || "admin");
const password = readArg("--password", process.env.E2E_PASSWORD || "admin123");
const artifactDir = path.resolve(readArg("--artifact-dir", process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")));
const headless = !hasFlag("--headed");
const postgresContainer = process.env.E2E_POSTGRES_CONTAINER || "seahorse-postgres";
const postgresUser = process.env.E2E_POSTGRES_USER || "seahorse";
const postgresDatabase = process.env.E2E_POSTGRES_DATABASE || "seahorse";

await fs.mkdir(artifactDir, { recursive: true });

async function api(pathname, options = {}) {
  const response = await fetch(`${baseUrl}/api${pathname}`, {
    ...options,
    headers: {
      ...(options.body ? { "content-type": "application/json" } : {}),
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`API ${pathname} returned non-JSON: ${text.slice(0, 300)}`);
  }
  if (!response.ok) {
    throw new Error(`API ${pathname} HTTP ${response.status}: ${text.slice(0, 500)}`);
  }
  if (payload && payload.code !== "0") {
    throw new Error(`API ${pathname} code ${payload.code}: ${payload.message || ""}`);
  }
  return payload?.data;
}

function psql(sql) {
  const result = spawnSync("docker", [
    "exec",
    "-i",
    postgresContainer,
    "psql",
    "-U",
    postgresUser,
    "-d",
    postgresDatabase,
    "-t",
    "-A",
    "-F",
    "|",
    "-v",
    "ON_ERROR_STOP=1"
  ], {
    input: sql,
    encoding: "utf8"
  });
  if (result.status !== 0) {
    throw new Error(`psql failed: ${result.stderr || result.stdout}`);
  }
  return result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

async function loginApi() {
  const data = await api("/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
  if (!data?.token || !data?.userId) {
    throw new Error(`login API did not return token/userId: ${JSON.stringify(data)}`);
  }
  return data;
}

function sqlLiteral(value) {
  return String(value).replaceAll("'", "''");
}

function cleanupPreviousSmokeMemories(userId) {
  psql(`
update t_short_term_memory
set deleted = 1,
    update_time = now()
where user_id = ${userId}
  and id like 'codxmg%'
  and deleted = 0;
`);
}

function seedConflictMemories(userId, marker) {
  const suffix = Date.now();
  const memoryIdA = `codxmgA${suffix}`;
  const memoryIdB = `codxmgB${suffix}`;
  const semanticKey = `profile:occupation:${marker}`;
  const metadataA = {
    semanticKey,
    importanceScore: 0.95,
    confidenceLevel: 0.92,
    marker,
    userId: String(userId),
    tenantId: "default"
  };
  const metadataB = {
    semanticKey,
    importanceScore: 0.94,
    confidenceLevel: 0.91,
    marker,
    userId: String(userId),
    tenantId: "default"
  };
  psql(`
insert into t_short_term_memory
  (id, user_id, tenant_id, conversation_id, memory_type, content, metadata_json,
   source_message_ids, importance_score, status, create_time, update_time, deleted)
values
  ('${memoryIdA}', ${userId}, 'default', null, 'PROFILE',
   'Memory governance smoke A ${sqlLiteral(marker)} says occupation is reliability engineer',
   $json$${JSON.stringify(metadataA)}$json$::jsonb, '[]'::jsonb, 0.950, 'ACTIVE', now(), now(), 0),
  ('${memoryIdB}', ${userId}, 'default', null, 'PROFILE',
   'Memory governance smoke B ${sqlLiteral(marker)} says occupation is product designer',
   $json$${JSON.stringify(metadataB)}$json$::jsonb, '[]'::jsonb, 0.940, 'ACTIVE', now(), now(), 0);
`);
  return { memoryIdA, memoryIdB };
}

async function runGovernance(auth, userId, marker, memoryIdA, memoryIdB) {
  const result = await api(`/memories/governance/run?userId=${encodeURIComponent(userId)}&reason=${encodeURIComponent(marker)}&assessQuality=true`, {
    method: "POST",
    headers: auth
  });
  if (Array.isArray(result?.errors) && result.errors.length > 0) {
    throw new Error(`governance returned errors: ${JSON.stringify(result.errors)}`);
  }

  const pending = await api(`/memories/conflicts?userId=${encodeURIComponent(userId)}&status=PENDING&limit=50`, {
    headers: auth
  });
  const conflict = (Array.isArray(pending) ? pending : []).find((item) =>
    (item.memoryId1 === memoryIdA && item.memoryId2 === memoryIdB) ||
    (item.memoryId1 === memoryIdB && item.memoryId2 === memoryIdA)
  );
  if (!conflict?.id) {
    throw new Error(`PENDING conflict was not returned for seeded memories: ${JSON.stringify(pending)}`);
  }

  const open = await api(`/memories/conflicts?userId=${encodeURIComponent(userId)}&status=open&limit=50`, {
    headers: auth
  });
  if (Array.isArray(open) && open.some((item) => item.id === conflict.id)) {
    throw new Error("legacy status=open unexpectedly returned the seeded PENDING conflict");
  }

  const snapshots = await api(`/memories/quality-snapshots?userId=${encodeURIComponent(userId)}&limit=5`, {
    headers: auth
  });
  if (!Array.isArray(snapshots) || snapshots.length === 0) {
    throw new Error("governance did not create a quality snapshot");
  }
  return { conflict, latestSnapshot: snapshots[0] };
}

async function loginBrowser(page, login) {
  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await page.evaluate((data) => {
    window.localStorage.setItem("seahorse_agent_token", data.token);
    window.localStorage.setItem("seahorse_agent_user", JSON.stringify({
      userId: data.userId,
      username: data.username || "admin",
      role: data.role || "admin",
      token: data.token
    }));
  }, login);
}

async function verifyPageAndResolve(page, seeded, conflictId) {
  await page.goto(`${baseUrl}/admin/memory-governance`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.locator('[role="tab"]').nth(1).click();
  await page.waitForFunction(
    (memoryId) => document.body.innerText.includes(memoryId),
    seeded.memoryIdA,
    { timeout: 20000 }
  );
  await page.waitForFunction(
    (memoryId) => document.body.innerText.includes(memoryId),
    seeded.memoryIdB,
    { timeout: 20000 }
  );
  await page.waitForFunction(
    () => document.body.innerText.includes("PENDING"),
    null,
    { timeout: 10000 }
  );

  const conflictCard = page
    .locator(".p-4.bg-slate-50.rounded-lg")
    .filter({ hasText: seeded.memoryIdA })
    .filter({ hasText: seeded.memoryIdB })
    .first();
  await conflictCard.getByRole("button", { name: "解决" }).click();
  await page.locator('[role="dialog"]').waitFor({ state: "visible", timeout: 10000 });
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes(`/memories/conflicts/${encodeURIComponent(conflictId)}/resolve`),
    { timeout: 15000 }
  );
  await page.getByRole("button", { name: "确认" }).click();
  const response = await responsePromise;
  if (response.status() < 200 || response.status() >= 300) {
    const body = await response.text().catch(() => "");
    throw new Error(`resolve API returned HTTP ${response.status()}: ${body.slice(0, 500)}`);
  }

  await page.locator('[role="tab"]').nth(2).click();
  await page.waitForFunction(
    () => document.body.innerText.includes("短期记忆") && document.body.innerText.includes("语义记忆"),
    null,
    { timeout: 15000 }
  );
}

function verifyResolvedInDb(conflictId) {
  const rows = psql(`
select id, resolution_status, resolution_action, resolved_by
from t_memory_conflict_log
where id = '${sqlLiteral(conflictId)}'
  and deleted = 0
limit 1;
`);
  const row = rows[0] || "";
  if (!row.includes(`${conflictId}|RESOLVED|keep_a|`)) {
    throw new Error(`conflict was not resolved in DB: ${row}`);
  }
  return row;
}

const marker = `CODX_MEMORY_GOVERNANCE_${Date.now()}`;
const login = await loginApi();
const auth = { Authorization: `Bearer ${login.token}` };
cleanupPreviousSmokeMemories(login.userId);
const seeded = seedConflictMemories(login.userId, marker);
const governance = await runGovernance(auth, login.userId, marker, seeded.memoryIdA, seeded.memoryIdB);

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });

try {
  await loginBrowser(page, login);
  await verifyPageAndResolve(page, seeded, governance.conflict.id);
  const screenshot = path.join(artifactDir, `memory-governance-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  const resolvedRow = verifyResolvedInDb(governance.conflict.id);
  console.log(JSON.stringify({
    ok: true,
    marker,
    userId: String(login.userId),
    memoryIdA: seeded.memoryIdA,
    memoryIdB: seeded.memoryIdB,
    conflictId: governance.conflict.id,
    conflictStatusBeforeResolve: governance.conflict.resolutionStatus,
    qualitySnapshotId: governance.latestSnapshot?.id,
    resolvedRow,
    screenshot
  }, null, 2));
} finally {
  await browser.close();
}
