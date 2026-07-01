import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

function readArg(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
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
    return createRequire(path.join(runtimeDir, "package.json"))("playwright");
  }
}

const { chromium } = loadPlaywright();

const baseUrl = readArg("--base-url", process.env.E2E_BASE_URL || "http://127.0.0.1").replace(/\/$/, "");
const username = readArg("--username", process.env.E2E_USERNAME || "admin");
const password = readArg("--password", process.env.E2E_PASSWORD || "admin123");
const postgresContainer = readArg("--postgres-container", process.env.E2E_POSTGRES_CONTAINER || "seahorse-postgres");
const postgresUser = readArg("--postgres-user", process.env.E2E_POSTGRES_USER || "seahorse");
const postgresDatabase = readArg("--postgres-database", process.env.E2E_POSTGRES_DATABASE || "seahorse");
const artifactDir = path.resolve(readArg("--artifact-dir", process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")));
const headless = !hasFlag("--headed");

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
    return { ok: false, status: response.status, payload };
  }
  if (payload && payload.code !== "0") {
    return { ok: false, status: response.status, payload };
  }
  return { ok: true, status: response.status, data: payload?.data };
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
  ], { input: sql, encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error(`psql failed: ${result.stderr || result.stdout}`);
  }
  return result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

function sqlLiteral(value) {
  return String(value).replaceAll("'", "''");
}

function requireOk(result, label) {
  if (!result.ok) {
    throw new Error(`${label} failed: HTTP ${result.status} ${JSON.stringify(result.payload)}`);
  }
  return result.data;
}

function startOpenApiTarget(marker) {
  const requests = [];
  const server = http.createServer((request, response) => {
    const requestUrl = new URL(request.url || "/", "http://openapi-smoke-target");
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      requests.push({
        method: request.method,
        path: requestUrl.pathname,
        query: Object.fromEntries(requestUrl.searchParams.entries()),
        headers: request.headers,
        body: Buffer.concat(chunks).toString("utf8")
      });
      response.writeHead(200, { "content-type": "application/json" });
      response.end(JSON.stringify({
        marker,
        path: requestUrl.pathname,
        status: requestUrl.searchParams.get("status"),
        token: `${marker}-raw-token`,
        nested: { secret: `${marker}-raw-secret` }
      }));
    });
  });
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "0.0.0.0", () => {
      const address = server.address();
      resolve({ server, requests, port: address.port });
    });
  });
}

