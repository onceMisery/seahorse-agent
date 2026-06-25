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
const adminUsername = readArg("--admin-username", process.env.E2E_ADMIN_USERNAME || "admin");
const adminPassword = readArg("--admin-password", process.env.E2E_ADMIN_PASSWORD || "admin123");
const userUsername = readArg("--user-username", process.env.E2E_USER_USERNAME || "demo_user_001");
const userPassword = readArg("--user-password", process.env.E2E_USER_PASSWORD || "demo123");
const artifactDir = path.resolve(readArg("--artifact-dir", process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")));
const headless = !hasFlag("--headed");

await fs.mkdir(artifactDir, { recursive: true });

const findings = {
  scenarios: [],
  console: [],
  pageErrors: [],
  failedRequests: []
};

function pushFinding(bucket, item) {
  bucket.push({ ...item, time: new Date().toISOString() });
}

async function unwrapJsonResponse(response) {
  const json = await response.json().catch(() => null);
  if (json && typeof json === "object" && "data" in json) {
    return json.data;
  }
  return json;
}

async function visibleText(page) {
  return page.locator("body").innerText({ timeout: 5000 });
}

async function assertBodyIncludes(page, expected, name) {
  await page.waitForFunction(
    (text) => document.body.innerText.includes(text),
    expected,
    { timeout: 10000 }
  ).catch(async () => {
    const text = await visibleText(page).catch(() => "");
    throw new Error(`${name} did not show '${expected}'. Visible text: ${text.slice(0, 1000)}`);
  });
}

async function screenshot(page, name) {
  const file = path.join(artifactDir, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function waitForObservedResponse(responses, predicate, timeoutMs, label) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const found = responses.find(predicate);
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  const observed = responses.map((item) => ({ url: item.url, status: item.status }));
  throw new Error(`${label} was not observed. Observed tool responses: ${JSON.stringify(observed)}`);
}

async function login(page, username, password) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
}

async function newPage(contextName, browser) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    ignoreHTTPSErrors: true
  });
  const page = await context.newPage();
  const toolResponses = [];

  page.on("console", (message) => {
    if (["error", "warning"].includes(message.type())) {
      pushFinding(findings.console, {
        context: contextName,
        type: message.type(),
        text: message.text(),
        location: message.location()
      });
    }
  });
  page.on("pageerror", (error) => {
    pushFinding(findings.pageErrors, {
      context: contextName,
      message: error.message,
      stack: error.stack
    });
  });
  page.on("requestfailed", (request) => {
    pushFinding(findings.failedRequests, {
      context: contextName,
      method: request.method(),
      url: request.url(),
      failure: request.failure()?.errorText || ""
    });
  });
  page.on("response", async (response) => {
    const url = response.url();
    if (!url.includes("/api/tools")) return;
    toolResponses.push({
      url,
      status: response.status(),
      body: await response.json().catch(() => null)
    });
  });

  return { context, page, toolResponses };
}

async function runScenario(name, action) {
  const startedAt = new Date().toISOString();
  try {
    const result = await action();
    findings.scenarios.push({ name, status: "PASS", startedAt, finishedAt: new Date().toISOString(), ...result });
  } catch (error) {
    findings.scenarios.push({
      name,
      status: "FAIL",
      startedAt,
      finishedAt: new Date().toISOString(),
      error: {
        message: error.message,
        stack: error.stack
      }
    });
    throw error;
  }
}

const browser = await chromium.launch({ headless });

