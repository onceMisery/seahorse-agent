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

function apiUrl(pathname) {
  return `${baseUrl}/api${pathname}`;
}

async function api(pathname, options = {}) {
  const response = await fetch(apiUrl(pathname), {
    ...options,
    headers: {
      "content-type": "application/json",
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

function sqlEscape(value) {
  return String(value).replaceAll("'", "''");
}

function psql(sql) {
  const result = spawnSync("docker", [
    "exec",
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
    "-c",
    sql
  ], { encoding: "utf8" });
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
  if (!data?.token) {
    throw new Error("login API did not return token");
  }
  return data.token;
}

async function createCanary(auth, agentId, versionId, canaryPercent, operator) {
  const data = await api(`/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/rollouts/canary`, {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      tenantId: "tenant-default",
      canaryPercent,
      operator
    })
  });
  if (!data?.rolloutId || data.status !== "RUNNING") {
    throw new Error(`canary create did not return RUNNING rollout: ${JSON.stringify(data)}`);
  }
  return data;
}

async function promote(auth, agentId, rolloutId, comment) {
  return api(`/api/agents/${encodeURIComponent(agentId)}/rollouts/${encodeURIComponent(rolloutId)}/promote`, {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      tenantId: "tenant-default",
      operator: "codex",
      comment
    })
  });
}

function seedPassingGate(agentId, versionId, reportId) {
  const itemJson = JSON.stringify([
    { code: "OWNER_PRESENT", status: "PASS", message: "Codex rollout smoke seeded pass gate." }
  ]);
  psql(`
insert into sa_production_gate_report(report_id, agent_id, version_id, status, result_json, checked_at)
values ('${sqlEscape(reportId)}', '${sqlEscape(agentId)}', '${sqlEscape(versionId)}', 'PASS', '${sqlEscape(itemJson)}', now());
`);
}

function verifyFailureRollout(rolloutId) {
  const rows = psql(`
select status, coalesce(failure_code, '')
from sa_agent_version_rollout
where rollout_id = '${sqlEscape(rolloutId)}';
`);
  if (rows[0] !== "FAILED|GATE_MISSING") {
    throw new Error(`expected FAILED|GATE_MISSING for ${rolloutId}, got ${rows.join("\\n")}`);
  }
}

function verifyPromotedRollout(rolloutId, gateReportId) {
  const rows = psql(`
select status, coalesce(failure_code, ''), coalesce(gate_report_id, '')
from sa_agent_version_rollout
where rollout_id = '${sqlEscape(rolloutId)}';
`);
  if (rows[0] !== `PROMOTED||${gateReportId}`) {
    throw new Error(`expected PROMOTED with gate ${gateReportId}, got ${rows.join("\\n")}`);
  }
  return rows[0];
}

function verifyAudit(rolloutId, eventType) {
  const rows = psql(`
select event_type, resource_type, resource_id
from sa_audit_event
where event_type = '${sqlEscape(eventType)}'
  and resource_type = 'AGENT_ROLLOUT'
  and resource_id = '${sqlEscape(rolloutId)}'
order by occurred_at desc
limit 1;
`);
  if (rows.length === 0) {
    throw new Error(`audit row ${eventType} for ${rolloutId} was not found`);
  }
  return rows[0];
}

async function loginUi(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
}

async function setInputByIndex(page, index, value) {
  const inputs = page.locator(".admin-page input");
  await inputs.nth(index).fill(value);
}

