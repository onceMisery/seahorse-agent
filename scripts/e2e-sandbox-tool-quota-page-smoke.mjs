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
const runtimeNodeId = readArg("--runtime-node-id", process.env.E2E_RUNTIME_NODE_ID || "sandbox-node-b");
const browserSessionArtifactId = readArg(
  "--browser-session-artifact-id",
  process.env.E2E_BROWSER_SESSION_ARTIFACT_ID || ""
).trim();
const verifyExternalVirusScanner = hasFlag("--verify-external-virus-scanner");
const artifactDir = path.resolve(readArg(
  "--artifact-dir",
  process.env.E2E_ARTIFACT_DIR || path.join(repoRoot, "output", "playwright", "artifacts")
));
const headless = !hasFlag("--headed");

await fs.mkdir(artifactDir, { recursive: true });

let createdPolicyId = null;
let authToken = null;
let egressPolicyRestore = null;
let browserProfileRestore = null;
let createdBrowserProfileId = null;
let drainedRuntimeNodeId = null;

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

async function restoreSandboxEgressPolicy() {
  if (!authToken || !egressPolicyRestore) {
    return;
  }
  await api("/api/sandbox/runtime/egress-policy", {
    method: "POST",
    body: JSON.stringify(egressPolicyRestore)
  }).catch((error) => {
    console.warn(`Failed to restore sandbox egress policy: ${error.message}`);
  });
}

