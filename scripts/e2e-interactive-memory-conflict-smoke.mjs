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
const artifactDir = path.resolve(readArg(
  "--artifact-dir",
  process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")
));
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

function cleanupPreviousSmokeRows(userId) {
  psql(`
update t_memory_conflict_log
set deleted = 1,
    update_time = now()
where user_id = ${userId}
  and id like 'codxic%'
  and deleted = 0;

update t_short_term_memory
set deleted = 1,
    update_time = now()
where user_id = ${userId}
  and id like 'codxic%'
  and deleted = 0;
`);
}

function seedInteractiveConflict(userId, marker) {
  const suffix = Date.now();
  const memoryIdA = `codxicA${suffix}`;
  const memoryIdB = `codxicB${suffix}`;
  const conflictId = `codxic-conflict-${suffix}`;
  const semanticKey = `profile:interactive-conflict:${marker}`;
  const contentA = `Interactive conflict smoke memory A ${marker} says the preferred workspace is quiet mode`;
  const contentB = `Interactive conflict smoke memory B ${marker} says the preferred workspace is collaboration mode`;
  const metadataA = {
    marker,
    semanticKey,
    userId: String(userId),
    tenantId: "default",
    importanceScore: 0.96,
    confidenceLevel: 0.93
  };
  const metadataB = {
    marker,
    semanticKey,
    userId: String(userId),
    tenantId: "default",
    importanceScore: 0.95,
    confidenceLevel: 0.92
  };

  psql(`
insert into t_short_term_memory
  (id, user_id, tenant_id, conversation_id, memory_type, content, metadata_json,
   source_message_ids, importance_score, status, create_time, update_time, deleted)
values
  ('${memoryIdA}', ${userId}, 'default', null, 'PROFILE',
   '${sqlLiteral(contentA)}',
   $json$${JSON.stringify(metadataA)}$json$::jsonb, '[]'::jsonb, 0.960, 'ACTIVE', now(), now(), 0),
  ('${memoryIdB}', ${userId}, 'default', null, 'PROFILE',
   '${sqlLiteral(contentB)}',
   $json$${JSON.stringify(metadataB)}$json$::jsonb, '[]'::jsonb, 0.950, 'ACTIVE', now(), now(), 0);

insert into t_memory_conflict_log
  (id, user_id, memory_id_1, memory_id_2, conflict_type, severity, resolution_status,
   resolution_action, resolved_by, resolved_at, create_time, update_time, deleted)
values
  ('${conflictId}', ${userId}, '${memoryIdA}', '${memoryIdB}', 'CONTRADICTION', 'HIGH', 'PENDING',
   null, null, null, now(), now(), 0);
`);

  return { memoryIdA, memoryIdB, conflictId, contentA, contentB };
}

async function verifyPendingConflict(auth, userId, conflictId) {
  const pending = await api(`/memories/conflicts?userId=${encodeURIComponent(userId)}&status=PENDING&limit=10`, {
    headers: auth
  });
  const conflict = (Array.isArray(pending) ? pending : []).find((item) => item?.id === conflictId);
  if (!conflict) {
    throw new Error(`seeded pending conflict was not returned by API: ${JSON.stringify(pending)}`);
  }
  return conflict;
}

async function loginBrowser(page, login) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.evaluate((data) => {
    window.localStorage.setItem("seahorse_agent_token", data.token);
    window.localStorage.setItem("seahorse_agent_user", JSON.stringify({
      userId: data.userId,
      username: data.username || "admin",
      role: data.role || "admin",
      tenantId: data.tenantId || "default",
      token: data.token
    }));
  }, login);
}

async function sendChatAndResolve(page, seeded) {
  await page.goto(`${baseUrl}/chat`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);

  const input = page.locator("textarea").last();
  await input.waitFor({ state: "visible", timeout: 20000 });
  await input.fill("Please continue this conversation.");

  const chatResponsePromise = page.waitForResponse(
    (response) => response.url().includes("/api/rag/v3/chat") && response.status() === 200,
    { timeout: 30000 }
  );
  await input.press("Enter");
  const chatResponse = await chatResponsePromise;
  const contentType = chatResponse.headers()["content-type"] || "";
  if (!contentType.includes("text/event-stream")) {
    throw new Error(`chat response was not SSE: ${contentType}`);
  }

  await page.waitForFunction(
    ({ contentA, contentB }) =>
      document.body.innerText.includes(contentA) && document.body.innerText.includes(contentB),
    { contentA: seeded.contentA, contentB: seeded.contentB },
    { timeout: 60000 }
  );

  const card = page
    .locator(".rounded-lg.border.p-3.text-sm")
    .filter({ hasText: seeded.contentA })
    .filter({ hasText: seeded.contentB })
    .first();
  await card.waitFor({ state: "visible", timeout: 10000 });

  verifySeededMemoriesActive(seeded);

  await card.locator("button").nth(0).click();
  const resolveResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes("/api/memories/conflicts/interactive-resolve") &&
      response.request().method() === "POST",
    { timeout: 20000 }
  );
  await card.locator("button").last().click();
  const resolveResponse = await resolveResponsePromise;
  if (resolveResponse.status() < 200 || resolveResponse.status() >= 300) {
    const body = await resolveResponse.text().catch(() => "");
    throw new Error(`interactive resolve API returned HTTP ${resolveResponse.status()}: ${body.slice(0, 500)}`);
  }

  await page.waitForFunction(
    (memoryId) => !document.body.innerText.includes(memoryId),
    seeded.memoryIdA,
    { timeout: 10000 }
  ).catch(() => null);
}