async function waitUntilEnabled(locator, label) {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    if (await locator.isEnabled()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become enabled`);
}

async function runPromoteOnPage(page, agentId, versionId, marker) {
  const promoteResponses = [];
  page.on("response", async (response) => {
    if (response.url().includes(`/rollouts/`) && response.url().includes(`/promote`)) {
      promoteResponses.push({
        status: response.status(),
        body: await response.text().catch(() => "")
      });
    }
  });

  await page.goto(`${baseUrl}/admin/agents/${encodeURIComponent(agentId)}/rollout`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector(".admin-page input", { timeout: 20000 });
  await setInputByIndex(page, 0, versionId);
  await setInputByIndex(page, 1, "tenant-default");
  await setInputByIndex(page, 2, "25");

  const createButton = page.getByRole("button", { name: "创建 Canary 发布" });
  await waitUntilEnabled(createButton, "create canary button");
  await createButton.click();
  await page.waitForFunction(() => document.body.innerText.includes("RUNNING"), null, { timeout: 20000 });

  const latestRows = psql(`
select rollout_id
from sa_agent_version_rollout
where agent_id = '${sqlEscape(agentId)}'
  and version_id = '${sqlEscape(versionId)}'
  and tenant_id = 'tenant-default'
order by updated_at desc, rollout_id desc
limit 1;
`);
  const rolloutId = latestRows[0];
  if (!rolloutId) {
    throw new Error("page-created rollout was not found in database");
  }

  const promoteButton = page.getByRole("button", { name: "全量发布" });
  await promoteButton.waitFor({ state: "visible", timeout: 15000 });
  await promoteButton.click();
  await page.locator("textarea").fill(`codex page promote ${marker}`);

  const responsePromise = page.waitForResponse(
    (response) => response.url().includes(`/rollouts/${rolloutId}/promote`),
    { timeout: 20000 }
  );
  await page.getByRole("button", { name: "确认全量发布" }).click();
  const response = await responsePromise;
  if (response.status() < 200 || response.status() >= 300) {
    const body = await response.text().catch(() => "");
    throw new Error(`page promote returned HTTP ${response.status()}: ${body.slice(0, 500)}`);
  }
  await page.waitForFunction(() => document.body.innerText.includes("PROMOTED"), null, { timeout: 20000 });

  const captured = promoteResponses.at(-1);
  if (captured && (captured.status < 200 || captured.status >= 300)) {
    throw new Error(`captured promote response failed: ${JSON.stringify(captured)}`);
  }
  return rolloutId;
}

const marker = `CODX_AGENT_ROLLOUT_${Date.now()}`;
const token = await loginApi();
const auth = { Authorization: `Bearer ${token}` };

const failureAgentId = `rollout-fail-${marker}`;
const failureVersionId = `version-fail-${marker}`;
const failureRollout = await createCanary(auth, failureAgentId, failureVersionId, 10, "codex");
const failed = await promote(auth, failureAgentId, failureRollout.rolloutId, `codex missing gate ${marker}`);
if (failed.status !== "FAILED" || failed.failureCode !== "GATE_MISSING") {
  throw new Error(`missing-gate promote did not fail as expected: ${JSON.stringify(failed)}`);
}
verifyFailureRollout(failureRollout.rolloutId);
const failureAudit = verifyAudit(failureRollout.rolloutId, "AGENT_ROLLOUT_FAILED");

const successAgentId = `rollout-pass-${marker}`;
const successVersionId = `version-pass-${marker}`;
const gateReportId = `gate_${Date.now()}`;
seedPassingGate(successAgentId, successVersionId, gateReportId);

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });

try {
  await loginUi(page);
  const promotedRolloutId = await runPromoteOnPage(page, successAgentId, successVersionId, marker);
  const screenshot = path.join(artifactDir, `agent-rollout-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });

  const promotedRow = verifyPromotedRollout(promotedRolloutId, gateReportId);
  const startAudit = verifyAudit(promotedRolloutId, "AGENT_ROLLOUT_STARTED");
  const promoteAudit = verifyAudit(promotedRolloutId, "AGENT_ROLLOUT_PROMOTED");

  console.log(JSON.stringify({
    ok: true,
    marker,
    failure: {
      agentId: failureAgentId,
      versionId: failureVersionId,
      rolloutId: failureRollout.rolloutId,
      audit: failureAudit
    },
    success: {
      agentId: successAgentId,
      versionId: successVersionId,
      rolloutId: promotedRolloutId,
      gateReportId,
      rolloutRow: promotedRow,
      startAudit,
      promoteAudit
    },
    screenshot
  }, null, 2));
} finally {
  await browser.close();
}
