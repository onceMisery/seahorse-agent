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
const marker = readArg("--marker", process.env.E2E_MARKER || "seahorse-sandbox-tool-quota-page-smoke");
const artifactDir = path.resolve(readArg(
  "--artifact-dir",
  process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")
));
const headless = !hasFlag("--headed");

await fs.mkdir(artifactDir, { recursive: true });

let createdPolicyId = null;
let authToken = null;
let browserProfileRestore = null;

async function api(pathname, options = {}) {
  const pathWithSlash = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const proxyPath = `/api${pathWithSlash}`;
  const response = await fetch(`${baseUrl}${proxyPath}`, {
    ...options,
    headers: {
      "content-type": "application/json",
      ...(authToken ? { authorization: `Bearer ${authToken}` } : {}),
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
  authToken = data.token;
}

async function loginPage(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
}

async function assertPanelText(page, text, label) {
  await page.waitForFunction(
    (expected) => document.body.innerText.includes(expected),
    text,
    { timeout: 10000 }
  ).catch(async () => {
    const visible = await page.locator("body").innerText({ timeout: 5000 }).catch(() => "");
    throw new Error(`${label} did not show '${text}'. Visible text: ${visible.slice(0, 1200)}`);
  });
}

async function assertLocatorText(locator, text, label) {
  await locator.waitFor({ state: "visible", timeout: 10000 });
  const visible = await locator.innerText({ timeout: 5000 });
  if (!visible.includes(text)) {
    throw new Error(`${label} did not show '${text}'. Visible text: ${visible.slice(0, 1200)}`);
  }
}

function assertNumericField(payload, field, expected) {
  const actual = Number(payload?.[field]);
  if (!Number.isFinite(actual) || actual !== expected) {
    throw new Error(`Unexpected quota ${field}: ${JSON.stringify(payload)}`);
  }
}

async function cleanupPolicy() {
  if (!createdPolicyId || !authToken) {
    return;
  }
  await api("/api/sandbox/runtime/tool-quota-policies", {
    method: "POST",
    body: JSON.stringify({
      policyId: createdPolicyId,
      tenantId: "default",
      toolId: "sandbox_python",
      status: "DISABLED",
      callLimit: 0,
      warnRatio: 0.75
    })
  }).catch((error) => {
    console.warn(`Failed to disable quota policy ${createdPolicyId}: ${error.message}`);
  });
}

async function restoreBrowserProfilePolicy() {
  if (!authToken || !browserProfileRestore) {
    return;
  }
  await api("/api/sandbox/runtime/profile-policies", {
    method: "POST",
    body: JSON.stringify(browserProfileRestore)
  }).catch((error) => {
    console.warn(`Failed to restore browser runtime profile policy: ${error.message}`);
  });
}

const browser = await chromium.launch({ headless });
const findings = {
  console: [],
  pageErrors: [],
  failedRequests: []
};

try {
  await loginApi();
  const runtimeProfiles = await api("/api/sandbox/runtime/profiles?tenantId=default");
  const runtimeHealth = await api("/api/sandbox/runtime/health");
  const egressPolicy = runtimeProfiles?.defaultNetworkPolicy || "DENY_ALL";
  const allowlistedHosts = Array.isArray(runtimeProfiles?.allowlistedHosts)
    ? runtimeProfiles.allowlistedHosts.filter(Boolean).map(String)
    : [];
  const privateNetworkAllowedHosts = Array.isArray(runtimeHealth?.browserPrivateNetworkAllowedHosts)
    ? runtimeHealth.browserPrivateNetworkAllowedHosts.filter(Boolean).map(String)
    : [];
  const browserProfile = (Array.isArray(runtimeProfiles?.profiles) ? runtimeProfiles.profiles : [])
    .find((profile) => profile?.runtimeType === "BROWSER_AUTOMATION");
  if (!browserProfile) {
    throw new Error(`BROWSER_AUTOMATION runtime profile missing: ${JSON.stringify(runtimeProfiles)}`);
  }
  browserProfileRestore = {
    policyId: browserProfile.policyId,
    tenantId: "default",
    runtimeType: "BROWSER_AUTOMATION",
    profileId: browserProfile.profileId,
    status: browserProfile.policyStatus || "ACTIVE",
    sessionTtlSeconds: browserProfile.sessionTtlSeconds || runtimeProfiles?.defaultTtlSeconds || 3600,
    networkAllowed: Boolean(browserProfile.networkAllowed)
  };
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    ignoreHTTPSErrors: true
  });
  const page = await context.newPage();

  page.on("console", (message) => {
    if (["error", "warning"].includes(message.type())) {
      findings.console.push({
        type: message.type(),
        text: message.text(),
        location: message.location()
      });
    }
  });
  page.on("pageerror", (error) => {
    findings.pageErrors.push({ message: error.message, stack: error.stack });
  });
  page.on("requestfailed", (request) => {
    findings.failedRequests.push({
      method: request.method(),
      url: request.url(),
      failure: request.failure()?.errorText || ""
    });
  });

  try {
    await loginPage(page);
    await page.goto(`${baseUrl}/admin/sandbox`, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);

    const egressPanel = page.getByTestId("sandbox-egress-policy-panel");
    await egressPanel.waitFor({ state: "visible", timeout: 20000 });
    await assertLocatorText(page.getByTestId("sandbox-egress-policy-name"), egressPolicy, "Sandbox egress policy");
    await assertLocatorText(
      page.getByTestId("sandbox-egress-allowlist-count"),
      `${allowlistedHosts.length} hosts`,
      "Sandbox egress allowlist count"
    );
    for (const host of allowlistedHosts.slice(0, 6)) {
      await assertLocatorText(page.getByTestId("sandbox-egress-allowlist-preview"), host, "Sandbox egress allowlist");
    }
    await assertLocatorText(
      page.getByTestId("sandbox-egress-private-network-count"),
      `${privateNetworkAllowedHosts.length} hosts`,
      "Sandbox private network exception count"
    );
    for (const host of privateNetworkAllowedHosts.slice(0, 6)) {
      await assertLocatorText(
        page.getByTestId("sandbox-egress-private-network-preview"),
        host,
        "Sandbox private network exceptions"
      );
    }

    const browserNetworkToggle = page.getByTestId("sandbox-runtime-profile-network-BROWSER_AUTOMATION");
    await browserNetworkToggle.waitFor({ state: "visible", timeout: 10000 });
    await browserNetworkToggle.setChecked(true);
    const profileResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/api/sandbox/runtime/profile-policies") &&
        response.request().method() === "POST",
      { timeout: 20000 }
    );
    await page.getByTestId("sandbox-runtime-profile-save-BROWSER_AUTOMATION").click();
    const profileResponse = await profileResponsePromise;
    const profilePayload = await profileResponse.json();
    if (profileResponse.status() !== 200 || profilePayload?.code !== "0") {
      throw new Error(`Runtime profile network save failed: HTTP ${profileResponse.status()} ${JSON.stringify(profilePayload)}`);
    }
    const savedProfilePolicy = profilePayload.data;
    if (savedProfilePolicy?.runtimeType !== "BROWSER_AUTOMATION" || savedProfilePolicy?.networkAllowed !== true) {
      throw new Error(`Unexpected browser runtime profile policy response: ${JSON.stringify(savedProfilePolicy)}`);
    }
    await page.waitForFunction(
      () =>
        document.querySelector('[data-testid="sandbox-runtime-profile-network-status-BROWSER_AUTOMATION"]')
          ?.textContent
          ?.trim() === "NETWORK",
      null,
      { timeout: 10000 }
    );
    const browserNetworkStatus = await page
      .getByTestId("sandbox-runtime-profile-network-status-BROWSER_AUTOMATION")
      .innerText({ timeout: 5000 });
    if (browserNetworkStatus.trim() !== "NETWORK") {
      throw new Error(`Browser runtime profile network status did not become NETWORK: ${browserNetworkStatus}`);
    }

    const panel = page.getByTestId("sandbox-tool-quota-panel");
    await panel.waitFor({ state: "visible", timeout: 20000 });

    const policyId = `sandbox-tool-quota-page-${Date.now()}`;
    createdPolicyId = policyId;
    await panel.getByTestId("sandbox-tool-quota-policy-id").fill(policyId);
    await panel.getByTestId("sandbox-tool-quota-tool").selectOption("sandbox_python");
    await panel.getByTestId("sandbox-tool-quota-status").selectOption("ACTIVE");
    await panel.getByTestId("sandbox-tool-quota-call-limit").fill("0");
    await panel.getByTestId("sandbox-tool-quota-token-limit").fill("");
    await panel.getByTestId("sandbox-tool-quota-cost-limit").fill("");
    await panel.getByTestId("sandbox-tool-quota-warn-ratio").fill("0.75");

    const quotaResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/api/sandbox/runtime/tool-quota-policies") &&
        response.request().method() === "POST",
      { timeout: 20000 }
    );
    await panel.getByTestId("sandbox-tool-quota-save").click();
    const quotaResponse = await quotaResponsePromise;
    const quotaPayload = await quotaResponse.json();
    if (quotaResponse.status() !== 200 || quotaPayload?.code !== "0") {
      throw new Error(`Tool quota save failed: HTTP ${quotaResponse.status()} ${JSON.stringify(quotaPayload)}`);
    }
    const policy = quotaPayload.data;
    if (policy?.policyId !== policyId) {
      throw new Error(`Unexpected policyId: ${JSON.stringify(policy)}`);
    }
    if (policy?.scope !== "TOOL" || policy?.subjectId !== "sandbox_python") {
      throw new Error(`Unexpected quota scope response: ${JSON.stringify(policy)}`);
    }
    assertNumericField(policy, "callLimit", 0);
    assertNumericField(policy, "warnRatio", 0.75);

    await assertPanelText(page, policyId, "Tool quota panel");
    await assertPanelText(page, "TOOL", "Tool quota panel");
    await assertPanelText(page, "sandbox_python", "Tool quota panel");

    const screenshotPath = path.join(artifactDir, `${marker}.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true });
    console.log(`PASS sandbox tool quota page smoke`);
    console.log(`Egress policy: ${egressPolicy} / ${allowlistedHosts.length} hosts`);
    console.log(`Private network exceptions: ${privateNetworkAllowedHosts.length} hosts`);
    console.log(`Browser runtime network policy: ${savedProfilePolicy.networkAllowed}`);
    console.log(`Policy: ${policyId}`);
    console.log(`Screenshot: ${screenshotPath}`);
  } finally {
    await context.close();
  }
} finally {
  await restoreBrowserProfilePolicy();
  await cleanupPolicy();
  await browser.close();
}

if (findings.pageErrors.length > 0) {
  throw new Error(`Page errors observed: ${JSON.stringify(findings.pageErrors)}`);
}