async function restoreRuntimeNodeAdmission() {
  if (!authToken || !drainedRuntimeNodeId) {
    return;
  }
  const nodeId = drainedRuntimeNodeId;
  await api(`/api/admin/sandbox/runtime/registrations/${encodeURIComponent(nodeId)}/resume`, {
    method: "POST"
  }).catch((error) => {
    console.warn(`Failed to resume runtime node ${nodeId}: ${error.message}`);
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
  const runtimeRegistrations = await api("/api/admin/sandbox/runtime/registrations");
  const initialRuntimeNode = Array.isArray(runtimeRegistrations)
    ? runtimeRegistrations.find((registration) => registration?.nodeId === runtimeNodeId)
    : null;
  if (!initialRuntimeNode || initialRuntimeNode.registrationStatus !== "LIVE" || initialRuntimeNode.operatorDraining) {
    throw new Error(`Expected a live, available runtime node ${runtimeNodeId}: ${JSON.stringify(initialRuntimeNode)}`);
  }
  const originalEgressPolicy = await api("/api/sandbox/runtime/egress-policy?tenantId=default");
  const runtimeHealth = await api("/api/sandbox/runtime/health");
  const scannerHealth = await api("/api/sandbox/runtime/artifact-scanner-health");
  const expectedScannerId = verifyExternalVirusScanner ? "clamav-plus-local-bounded" : "default-local-bounded";
  if (scannerHealth?.scannerId !== expectedScannerId || scannerHealth?.status !== "AVAILABLE"
      || scannerHealth?.available !== true || scannerHealth?.externalEngine !== verifyExternalVirusScanner) {
    throw new Error(`Unexpected artifact scanner health: ${JSON.stringify(scannerHealth)}`);
  }
  const scannerHealthJson = JSON.stringify(scannerHealth);
  if (/clamav:|3310|SEAHORSE-CLAMAV-E2E-MARKER|sandbox-workspaces/i.test(scannerHealthJson)) {
    throw new Error(`Artifact scanner health leaked runtime details: ${scannerHealthJson}`);
  }
  if (runtimeHealth?.runtime !== "container") {
    throw new Error(`Expected container sandbox runtime health: ${JSON.stringify(runtimeHealth)}`);
  }
  if (runtimeHealth.dropAllCapabilities !== true
      || runtimeHealth.noNewPrivileges !== true
      || runtimeHealth.readOnlyRootFilesystem !== true
      || Number(runtimeHealth.maxSessionFileBytes) !== 67108864
      || Number(runtimeHealth.maxSessionWorkspaceFiles) !== 256) {
    throw new Error(`Sandbox isolation posture API readback failed: ${JSON.stringify(runtimeHealth)}`);
  }
  egressPolicyRestore = {
    policyId: originalEgressPolicy?.policyId,
    tenantId: originalEgressPolicy?.tenantId || "default",
    networkPolicy: originalEgressPolicy?.networkPolicy || "DENY_ALL",
    allowlistedHosts: Array.isArray(originalEgressPolicy?.allowlistedHosts)
      ? originalEgressPolicy.allowlistedHosts.filter(Boolean).map(String)
      : [],
    browserPrivateNetworkAllowedHosts: Array.isArray(originalEgressPolicy?.browserPrivateNetworkAllowedHosts)
      ? originalEgressPolicy.browserPrivateNetworkAllowedHosts.filter(Boolean).map(String)
      : []
  };
  const egressPolicy = originalEgressPolicy?.networkPolicy || runtimeProfiles?.defaultNetworkPolicy || "DENY_ALL";
  const allowlistedHosts = Array.isArray(originalEgressPolicy?.allowlistedHosts)
    ? originalEgressPolicy.allowlistedHosts.filter(Boolean).map(String)
    : [];
  const privateNetworkAllowedHosts = Array.isArray(originalEgressPolicy?.browserPrivateNetworkAllowedHosts)
    ? originalEgressPolicy.browserPrivateNetworkAllowedHosts.filter(Boolean).map(String)
    : Array.isArray(runtimeProfiles?.browserPrivateNetworkAllowedHosts)
      ? runtimeProfiles.browserPrivateNetworkAllowedHosts.filter(Boolean).map(String)
    : Array.isArray(runtimeHealth?.browserPrivateNetworkAllowedHosts)
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
  if (typeof page.waitForFunction !== "function") {
    page.waitForFunction = async (predicate, argument, options = {}) => {
      const deadline = Date.now() + (options.timeout || 30000);
      while (Date.now() < deadline) {
        if (await page.evaluate(predicate, argument)) {
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 200));
      }
      throw new Error("Timed out waiting for page condition");
    };
  }

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
    await assertLocatorText(page.getByTestId("sandbox-runtime-isolation-posture"), "RO root", "Sandbox isolation posture");
    await assertLocatorText(page.getByTestId("sandbox-runtime-isolation-posture"), "no caps", "Sandbox isolation posture");
    await assertLocatorText(page.getByTestId("sandbox-runtime-isolation-posture"), "no new privs", "Sandbox isolation posture");
    await assertLocatorText(page.getByTestId("sandbox-runtime-file-quota"), "Session workspace quota", "Sandbox workspace quota");
    await assertLocatorText(page.getByTestId("sandbox-runtime-file-quota"), "64 MB", "Sandbox file quota");
    await assertLocatorText(page.getByTestId("sandbox-runtime-file-count-limit"), "Workspace files", "Sandbox file count limit");
    await assertLocatorText(page.getByTestId("sandbox-runtime-file-count-limit"), "256 max", "Sandbox file count limit");
    const scannerPanel = page.getByTestId("sandbox-artifact-scanner-panel");
    await scannerPanel.waitFor({ state: "visible", timeout: 20000 });
    await assertLocatorText(scannerPanel, expectedScannerId, "Artifact scanner panel");
    await assertLocatorText(scannerPanel, scannerHealth.scannerMode, "Artifact scanner panel");
    await assertLocatorText(page.getByTestId("sandbox-artifact-scanner-health"), "AVAILABLE", "Artifact scanner health");
    const scannerPanelText = await scannerPanel.innerText();
    if (/clamav:|3310|SEAHORSE-CLAMAV-E2E-MARKER|sandbox-workspaces/i.test(scannerPanelText)) {
      throw new Error(`Artifact scanner panel leaked runtime details: ${scannerPanelText}`);
    }

    const nodeRegistry = page.getByTestId("sandbox-runtime-node-registry");
    await nodeRegistry.waitFor({ state: "visible", timeout: 20000 });
    const nodeRow = page.getByTestId(`sandbox-runtime-node-registration-${runtimeNodeId}`);
    await nodeRow.waitFor({ state: "visible", timeout: 10000 });
    await assertLocatorText(
      page.getByTestId(`sandbox-runtime-node-effective-admission-${runtimeNodeId}`),
      "AVAILABLE",
      "Runtime node effective admission before drain"
    );
    const drainResponsePromise = page.waitForResponse(
      (response) => response.url().includes(
        `/api/api/admin/sandbox/runtime/registrations/${encodeURIComponent(runtimeNodeId)}/drain`
      ) && response.request().method() === "POST",
      { timeout: 20000 }
    );
    await page.getByTestId(`sandbox-runtime-node-drain-${runtimeNodeId}`).click();
    const drainResponse = await drainResponsePromise;
    const drainPayload = await drainResponse.json();
    if (drainResponse.status() !== 200 || drainPayload?.code !== "0" || drainPayload?.data?.draining !== true) {
      throw new Error(`Runtime node drain failed: HTTP ${drainResponse.status()} ${JSON.stringify(drainPayload)}`);
    }
    drainedRuntimeNodeId = runtimeNodeId;
    const drainedRegistrations = await api("/api/admin/sandbox/runtime/registrations");
    const drainedRuntimeNode = Array.isArray(drainedRegistrations)
      ? drainedRegistrations.find((registration) => registration?.nodeId === runtimeNodeId)
      : null;
    if (!drainedRuntimeNode || drainedRuntimeNode.operatorDraining !== true
        || drainedRuntimeNode.effectiveAdmissionStatus !== "DRAINING"
        || drainedRuntimeNode.effectiveAdmissionAvailable !== false) {
      throw new Error(`Runtime node drain API readback failed: ${JSON.stringify(drainedRuntimeNode)}`);
    }
    const maintenance = await api(
      `/api/admin/sandbox/runtime/registrations/${encodeURIComponent(runtimeNodeId)}/maintenance-status`
    );
    if (maintenance?.operatorDraining !== true
        || Number(maintenance.persistedActiveSessionCount) !== 0
        || Number(maintenance.pendingReservationCount) !== 0
        || maintenance.createOperationTrackingAvailable !== true
        || Number(maintenance.inFlightCreateOperationCount) !== 0
        || maintenance.stabilizationElapsed !== false
        || maintenance.maintenanceReady !== false) {
      throw new Error(`Runtime node maintenance readback failed: ${JSON.stringify(maintenance)}`);
    }
    const maintenancePanel = page.getByTestId(`sandbox-runtime-node-maintenance-${runtimeNodeId}`);
    await assertLocatorText(maintenancePanel, "NOT READY", "Runtime node maintenance readiness");
    await assertLocatorText(maintenancePanel, "Sessions: 0", "Runtime node session count");
    await assertLocatorText(maintenancePanel, "Reservations: 0", "Runtime node reservation count");
    await assertLocatorText(maintenancePanel, "In-flight creates: 0", "Runtime node create count");
    await assertLocatorText(maintenancePanel, "Create tracking: AVAILABLE", "Runtime node create tracking");

    const resumeResponsePromise = page.waitForResponse(
      (response) => response.url().includes(
        `/api/api/admin/sandbox/runtime/registrations/${encodeURIComponent(runtimeNodeId)}/resume`
      ) && response.request().method() === "POST",
      { timeout: 20000 }
    );
    await page.getByTestId(`sandbox-runtime-node-resume-${runtimeNodeId}`).click();
    const resumeResponse = await resumeResponsePromise;
    const resumePayload = await resumeResponse.json();
    if (resumeResponse.status() !== 200 || resumePayload?.code !== "0" || resumePayload?.data?.draining !== false) {
      throw new Error(`Runtime node resume failed: HTTP ${resumeResponse.status()} ${JSON.stringify(resumePayload)}`);
    }
    const resumedRegistrations = await api("/api/admin/sandbox/runtime/registrations");
    const resumedRuntimeNode = Array.isArray(resumedRegistrations)
      ? resumedRegistrations.find((registration) => registration?.nodeId === runtimeNodeId)
      : null;
    if (!resumedRuntimeNode || resumedRuntimeNode.operatorDraining !== false
        || resumedRuntimeNode.effectiveAdmissionStatus !== "AVAILABLE"
        || resumedRuntimeNode.effectiveAdmissionAvailable !== true) {
      throw new Error(`Runtime node resume API readback failed: ${JSON.stringify(resumedRuntimeNode)}`);
    }
    await page.waitForFunction(
      (nodeId) => document.querySelector(`[data-testid="sandbox-runtime-node-effective-admission-${nodeId}"]`)
        ?.textContent?.trim() === "AVAILABLE",
      runtimeNodeId,
      { timeout: 10000 }
    );
    drainedRuntimeNodeId = null;

    const egressSmokeHost = `aaa-egress-policy-page-smoke-${Date.now()}.invalid`;
    const privateNetworkSmokeHost = `aaa-private-network-page-smoke-${Date.now()}.invalid`;
    const editedAllowlistedHosts = Array.from(new Set([...allowlistedHosts, egressSmokeHost])).sort();
    const editedPrivateNetworkAllowedHosts = Array.from(new Set([
      ...privateNetworkAllowedHosts,
      privateNetworkSmokeHost
    ])).sort();
    await page.getByTestId("sandbox-egress-policy-select").selectOption("ALLOWLISTED");
    await page.getByTestId("sandbox-egress-allowlist-input").fill(editedAllowlistedHosts.join("\n"));
    await page.getByTestId("sandbox-egress-private-network-input").fill(editedPrivateNetworkAllowedHosts.join("\n"));
    const egressResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/api/sandbox/runtime/egress-policy") &&
        response.request().method() === "POST",
      { timeout: 20000 }
    );
    await page.getByTestId("sandbox-egress-policy-save").click();
    const egressResponse = await egressResponsePromise;
    const egressPayload = await egressResponse.json();
    if (egressResponse.status() !== 200 || egressPayload?.code !== "0") {
      throw new Error(`Sandbox egress policy save failed: HTTP ${egressResponse.status()} ${JSON.stringify(egressPayload)}`);
    }
    const savedEgressPolicy = egressPayload.data;
    if (savedEgressPolicy?.networkPolicy !== "ALLOWLISTED"
        || !Array.isArray(savedEgressPolicy?.allowlistedHosts)
        || !savedEgressPolicy.allowlistedHosts.includes(egressSmokeHost)
        || !Array.isArray(savedEgressPolicy?.browserPrivateNetworkAllowedHosts)
        || !savedEgressPolicy.browserPrivateNetworkAllowedHosts.includes(privateNetworkSmokeHost)) {
      throw new Error(`Unexpected sandbox egress policy response: ${JSON.stringify(savedEgressPolicy)}`);
    }
    const apiEgressPolicy = await api("/api/sandbox/runtime/egress-policy?tenantId=default");
    if (apiEgressPolicy?.networkPolicy !== "ALLOWLISTED"
        || !Array.isArray(apiEgressPolicy?.allowlistedHosts)
        || !apiEgressPolicy.allowlistedHosts.includes(egressSmokeHost)
        || !Array.isArray(apiEgressPolicy?.browserPrivateNetworkAllowedHosts)
        || !apiEgressPolicy.browserPrivateNetworkAllowedHosts.includes(privateNetworkSmokeHost)) {
      throw new Error(`Sandbox egress policy API readback failed: ${JSON.stringify(apiEgressPolicy)}`);
    }
    await page.waitForFunction(
      () => document.querySelector('[data-testid="sandbox-egress-policy-name"]')?.textContent?.includes("ALLOWLISTED"),
      null,
      { timeout: 10000 }
    );
    await assertLocatorText(page.getByTestId("sandbox-egress-policy-name"), "ALLOWLISTED", "Saved sandbox egress policy");
    await page.waitForFunction(
      (expected) => document.querySelector('[data-testid="sandbox-egress-allowlist-count"]')?.textContent?.includes(expected),
      `${editedAllowlistedHosts.length} hosts`,
      { timeout: 10000 }
    );
    await assertLocatorText(
      page.getByTestId("sandbox-egress-allowlist-count"),
      `${editedAllowlistedHosts.length} hosts`,
      "Saved sandbox egress allowlist count"
    );
    await page.waitForFunction(
      (host) => document.querySelector('[data-testid="sandbox-egress-allowlist-input"]')?.value.includes(host),
      egressSmokeHost,
      { timeout: 10000 }
    );
    await page.waitForFunction(
      (expected) => document.querySelector('[data-testid="sandbox-egress-private-network-count"]')?.textContent?.includes(expected),
      `${editedPrivateNetworkAllowedHosts.length} hosts`,
      { timeout: 10000 }
    );
    await assertLocatorText(
      page.getByTestId("sandbox-egress-private-network-count"),
      `${editedPrivateNetworkAllowedHosts.length} hosts`,
      "Saved sandbox private network exception count"
    );
    await page.waitForFunction(
      (host) => document.querySelector('[data-testid="sandbox-egress-private-network-input"]')?.value.includes(host),
      privateNetworkSmokeHost,
      { timeout: 10000 }
    );

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

    if (browserSessionArtifactId) {
      const browserProfilesPanel = page.getByTestId("sandbox-browser-profiles-panel");
      await browserProfilesPanel.waitFor({ state: "visible", timeout: 20000 });
      const browserProfileName = `Browser profile ${marker}`;
      await browserProfilesPanel.getByTestId("sandbox-browser-profile-name").fill(browserProfileName);
      await browserProfilesPanel.getByTestId("sandbox-browser-profile-artifact").fill(browserSessionArtifactId);
      await browserProfilesPanel.getByTestId("sandbox-browser-profile-expires").fill("2030-01-02T03:04");
      const profileResponsePromise = page.waitForResponse(
        (response) =>
          response.url().includes("/api/api/sandbox/runtime/browser-profiles")
          && response.request().method() === "POST",
        { timeout: 20000 }
      );
      await browserProfilesPanel.getByTestId("sandbox-browser-profile-save").click();
      const profileResponse = await profileResponsePromise;
      const profilePayload = await profileResponse.json();
      if (profileResponse.status() !== 200 || profilePayload?.code !== "0") {
        throw new Error(`Browser profile save failed: HTTP ${profileResponse.status()} ${JSON.stringify(profilePayload)}`);
      }
      const savedBrowserProfile = profilePayload.data;
      createdBrowserProfileId = String(savedBrowserProfile?.profileId || "");
      if (!createdBrowserProfileId || savedBrowserProfile?.status !== "ACTIVE"
          || savedBrowserProfile?.sessionStateArtifactId !== browserSessionArtifactId) {
        throw new Error(`Unexpected browser profile response: ${JSON.stringify(savedBrowserProfile)}`);
      }
      await assertPanelText(page, browserProfileName, "Browser profiles panel");
      const apiProfiles = await api("/api/sandbox/runtime/browser-profiles?tenantId=default");
      const apiBrowserProfile = Array.isArray(apiProfiles)
        ? apiProfiles.find((profile) => profile?.profileId === createdBrowserProfileId)
        : null;
      if (!apiBrowserProfile || apiBrowserProfile.status !== "ACTIVE"
          || apiBrowserProfile.sessionStateArtifactId !== browserSessionArtifactId) {
        throw new Error(`Browser profile API readback failed: ${JSON.stringify(apiProfiles)}`);
      }
      const disableResponsePromise = page.waitForResponse(
        (response) =>
          response.url().includes(`/api/api/sandbox/runtime/browser-profiles/${createdBrowserProfileId}:disable`)
          && response.request().method() === "POST",
        { timeout: 20000 }
      );
      await browserProfilesPanel.getByTestId(`sandbox-browser-profile-disable-${createdBrowserProfileId}`).click();
      const disableResponse = await disableResponsePromise;
      const disablePayload = await disableResponse.json();
      if (disableResponse.status() !== 200 || disablePayload?.code !== "0" || disablePayload?.data?.status !== "DISABLED") {
        throw new Error(`Browser profile disable failed: HTTP ${disableResponse.status()} ${JSON.stringify(disablePayload)}`);
      }
      await page.waitForFunction(
        (profileId) => document.querySelector(`[data-testid="sandbox-browser-profile-disable-${profileId}"]`)?.hasAttribute("disabled"),
        createdBrowserProfileId,
        { timeout: 10000 }
      );
    }

    const screenshotPath = path.join(artifactDir, `${marker}.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true });
    console.log(`PASS sandbox tool quota page smoke`);
    console.log(`Egress policy: ${egressPolicy} / ${allowlistedHosts.length} hosts`);
    console.log(`Saved egress policy: ${savedEgressPolicy.networkPolicy} / ${savedEgressPolicy.allowlistedHosts.length} hosts`);
    console.log(`Private network exceptions: ${privateNetworkAllowedHosts.length} hosts`);
    console.log(`Saved private network exceptions: ${savedEgressPolicy.browserPrivateNetworkAllowedHosts.length} hosts`);
    console.log(`Browser runtime network policy: ${savedProfilePolicy.networkAllowed}`);
    console.log(`Artifact scanner: ${scannerHealth.scannerId} / ${scannerHealth.status}`);
    if (createdBrowserProfileId) console.log(`Browser profile: ${createdBrowserProfileId}`);
    console.log(`Policy: ${policyId}`);
    console.log(`Screenshot: ${screenshotPath}`);
  } finally {
    await context.close();
  }
} finally {
  await restoreRuntimeNodeAdmission();
  await restoreSandboxEgressPolicy();
  await restoreBrowserProfilePolicy();
  await cleanupPolicy();
  await browser.close();
}

if (findings.pageErrors.length > 0) {
  throw new Error(`Page errors observed: ${JSON.stringify(findings.pageErrors)}`);
}
