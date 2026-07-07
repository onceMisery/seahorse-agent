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
const apiBaseUrl = readArg("--api-url", process.env.E2E_API_URL || "http://127.0.0.1:9090").replace(/\/$/, "");
const username = readArg("--username", process.env.E2E_USERNAME || "admin");
const password = readArg("--password", process.env.E2E_PASSWORD || "admin123");
const artifactDir = path.resolve(readArg("--artifact-dir", process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")));
const headless = !hasFlag("--headed");

await fs.mkdir(artifactDir, { recursive: true });

function apiUrl(pathname) {
  return `${apiBaseUrl}${pathname}`;
}

async function api(pathname, options = {}) {
  const response = await fetch(apiUrl(pathname), {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "content-type": "application/json" }),
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

async function prepareRealConversation(token, marker) {
  const auth = { Authorization: `Bearer ${token}` };
  const runProfiles = await api("/api/run-profiles", { headers: auth });
  const preferredIds = new Set(["-9101", "-9102"]);
  const allProfiles = (Array.isArray(runProfiles) ? runProfiles : []).filter((profile) => profile?.id && profile?.name);
  let profiles = allProfiles
    .filter((profile) => preferredIds.has(String(profile.id)) && (profile.executorEngine || "kernel") === "kernel")
    .sort((left, right) => String(left.id).localeCompare(String(right.id)))
    .slice(0, 2);
  if (profiles.length < 2) {
    profiles = allProfiles.filter((profile) => (profile.executorEngine || "kernel") === "kernel").slice(0, 2);
  }
  if (profiles.length < 2) {
    throw new Error(`run profile list returned fewer than two usable profiles: ${JSON.stringify(runProfiles)}`);
  }

  const conversationId = await api("/api/conversations", { method: "POST", headers: auth });
  if (!conversationId) {
    throw new Error("create conversation API did not return an id");
  }

  const question = `Run experiment UI smoke ${marker}: answer with one short sentence.`;
  const chatResponse = await fetch(
    apiUrl(`/rag/v3/chat?conversationId=${encodeURIComponent(String(conversationId))}&question=${encodeURIComponent(question)}`),
    { headers: auth }
  );
  const chatText = await chatResponse.text();
  if (!chatResponse.ok || !chatResponse.headers.get("content-type")?.includes("text/event-stream")) {
    throw new Error(`chat SSE failed HTTP ${chatResponse.status}: ${chatText.slice(0, 500)}`);
  }
  if (!chatText.includes("[DONE]")) {
    throw new Error(`chat SSE did not include [DONE]: ${chatText.slice(0, 500)}`);
  }

  await new Promise((resolve) => setTimeout(resolve, 2000));
  const messages = await api(`/api/conversations/${encodeURIComponent(String(conversationId))}/messages`, { headers: auth });
  const assistant = (Array.isArray(messages) ? messages : []).filter((message) => message?.role === "assistant").at(-1);
  if (!assistant?.id) {
    throw new Error(`conversation ${conversationId} did not produce an assistant message`);
  }

  return { auth, conversationId: String(conversationId), baseLeafMessageId: String(assistant.id), profiles };
}

async function loginUi(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
}

async function bodyText(page) {
  return page.locator("body").innerText({ timeout: 10000 });
}

async function assertBodyContains(page, expected, context) {
  const text = await bodyText(page);
  if (!text.includes(expected)) {
    throw new Error(`${context} did not render '${expected}'. Body preview: ${text.slice(0, 1000)}`);
  }
}

async function clickButtonByText(page, candidates, context) {
  const escaped = candidates.map((value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|");
  const button = page.getByRole("button", { name: new RegExp(escaped) }).first();
  await button.waitFor({ state: "visible", timeout: 15000 });
  await button.click();
}

function responseMatches(response, endpointSuffix) {
  if (response.status() !== 200) {
    return false;
  }
  const pathname = new URL(response.url()).pathname;
  return pathname === endpointSuffix || pathname === `/api${endpointSuffix}`;
}

async function createExperimentInUi(page, subjects, marker) {
  await page.goto(`${baseUrl}/admin/run-experiments`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction((name) => document.body.innerText.includes(name), subjects.profiles[0].name, { timeout: 20000 });

  const inputs = page.locator("label input");
  await inputs.nth(0).fill(`${marker}-profile-compare`);
  await inputs.nth(1).fill(subjects.conversationId);
  await inputs.nth(2).fill(subjects.baseLeafMessageId);
  for (const profile of subjects.profiles) {
    await page.getByLabel(profile.name, { exact: true }).check();
  }

  const responsePromise = page.waitForResponse(
    (response) => responseMatches(response, "/api/run-experiments"),
    { timeout: 180000 }
  );
  await clickButtonByText(page, ["发起实验", "鍙戣捣瀹為獙"], "create experiment");
  const response = await responsePromise;
  const payload = await response.json();
  const experimentId = payload?.data?.experiment?.id;
  const status = payload?.data?.experiment?.status;
  if (!experimentId) {
    throw new Error(`create experiment response did not include id: ${JSON.stringify(payload).slice(0, 500)}`);
  }
  if (status !== "SUCCEEDED") {
    throw new Error(`create experiment returned status ${status}: ${JSON.stringify(payload).slice(0, 1000)}`);
  }
  try {
    await page.waitForFunction((id) => document.body.innerText.includes(`SUCCEEDED`) && document.body.innerText.includes(String(id)), experimentId, {
      timeout: 30000
    });
  } catch (error) {
    const text = await bodyText(page).catch((bodyError) => `Unable to read body: ${bodyError.message}`);
    throw new Error(`run experiment page did not render SUCCEEDED experiment ${experimentId}: ${error.message}\nBody preview: ${text.slice(0, 1200)}`);
  }
  await assertBodyContains(page, String(experimentId), "run experiment page");
  for (const profile of subjects.profiles) {
    await assertBodyContains(page, profile.name, "run experiment page");
  }
  return String(experimentId);
}

async function exportReportInUi(page, experimentId) {
  const responsePromise = page.waitForResponse(
    (response) => responseMatches(response, `/api/run-experiments/${experimentId}/report`),
    { timeout: 30000 }
  );
  await clickButtonByText(page, ["导出报告", "瀵煎嚭鎶ュ憡"], "export report");
  const response = await responsePromise;
  const payload = await response.json();
  const report = payload?.data;
  if (!report?.markdown) {
    throw new Error(`report response did not include markdown: ${JSON.stringify(payload).slice(0, 500)}`);
  }
  await page.waitForFunction(() => document.body.innerText.includes("Report preview"), null, { timeout: 15000 });
  await assertBodyContains(page, "Run Experiment Report", "report preview");
  await assertBodyContains(page, "Evidence Completeness Summary", "report preview");
  await assertBodyContains(page, "Output Comparison", "report preview");
  await assertBodyContains(page, "Reproduction Appendix", "report preview");
  return report;
}

const marker = `CODX_RUN_EXP_UI_${Date.now()}`;
const token = await loginApi();
const subjects = await prepareRealConversation(token, marker);

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1440, height: 920 } });
page.on("console", (message) => {
  if (message.type() === "error") {
    console.error(`[browser:${message.type()}] ${message.text()}`);
  }
});
page.on("requestfailed", (request) => {
  console.error(`[requestfailed] ${request.method()} ${request.url()} ${request.failure()?.errorText || ""}`);
});

try {
  await loginUi(page);
  const experimentId = await createExperimentInUi(page, subjects, marker);
  const report = await exportReportInUi(page, experimentId);
  const screenshot = path.join(artifactDir, `run-experiment-ui-report-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  console.log(
    JSON.stringify(
      {
        ok: true,
        marker,
        conversationId: subjects.conversationId,
        baseLeafMessageId: subjects.baseLeafMessageId,
        runProfileIds: subjects.profiles.map((profile) => String(profile.id)),
        experimentId,
        reportFile: report.fileName,
        screenshot
      },
      null,
      2
    )
  );
} finally {
  await browser.close();
}
