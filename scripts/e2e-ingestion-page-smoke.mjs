import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

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

await fs.mkdir(artifactDir, { recursive: true });

async function api(pathname, options = {}) {
  const response = await fetch(`${baseUrl}/api${pathname}`, {
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

async function seedIngestionData(token, marker) {
  const auth = { Authorization: `Bearer ${token}` };
  const pipeline = await api("/ingestion/pipelines", {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      name: `${marker}-page`,
      description: "Codex ingestion page smoke pipeline",
      nodes: [
        { nodeId: "1", nodeType: "parser", nextNodeId: "2", settings: {} },
        { nodeId: "2", nodeType: "chunker", settings: { chunkSize: 48, overlapSize: 0, embed: false } }
      ]
    })
  });
  const task = await api("/ingestion/tasks", {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      pipelineId: String(pipeline.id),
      source: {
        type: "text",
        location: `Codex ingestion page smoke source ${marker}`,
        fileName: "codex-ingestion-page.txt"
      },
      metadata: { marker }
    })
  });
  if (task.status !== "completed") {
    throw new Error(`seed ingestion task did not complete: ${JSON.stringify(task)}`);
  }
  return { pipeline, task };
}

async function loginUi(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByRole("textbox", { name: "用户名" }).fill(username);
  await page.getByRole("textbox", { name: "密码" }).fill(password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 20000 }),
    page.getByRole("button", { name: "进入系统" }).click()
  ]);
}

async function pageText(page) {
  return page.locator("body").innerText({ timeout: 10000 });
}

async function verifyIngestionPage(page, marker, taskId) {
  await page.goto(`${baseUrl}/admin/ingestion?tab=pipelines`, { waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: "数据通道" }).waitFor({ state: "visible", timeout: 15000 });
  let text = await pageText(page);
  if (!text.includes(`${marker}-page`)) {
    throw new Error("pipelines tab did not show seeded pipeline");
  }
  if (!text.includes("通道流水线")) {
    throw new Error("pipelines tab did not render pipeline section");
  }

  await page.goto(`${baseUrl}/admin/ingestion?tab=tasks`, { waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: "任务", exact: true }).waitFor({ state: "visible", timeout: 15000 });
  await page.waitForFunction(
    (expectedTaskId) => document.body.innerText.includes(expectedTaskId),
    taskId,
    { timeout: 15000 }
  );
  text = await pageText(page);
  if (!text.includes(taskId)) {
    throw new Error("tasks tab did not show seeded task id");
  }
  if (!text.includes("completed") && !text.includes("完成")) {
    throw new Error("tasks tab did not show completed task status");
  }
}

const marker = `CODX_INGESTION_PAGE_${Date.now()}`;
const token = await loginApi();
const seeded = await seedIngestionData(token, marker);

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 820 } });

const findings = {
  marker,
  pipelineId: String(seeded.pipeline.id),
  taskId: String(seeded.task.taskId),
  screenshot: null
};

try {
  await loginUi(page);
  await verifyIngestionPage(page, marker, findings.taskId);
  findings.screenshot = path.join(artifactDir, `ingestion-page-${marker}.png`);
  await page.screenshot({ path: findings.screenshot, fullPage: true });
  console.log(JSON.stringify({
    ok: true,
    marker: findings.marker,
    pipelineId: findings.pipelineId,
    taskId: findings.taskId,
    screenshot: findings.screenshot
  }, null, 2));
} finally {
  await browser.close();
}