function expectedInteractiveOperator(userId) {
  const prefix = "interactive:";
  const fallback = "system";
  let operator = String(userId || fallback).trim() || fallback;
  const maxOperatorLength = 32 - prefix.length;
  if (operator.length > maxOperatorLength) {
    operator = operator.slice(operator.length - maxOperatorLength);
  }
  return `${prefix}${operator}`;
}

function verifyResolvedInDb(conflictId, userId) {
  const rows = psql(`
select id, resolution_status, resolution_action, resolved_by
from t_memory_conflict_log
where id = '${sqlLiteral(conflictId)}'
  and deleted = 0
limit 1;
`);
  const row = rows[0] || "";
  const expected = `${conflictId}|RESOLVED|keep_a|${expectedInteractiveOperator(userId)}`;
  if (row !== expected) {
    throw new Error(`conflict was not resolved as expected: got "${row}", expected "${expected}"`);
  }
  return row;
}

function verifyMemoryStateInDb(seeded) {
  const rows = psql(`
select id, deleted
from t_short_term_memory
where id in ('${sqlLiteral(seeded.memoryIdA)}', '${sqlLiteral(seeded.memoryIdB)}')
order by id;
`);
  const expected = [
    `${seeded.memoryIdA}|0`,
    `${seeded.memoryIdB}|1`
  ].sort().join("\n");
  const actual = rows.sort().join("\n");
  if (actual !== expected) {
    throw new Error(`underlying memory state mismatch:\nactual:\n${actual}\nexpected:\n${expected}`);
  }
  return actual;
}

function verifySeededMemoriesActive(seeded) {
  const rows = psql(`
select id, deleted
from t_short_term_memory
where id in ('${sqlLiteral(seeded.memoryIdA)}', '${sqlLiteral(seeded.memoryIdB)}')
order by id;
`);
  const expected = [
    `${seeded.memoryIdA}|0`,
    `${seeded.memoryIdB}|0`
  ].sort().join("\n");
  const actual = rows.sort().join("\n");
  if (actual !== expected) {
    throw new Error(`seeded memories became inactive before interactive resolve:\nactual:\n${actual}\nexpected:\n${expected}`);
  }
  return actual;
}

function verifyObservabilityInDb(conflictId, userId) {
  const operator = expectedInteractiveOperator(userId);
  const traceRows = psql(`
select status,
       details_json ->> 'source',
       details_json ->> 'operator',
       details_json ->> 'action'
from t_memory_trace_event
where component = 'memory-conflict'
  and event_type = 'interactive-resolve'
  and subject_id = '${sqlLiteral(conflictId)}'
order by occurred_at desc, create_time desc
limit 1;
`);
  const expectedTrace = `SUCCESS|chat-ui|${operator}|keep_a`;
  if ((traceRows[0] || "") !== expectedTrace) {
    throw new Error(`memory conflict trace mismatch: got "${traceRows[0] || ""}", expected "${expectedTrace}"`);
  }

  const auditRows = psql(`
select event_type,
       actor_id,
       resource_id,
       redacted_payload::jsonb ->> 'source',
       redacted_payload::jsonb ->> 'action'
from sa_audit_event
where event_type = 'MEMORY_CONFLICT_RESOLVED'
  and resource_id = '${sqlLiteral(conflictId)}'
order by occurred_at desc
limit 1;
`);
  const expectedAudit = `MEMORY_CONFLICT_RESOLVED|${operator}|${conflictId}|chat-ui|keep_a`;
  if ((auditRows[0] || "") !== expectedAudit) {
    throw new Error(`memory conflict audit mismatch: got "${auditRows[0] || ""}", expected "${expectedAudit}"`);
  }
  return { traceRow: traceRows[0], auditRow: auditRows[0] };
}

const marker = `CODX_INTERACTIVE_MEMORY_CONFLICT_${Date.now()}`;
const login = await loginApi();
const auth = { Authorization: `Bearer ${login.token}` };
cleanupPreviousSmokeRows(login.userId);
const seeded = seedInteractiveConflict(login.userId, marker);
const pendingConflict = await verifyPendingConflict(auth, login.userId, seeded.conflictId);

const browser = await chromium.launch({ headless });
const context = await browser.newContext({
  viewport: { width: 1366, height: 860 },
  ignoreHTTPSErrors: true
});
const page = await context.newPage();

try {
  await loginBrowser(page, login);
  await sendChatAndResolve(page, seeded);
  const screenshot = path.join(artifactDir, `interactive-memory-conflict-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  const resolvedRow = verifyResolvedInDb(seeded.conflictId, login.userId);
  const memoryState = verifyMemoryStateInDb(seeded);
  const observability = verifyObservabilityInDb(seeded.conflictId, login.userId);
  console.log(JSON.stringify({
    ok: true,
    marker,
    userId: String(login.userId),
    memoryIdA: seeded.memoryIdA,
    memoryIdB: seeded.memoryIdB,
    contentA: seeded.contentA,
    contentB: seeded.contentB,
    conflictId: seeded.conflictId,
    conflictStatusBeforeResolve: pendingConflict.resolutionStatus,
    resolvedRow,
    memoryState,
    observability,
    screenshot
  }, null, 2));
} finally {
  await browser.close();
}