try {
  await runScenario("admin data state shows tool catalog rows", async () => {
    const { context, page } = await newPage("admin-data", browser);
    try {
      await login(page, adminUsername, adminPassword);
      const toolsResponsePromise = page.waitForResponse(
        (response) => response.url().includes("/api/tools?") && response.status() === 200,
        { timeout: 20000 }
      );
      await page.goto(`${baseUrl}/admin/tools`, { waitUntil: "domcontentloaded" });
      const toolsResponse = await toolsResponsePromise;
      const pageData = await unwrapJsonResponse(toolsResponse);
      const records = Array.isArray(pageData?.records) ? pageData.records : [];
      if (records.length === 0) {
        throw new Error("admin /api/tools returned no records for data-state verification");
      }
      const firstName = records.find((item) => item?.name)?.name || records[0].toolId;
      await assertBodyIncludes(page, firstName, "admin data state");
      const image = await screenshot(page, "governance-admin-tools-data-state.png");
      return {
        route: "/admin/tools",
        toolCount: records.length,
        firstVisibleTool: firstName,
        screenshot: image
      };
    } finally {
      await context.close();
    }
  });

  await runScenario("admin empty state is visible after no-result search", async () => {
    const { context, page, toolResponses } = await newPage("admin-empty", browser);
    try {
      await login(page, adminUsername, adminPassword);
      await page.goto(`${baseUrl}/admin/tools`, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
      const keyword = `__no_such_tool_codex_page_state_${Date.now()}__`;
      const beforeCount = toolResponses.length;
      const searchInput = page.getByPlaceholder("搜索工具名称");
      await searchInput.fill(keyword);
      await searchInput.press("Enter");
      await page.waitForTimeout(500);
      if (toolResponses.length === beforeCount) {
        await page.getByRole("button", { name: /搜索/ }).first().click();
      }
      const response = await waitForObservedResponse(
        toolResponses,
        (item, index) => index >= beforeCount && item.url.includes(encodeURIComponent(keyword)),
        20000,
        "empty-state search request"
      );
      if (response.status !== 200) {
        throw new Error(`empty-state search returned HTTP ${response.status}`);
      }
      const pageData = response.body && typeof response.body === "object" && "data" in response.body ? response.body.data : response.body;
      const records = Array.isArray(pageData?.records) ? pageData.records : [];
      if (records.length !== 0) {
        throw new Error(`empty-state search unexpectedly returned ${records.length} records`);
      }
      await assertBodyIncludes(page, "暂无工具", "empty state");
      const image = await screenshot(page, "governance-admin-tools-empty-state.png");
      return {
        route: "/admin/tools",
        keyword,
        screenshot: image
      };
    } finally {
      await context.close();
    }
  });

  await runScenario("normal user is kept out of admin route", async () => {
    const { context, page } = await newPage("permission-denied", browser);
    try {
      await login(page, userUsername, userPassword);
      await page.goto(`${baseUrl}/admin/tools`, { waitUntil: "domcontentloaded" });
      await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
      await assertBodyIncludes(page, userUsername, "normal-user route guard");
      const image = await screenshot(page, "governance-normal-user-admin-route-guard.png");
      return {
        attemptedRoute: "/admin/tools",
        finalUrl: page.url(),
        screenshot: image
      };
    } finally {
      await context.close();
    }
  });

  await runScenario("permission-denied API message is visible inside admin page", async () => {
    const { context, page } = await newPage("api-permission-denied", browser);
    try {
      await login(page, adminUsername, adminPassword);
      await page.route("**/api/tools?**", async (route) => {
        await route.fulfill({
          status: 409,
          contentType: "application/json; charset=utf-8",
          body: JSON.stringify({
            code: "CONFLICT",
            message: "权限不足",
            data: null
          })
        });
      });
      const responsePromise = page.waitForResponse(
        (response) => response.url().includes("/api/tools?") && response.status() === 409,
        { timeout: 20000 }
      );
      await page.goto(`${baseUrl}/admin/tools`, { waitUntil: "domcontentloaded" });
      const response = await responsePromise;
      if (response.status() !== 409) {
        throw new Error(`permission-denied API returned HTTP ${response.status()}`);
      }
      await assertBodyIncludes(page, "权限不足", "permission-denied state");
      const image = await screenshot(page, "governance-admin-tools-permission-denied-state.png");
      return {
        route: "/admin/tools",
        httpStatus: response.status(),
        message: "权限不足",
        screenshot: image
      };
    } finally {
      await context.close();
    }
  });

  await runScenario("backend-unavailable message is visible when tool API fails", async () => {
    const { context, page } = await newPage("backend-unavailable", browser);
    try {
      await login(page, adminUsername, adminPassword);
      await page.route("**/api/tools?**", async (route) => {
        await route.fulfill({
          status: 503,
          contentType: "application/json; charset=utf-8",
          body: JSON.stringify({
            code: "SERVICE_UNAVAILABLE",
            message: "Service not available",
            data: null
          })
        });
      });
      const responsePromise = page.waitForResponse(
        (response) => response.url().includes("/api/tools?") && response.status() === 503,
        { timeout: 20000 }
      );
      await page.goto(`${baseUrl}/admin/tools`, { waitUntil: "domcontentloaded" });
      const response = await responsePromise;
      await assertBodyIncludes(page, "Service not available", "backend-unavailable state");
      const image = await screenshot(page, "governance-admin-tools-backend-unavailable-state.png");
      return {
        route: "/admin/tools",
        simulatedHttpStatus: response.status(),
        screenshot: image
      };
    } finally {
      await context.close();
    }
  });
} catch (error) {
  findings.error = {
    message: error.message,
    stack: error.stack
  };
} finally {
  await fs.writeFile(path.join(artifactDir, "governance-page-states-results.json"), JSON.stringify(findings, null, 2), "utf8");
  await browser.close();
}

const failures = findings.scenarios.filter((scenario) => scenario.status !== "PASS");
const blockingFailedRequests = findings.failedRequests.filter((request) =>
  !String(request.failure || "").includes("net::ERR_ABORTED")
);
if (failures.length > 0 || findings.pageErrors.length > 0 || blockingFailedRequests.length > 0) {
  console.error(JSON.stringify(findings, null, 2));
  process.exit(1);
}

console.log(JSON.stringify({
  scenarios: findings.scenarios.map((scenario) => ({
    name: scenario.name,
    status: scenario.status,
    route: scenario.route,
    screenshot: scenario.screenshot,
    message: scenario.message
  })),
  artifacts: artifactDir
}, null, 2));
