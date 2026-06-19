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

const baseUrl = readArg("--base-url", process.env.E2E_BASE_URL || "http://localhost").replace(/\/$/, "");
const username = readArg("--username", process.env.E2E_USERNAME || "admin");
const password = readArg("--password", process.env.E2E_PASSWORD || "admin123");
const artifactDir = path.resolve(readArg("--artifact-dir", process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")));
const headless = !hasFlag("--headed");

await fs.mkdir(artifactDir, { recursive: true });

const routes = [
  "/",
  "/chat",
  "/workspace",
  "/workspace/tasks",
  "/workspace/examples/github-mermaid",
  "/memories",
  "/marketplace",
  "/admin/dashboard",
  "/admin/knowledge",
  "/admin/traces",
  "/admin/readiness",
  "/admin/model-config",
  "/admin/sample-questions",
  "/admin/mappings",
  "/admin/task-templates"
];

const findings = {
  console: [],
  pageErrors: [],
  failedRequests: [],
  badResponses: [],
  emptyPages: [],
  checkedRoutes: [],
  login: null,
  readiness: null,
  workspaceTask: null
};

function pushFinding(bucket, item) {
  bucket.push({ ...item, time: new Date().toISOString() });
}

function isIgnoredResponse(response) {
  const url = response.url();
  return url.includes("/favicon.ico") || url.startsWith("data:");
}

async function visibleTextLength(page) {
  return page.evaluate(() => {
    const root = document.querySelector("#root") || document.body;
    return (root.textContent || "").replace(/\s+/g, " ").trim().length;
  });
}

async function unwrapJsonResponse(response) {
  const json = await response.json().catch(() => null);
  if (json && typeof json === "object" && "data" in json) {
    return json.data;
  }
  return json;
}

async function login(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  findings.login = { currentUrl: page.url() };
  await page.screenshot({ path: path.join(artifactDir, "page-e2e-after-login.png"), fullPage: true });
}

async function assertRoute(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.waitForTimeout(350);
  const textLength = await visibleTextLength(page);
  const currentUrl = page.url();
  findings.checkedRoutes.push({ route, currentUrl, textLength });
  if (textLength < 20) {
    findings.emptyPages.push({ route, currentUrl, textLength });
  }
}

async function assertReadiness(page) {
  await page.goto(`${baseUrl}/admin/readiness`, { waitUntil: "domcontentloaded" });
  const response = await page.waitForResponse(
    (r) => r.url().includes("/api/readiness/summary") && r.status() === 200,
    { timeout: 20000 }
  );
  const summary = await unwrapJsonResponse(response);
  if (!summary || !Array.isArray(summary.checks) || summary.checks.length === 0) {
    throw new Error("readiness summary did not include checks");
  }
  findings.readiness = {
    mode: summary.mode,
    overall: summary.overall,
    checks: summary.checks.length
  };
}

async function assertWorkspaceQuickChatTask(page) {
  const question = `E2E workspace quick chat ${Date.now()}`;
  const taskResponses = [];
  const chatResponses = [];

  page.on("response", (response) => {
    if (response.url().includes("/api/tasks") && response.request().method() === "POST") {
      taskResponses.push({
        url: response.url(),
        status: response.status(),
        contentType: response.headers()["content-type"] || ""
      });
    }
    if (response.url().includes("/api/rag/v3/chat")) {
      chatResponses.push({
        url: response.url(),
        status: response.status(),
        contentType: response.headers()["content-type"] || ""
      });
    }
  });

  await page.goto(`${baseUrl}/workspace`, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  const input = page.locator('form input[type="text"]').first();
  await input.waitFor({ state: "visible", timeout: 15000 });
  await input.fill(question);

  const taskResponsePromise = page.waitForResponse(
    (response) => response.url().includes("/api/tasks") && response.request().method() === "POST",
    { timeout: 20000 }
  );
  await page.locator('button[aria-label="开始任务"]').click();
  const taskResponse = await taskResponsePromise;
  if (taskResponse.status() !== 200) {
    throw new Error(`POST /api/tasks returned ${taskResponse.status()}`);
  }
  const task = await unwrapJsonResponse(taskResponse);
  if (!task?.taskId) {
    throw new Error("POST /api/tasks did not return a taskId");
  }

  await page.waitForURL(/\/(chat|workspace\/tasks)\//, { timeout: 20000 });
  if (page.url().includes("/chat/")) {
    await page.waitForResponse(
      (response) => response.url().includes("/api/rag/v3/chat") && response.status() === 200,
      { timeout: 30000 }
    );
  }
  await page.waitForTimeout(3000);
  const bodyText = await page.locator("body").innerText();
  if (!bodyText.includes(question)) {
    throw new Error("workspace task question did not render on the destination page");
  }

  const badTaskResponse = taskResponses.find((response) => response.status !== 200);
  if (badTaskResponse) {
    throw new Error(`unexpected task response: ${JSON.stringify(badTaskResponse)}`);
  }
  const badChatResponse = chatResponses.find((response) => response.status !== 200 || !response.contentType.includes("text/event-stream"));
  if (badChatResponse) {
    throw new Error(`unexpected chat SSE response: ${JSON.stringify(badChatResponse)}`);
  }

  findings.workspaceTask = {
    taskId: task.taskId,
    conversationId: task.conversationId || null,
    finalUrl: page.url(),
    hasQuestion: bodyText.includes(question),
    taskResponses,
    chatResponses
  };
  await page.screenshot({ path: path.join(artifactDir, "page-e2e-workspace-task.png"), fullPage: true });
}

const browser = await chromium.launch({ headless });
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
  ignoreHTTPSErrors: true
});
const page = await context.newPage();

page.on("console", (message) => {
  if (["error", "warning"].includes(message.type())) {
    pushFinding(findings.console, {
      type: message.type(),
      text: message.text(),
      location: message.location()
    });
  }
});
page.on("pageerror", (error) => {
  pushFinding(findings.pageErrors, { message: error.message, stack: error.stack });
});
page.on("requestfailed", (request) => {
  pushFinding(findings.failedRequests, {
    method: request.method(),
    url: request.url(),
    failure: request.failure()?.errorText || ""
  });
});
page.on("response", (response) => {
  if (!isIgnoredResponse(response) && response.status() >= 400) {
    pushFinding(findings.badResponses, {
      status: response.status(),
      url: response.url()
    });
  }
});

let failed = false;
try {
  await login(page);
  for (const route of routes) {
    await assertRoute(page, route);
  }
  await assertReadiness(page);
  await assertWorkspaceQuickChatTask(page);
} catch (error) {
  failed = true;
  findings.error = {
    message: error.message,
    stack: error.stack
  };
  await page.screenshot({ path: path.join(artifactDir, "page-e2e-failure.png"), fullPage: true }).catch(() => null);
} finally {
  await fs.writeFile(path.join(artifactDir, "page-e2e-results.json"), JSON.stringify(findings, null, 2), "utf8");
  await browser.close();
}

const hardFailures = [
  ...findings.console,
  ...findings.pageErrors,
  ...findings.failedRequests,
  ...findings.badResponses,
  ...findings.emptyPages
];

if (failed || hardFailures.length > 0) {
  console.error(JSON.stringify(findings, null, 2));
  process.exit(1);
}

console.log(JSON.stringify({
  checkedRoutes: findings.checkedRoutes.length,
  login: findings.login,
  readiness: findings.readiness,
  workspaceTask: findings.workspaceTask,
  artifacts: artifactDir
}, null, 2));