async function closeServer(server) {
  await new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

const marker = `CODX_OPENAPI_${Date.now()}`;
const openApiTarget = await startOpenApiTarget(marker);
const serverUrlOverride = readArg("--openapi-server-url", process.env.E2E_OPENAPI_SERVER_URL || "");
const serverUrl = (serverUrlOverride || `http://host.docker.internal:${openApiTarget.port}/${marker}`).replace(/\/$/, "");
const login = requireOk(await api("/auth/login", {
  method: "POST",
  body: JSON.stringify({ username, password })
}), "login");
const auth = { Authorization: `Bearer ${login.token}` };

const spec = {
  openapi: "3.0.3",
  info: { title: `Codex OpenAPI Smoke ${marker}`, version: "1.0.0" },
  servers: [{ url: serverUrl }],
  paths: {
    "/pets": {
      get: {
        operationId: `listPets_${marker}`,
        summary: "List pets",
        parameters: [{ name: "status", in: "query", required: false, schema: { type: "string" } }],
        responses: { 200: { description: "ok" } }
      }
    },
    "/pets/{petId}": {
      delete: {
        operationId: `deletePet_${marker}`,
        summary: "Delete pet",
        parameters: [{ name: "petId", in: "path", required: true, schema: { type: "string" } }],
        responses: { 204: { description: "deleted" } }
      }
    }
  }
};

const connector = requireOk(await api("/connectors/openapi", {
  method: "POST",
  headers: auth,
  body: JSON.stringify({
    tenantId: "default",
    name: `Codex OpenAPI Smoke ${marker}`,
    specJson: JSON.stringify(spec),
    importedBy: "codex-e2e"
  })
}), "import OpenAPI connector");

const connectorId = connector.connectorId || connector.id;
if (!connectorId) {
  throw new Error(`import response did not include connectorId: ${JSON.stringify(connector)}`);
}

const operations = requireOk(await api(`/connectors/${encodeURIComponent(connectorId)}/operations`, {
  headers: auth
}), "list connector operations");
if (!Array.isArray(operations) || operations.length !== 2) {
  throw new Error(`expected 2 operations, got ${JSON.stringify(operations)}`);
}
const getOperation = operations.find((operation) => operation.method === "GET");
const deleteOperation = operations.find((operation) => operation.method === "DELETE");
if (!getOperation?.operationId || !deleteOperation?.operationId) {
  throw new Error(`missing expected GET/DELETE operations: ${JSON.stringify(operations)}`);
}
if (getOperation.riskLevel !== "LOW" || deleteOperation.riskLevel !== "HIGH") {
  throw new Error(`unexpected operation risks: ${JSON.stringify(operations)}`);
}

const enabledGet = requireOk(await api(
  `/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(getOperation.operationId)}/enable`,
  { method: "POST", headers: auth }
), "enable low-risk GET operation");

const highRiskResult = await api(
  `/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(deleteOperation.operationId)}/enable`,
  { method: "POST", headers: auth }
);
if (highRiskResult.ok || highRiskResult.status !== 409) {
  throw new Error(`high-risk DELETE enable should return 409, got ${JSON.stringify(highRiskResult)}`);
}

const tools = requireOk(await api("/tools?current=1&size=100&provider=OPENAPI", {
  headers: auth
}), "query OpenAPI tool catalog");
const toolRecords = Array.isArray(tools?.records) ? tools.records : [];
const connectorTool = toolRecords.find((tool) =>
  tool.provider === "OPENAPI" &&
  tool.enabled === true &&
  String(tool.ownerTeam || "") === `connector:${connectorId}`
);
if (!connectorTool) {
  throw new Error(`enabled OpenAPI tool was not returned: ${JSON.stringify(tools)}`);
}
const openApiToolId = connectorTool.toolId || enabledGet.toolId || enabledGet.toolCatalogId || enabledGet.operationId;
if (!openApiToolId) {
  throw new Error(`enabled OpenAPI tool id was not available: ${JSON.stringify({ connectorTool, enabledGet })}`);
}

const preflight = requireOk(await api(`/tools/${encodeURIComponent(openApiToolId)}/preflight`, {
  method: "POST",
  headers: auth,
  body: JSON.stringify({
    runId: `openapi-smoke-run-${marker}`,
    stepId: `openapi-smoke-step-${marker}`,
    toolCallId: `openapi-smoke-call-${marker}`,
    tenantId: "default",
    userId: String(login.userId || "1"),
    arguments: { status: "available" },
    idempotencyKey: `openapi-smoke-run-${marker}:openapi-smoke-call-${marker}`,
    allowedToolIds: [openApiToolId]
  })
}), "preflight enabled OpenAPI tool through Tool Gateway");
if (preflight.effect !== "ALLOW") {
  throw new Error(`enabled OpenAPI tool preflight should ALLOW, got ${JSON.stringify(preflight)}`);
}

const invokeRunId = `openapi-smoke-invoke-run-${marker}`;
const invokeToolCallId = `openapi-smoke-invoke-call-${marker}`;
const invocation = requireOk(await api(`/tools/${encodeURIComponent(openApiToolId)}/invoke`, {
  method: "POST",
  headers: auth,
  body: JSON.stringify({
    runId: invokeRunId,
    stepId: `openapi-smoke-invoke-step-${marker}`,
    toolCallId: invokeToolCallId,
    tenantId: "default",
    userId: String(login.userId || "1"),
    arguments: { status: "available" },
    idempotencyKey: `${invokeRunId}:${invokeToolCallId}`,
    allowedToolIds: [openApiToolId]
  })
}), "invoke enabled OpenAPI tool through Tool Gateway");
if (invocation.success !== true) {
  throw new Error(`enabled OpenAPI tool invocation should succeed, got ${JSON.stringify(invocation)}`);
}
if (String(invocation.content || "").includes(`${marker}-raw-token`) ||
    String(invocation.content || "").includes(`${marker}-raw-secret`)) {
  throw new Error(`OpenAPI invocation content leaked sensitive response fields: ${invocation.content}`);
}
const invocationPayload = JSON.parse(invocation.content || "{}");
if (invocationPayload.statusCode !== 200 ||
    invocationPayload.body?.marker !== marker ||
    invocationPayload.body?.status !== "available" ||
    invocationPayload.body?.token !== "[REDACTED]" ||
    invocationPayload.body?.nested?.secret !== "[REDACTED]") {
  throw new Error(`OpenAPI invocation returned unexpected payload: ${invocation.content}`);
}
const observedTargetRequest = openApiTarget.requests.find((request) =>
  request.method === "GET" &&
  request.path === `/${marker}/pets` &&
  request.query?.status === "available"
);
if (!observedTargetRequest) {
  throw new Error(`OpenAPI target did not observe expected GET request: ${JSON.stringify(openApiTarget.requests)}`);
}

const audit = requireOk(await api(
  `/tool-invocations?current=1&size=20&runId=${encodeURIComponent(invokeRunId)}&toolId=${encodeURIComponent(openApiToolId)}`,
  { headers: auth }
), "query OpenAPI invocation audit");
const auditRecords = Array.isArray(audit?.records) ? audit.records : [];
const succeededAudit = auditRecords.find((record) =>
  record.status === "SUCCEEDED" &&
  record.toolId === openApiToolId &&
  record.runId === invokeRunId
);
if (!succeededAudit) {
  throw new Error(`OpenAPI invocation did not create SUCCEEDED audit: ${JSON.stringify(audit)}`);
}

const dbRow = psql(`
select c.connector_id,
       c.name,
       c.base_url,
       c.status,
       count(o.operation_id),
       string_agg(o.method || ':' || o.risk_level || ':' || o.status, ', ' order by o.method)
from sa_connector c
join sa_connector_operation o on o.connector_id = c.connector_id
where c.connector_id = '${sqlLiteral(connectorId)}'
group by c.connector_id, c.name, c.base_url, c.status;
`)[0] || "";
if (!dbRow.includes(`${connectorId}|`) || !dbRow.includes(serverUrl) || !dbRow.includes("GET:LOW:ENABLED") || !dbRow.includes("DELETE:HIGH:DISABLED")) {
  throw new Error(`database row did not confirm expected operation state: ${dbRow}`);
}

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
let screenshotFile = "";
try {
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
  await page.goto(`${baseUrl}/admin/integrations/connectors`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction((text) => document.body.innerText.includes(text), marker, { timeout: 20000 });
  await page.goto(`${baseUrl}/admin/integrations/connectors/${encodeURIComponent(connectorId)}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(
    ({ getName, deleteName }) => document.body.innerText.includes(getName) && document.body.innerText.includes(deleteName),
    { getName: getOperation.originalOperationId || getOperation.operationName || getOperation.operationId, deleteName: deleteOperation.originalOperationId || deleteOperation.operationName || deleteOperation.operationId },
    { timeout: 20000 }
  );
  screenshotFile = path.join(artifactDir, `openapi-connector-${marker}.png`);
  await page.screenshot({ path: screenshotFile, fullPage: true });
} finally {
  await browser.close();
}
await closeServer(openApiTarget.server);

console.log(JSON.stringify({
  ok: true,
  marker,
  connectorId,
  operations: operations.map((operation) => `${operation.method}:${operation.riskLevel}:${operation.status}`),
  enabledGet: openApiToolId,
  preflightEffect: preflight.effect,
  invocationSuccess: invocation.success,
  invocationStatusCode: invocationPayload.statusCode,
  auditStatus: succeededAudit.status,
  highRiskStatus: highRiskResult.status,
  toolCount: toolRecords.length,
  dbRow,
  screenshot: screenshotFile
}, null, 2));
