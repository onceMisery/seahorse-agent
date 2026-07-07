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
const apiBaseUrl = readArg("--api-url", process.env.E2E_API_URL || baseUrl).replace(/\/$/, "");
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

async function prepareRealSubjects(token, marker) {
  const auth = { Authorization: `Bearer ${token}` };

  const runProfiles = await api("/api/run-profiles", { headers: auth });
  const runProfile = (Array.isArray(runProfiles) ? runProfiles : []).find((profile) => profile?.id);
  if (!runProfile) {
    throw new Error("run profile list returned no usable profile");
  }

  const skillPage = await api("/api/skills?tenantId=default&current=1&size=20", { headers: auth });
  const skills = Array.isArray(skillPage?.records) ? skillPage.records : [];
  const skill = skills.find((item) => item?.name);
  if (!skill) {
    throw new Error("skill list returned no usable skill");
  }

  const pipeline = await api("/ingestion/pipelines", {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      name: `${marker}-gate-ui`,
      description: "Codex GateResult UI smoke pipeline",
      nodes: [
        { nodeId: "1", nodeType: "parser", nextNodeId: "2", settings: {} },
        { nodeId: "2", nodeType: "chunker", settings: { chunkSize: 80, overlapSize: 0, embed: false } }
      ]
    })
  });
  if (!pipeline?.id) {
    throw new Error(`pipeline create did not return id: ${JSON.stringify(pipeline)}`);
  }

  return { runProfile, skill, pipeline, auth };
}

async function cleanupSubjects(subjects) {
  if (!subjects?.pipeline?.id) return;
  await api(`/ingestion/pipelines/${encodeURIComponent(String(subjects.pipeline.id))}`, {
    method: "DELETE",
    headers: subjects.auth
  }).catch(() => null);
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

function responseMatchesGateResult(response, endpointSuffix) {
  if (response.status() !== 200) {
    return false;
  }
  const pathname = new URL(response.url()).pathname;
  return pathname === endpointSuffix || pathname === `/api${endpointSuffix}`;
}

async function clickAndWaitForGateResult(page, action, endpointSuffix, context) {
  const responsePromise = page.waitForResponse(
    (response) => responseMatchesGateResult(response, endpointSuffix),
    { timeout: 20000 }
  );
  try {
    await action();
    return await responsePromise;
  } catch (error) {
    responsePromise.catch(() => null);
    const text = await bodyText(page).catch((bodyError) => `Unable to read body: ${bodyError.message}`);
    throw new Error(`${context} failed while waiting for ${endpointSuffix}: ${error.message}\nBody preview: ${text.slice(0, 1000)}`);
  }
}

async function verifyRunProfileGateResult(page, runProfile) {
  await page.goto(`${baseUrl}/admin/run-profiles`, { waitUntil: "domcontentloaded" });
  const button = page.getByRole("button", { name: `run-profile-gate-result-${runProfile.id}` });
  await button.waitFor({ state: "visible", timeout: 20000 });
  await clickAndWaitForGateResult(
    page,
    () => button.click(),
    `/api/run-profiles/${runProfile.id}/production-gate/gate-result`,
    "run profile GateResult"
  );
  await page.waitForFunction(() => document.body.innerText.includes("Run Profile GateResult"), null, { timeout: 15000 });
  await assertBodyContains(page, "RUN_PROFILE", "run profile GateResult panel");
  await assertBodyContains(page, "RUN_PROFILE_RISK_ASSESSED", "run profile GateResult panel");
}

async function verifySkillGateResult(page, skill) {
  await page.goto(`${baseUrl}/admin/skills`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction((skillName) => document.body.innerText.includes(skillName), skill.name, { timeout: 20000 });
  const card = page.getByText(skill.name, { exact: true }).locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' rounded-lg ') and .//button[normalize-space(.)='Gate']][1]"
  );
  await clickAndWaitForGateResult(
    page,
    () => card.getByRole("button", { name: "Gate" }).click(),
    `/api/skills/${encodeURIComponent(skill.name)}/gate-result`,
    "skill GateResult"
  );
  await page.waitForFunction(() => document.body.innerText.includes("Skill GateResult"), null, { timeout: 15000 });
  await assertBodyContains(page, "SKILL", "skill GateResult dialog");
  await assertBodyContains(page, "SKILL_SECURITY_SCAN", "skill GateResult dialog");
}

async function verifyPipelineGateResult(page, pipeline) {
  await page.goto(`${baseUrl}/admin/ingestion?tab=pipelines`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction((pipelineName) => document.body.innerText.includes(pipelineName), pipeline.name, { timeout: 20000 });
  const row = page.getByRole("row", { name: new RegExp(pipeline.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) });
  await clickAndWaitForGateResult(
    page,
    () => row.getByRole("button", { name: "Gate" }).click(),
    `/api/ingestion/pipelines/${pipeline.id}/gate-result`,
    "pipeline GateResult"
  );
  await page.waitForFunction(() => document.body.innerText.includes("Pipeline GateResult"), null, { timeout: 15000 });
  await assertBodyContains(page, "INGESTION_PIPELINE", "pipeline GateResult dialog");
  await assertBodyContains(page, "INGESTION_PIPELINE_NODES_PRESENT", "pipeline GateResult dialog");
}

const marker = `CODX_GATE_UI_${Date.now()}`;
const token = await loginApi();
const subjects = await prepareRealSubjects(token, marker);

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

const result = {
  marker,
  runProfileId: String(subjects.runProfile.id),
  skillName: String(subjects.skill.name),
  pipelineId: String(subjects.pipeline.id),
  screenshots: {}
};

try {
  await loginUi(page);

  await verifyRunProfileGateResult(page, subjects.runProfile);
  result.screenshots.runProfile = path.join(artifactDir, `gate-result-ui-run-profile-${marker}.png`);
  await page.screenshot({ path: result.screenshots.runProfile, fullPage: true });

  await verifySkillGateResult(page, subjects.skill);
  result.screenshots.skill = path.join(artifactDir, `gate-result-ui-skill-${marker}.png`);
  await page.screenshot({ path: result.screenshots.skill, fullPage: true });

  await verifyPipelineGateResult(page, subjects.pipeline);
  result.screenshots.pipeline = path.join(artifactDir, `gate-result-ui-pipeline-${marker}.png`);
  await page.screenshot({ path: result.screenshots.pipeline, fullPage: true });

  console.log(JSON.stringify({ ok: true, ...result }, null, 2));
} finally {
  await browser.close();
  await cleanupSubjects(subjects);
}
