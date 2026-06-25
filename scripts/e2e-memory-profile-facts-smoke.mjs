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

function sqlLiteral(value) {
  return String(value).replaceAll("'", "''");
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

function seedProfileFact(userId, marker) {
  const factId = BigInt(Date.now()) * 1000n + BigInt(Math.floor(Math.random() * 1000));
  const slotKey = `codex.profile_source.${Date.now()}`;
  const sourceIds = [`memory-snapshot-${factId}`, `conversation-message-${factId}`];
  const generationId = `${slotKey}:generation`;
  psql(`
insert into t_user_profile_fact
  (id, user_id, tenant_id, slot_key, value_text, value_json, confidence_level, source_type,
   source_ids, generation_id, status, version, valid_from, valid_until, last_referenced_at,
   access_count, create_time, update_time, deleted)
values
  (${factId}, ${userId}, 'default', '${sqlLiteral(slotKey)}', '${sqlLiteral(marker)}',
   jsonb_build_object('value', '${sqlLiteral(marker)}'), 0.923, 'explicit_user_memory',
   '["${sourceIds[0]}", "${sourceIds[1]}"]'::jsonb, '${sqlLiteral(generationId)}',
   'ACTIVE', 3, now(), null, now(), 7, now(), now(), 0);
`);
  return { factId: String(factId), slotKey, sourceIds, generationId };
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

async function verifyPage(page, marker, seeded) {
  await page.goto(`${baseUrl}/admin/memory-governance`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.locator('[role="tab"]').nth(7).click();
  await page.locator('[role="tabpanel"] button').nth(2).click();
  await page.waitForFunction(
    (text) => document.body.innerText.includes(text),
    marker,
    { timeout: 20000 }
  );
  const row = page.locator("tr").filter({ hasText: marker }).first();
  await row.locator("button").click();
  await page.waitForFunction(
    (sourceId) => document.body.innerText.includes(sourceId),
    seeded.sourceIds[0],
    { timeout: 10000 }
  );
}

const marker = `CODX_PROFILE_SOURCE_${Date.now()}`;
const login = await loginApi();
const auth = { Authorization: `Bearer ${login.token}` };
const seeded = seedProfileFact(login.userId, marker);

const facts = await api(`/memories/profile-facts?userId=${encodeURIComponent(login.userId)}&tenantId=default&limit=50`, {
  headers: auth
});
const fact = (Array.isArray(facts) ? facts : []).find((item) => item?.slotKey === seeded.slotKey);
if (!fact) {
  throw new Error(`seeded profile fact was not returned by API: ${JSON.stringify(facts)}`);
}
if (!Array.isArray(fact.sourceIds) || !seeded.sourceIds.every((id) => fact.sourceIds.includes(id))) {
  throw new Error(`API did not expose sourceIds for seeded profile fact: ${JSON.stringify(fact)}`);
}

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });

try {
  await loginBrowser(page, login);
  await verifyPage(page, marker, seeded);
  const screenshot = path.join(artifactDir, `memory-profile-facts-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  const dbRow = psql(`
select id, slot_key, value_text, confidence_level, source_type, source_ids, generation_id, version, access_count
from t_user_profile_fact
where id = ${seeded.factId}
  and deleted = 0
limit 1;
`)[0];
  console.log(JSON.stringify({
    ok: true,
    marker,
    userId: String(login.userId),
    factId: seeded.factId,
    slotKey: seeded.slotKey,
    sourceIds: seeded.sourceIds,
    apiSourceIds: fact.sourceIds,
    generationId: fact.generationId,
    confidenceLevel: fact.confidenceLevel,
    version: fact.version,
    accessCount: fact.accessCount,
    dbRow,
    screenshot
  }, null, 2));
} finally {
  await browser.close();
}
