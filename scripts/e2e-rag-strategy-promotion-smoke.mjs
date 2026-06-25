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

function apiUrl(pathname) {
  return `${baseUrl}/api${pathname}`;
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

function psql(sql) {
  const result = spawnSync("docker", [
    "exec",
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
    "-c",
    sql
  ], { encoding: "utf8" });
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
  if (!data?.token) {
    throw new Error("login API did not return token");
  }
  return data.token;
}

async function waitForChunks(auth, docId) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const data = await api(`/knowledge-base/docs/${encodeURIComponent(docId)}/chunks?current=1&size=10`, {
      headers: auth
    });
    const records = Array.isArray(data?.records) ? data.records : [];
    if (records.length > 0) return records;
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
  throw new Error(`document ${docId} did not produce chunks`);
}

async function seedEvaluation(auth, marker) {
  const kbId = await api("/knowledge-base", {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      name: `rag-promotion-${marker}`,
      embeddingModel: "nomic-embed-text",
      collectionName: `ragpromotion${marker.replace(/\D/g, "").slice(-12)}`
    })
  });

  const documentText = `# Seahorse RAG Strategy Promotion ${marker}

Seahorse Agent uses nomic-embed-text for local embedding with dimension 768.
The RAG pipeline includes vector search, intent directed search, keyword search, RRF fusion, and rerank.
Production gates use retrieval evaluation, trace evidence, audit events, and cost checks.
Strategy promotion should mark a winning comparison as the recommended retrieval template.
`;
  const form = new FormData();
  form.append("file", new Blob([documentText], { type: "text/markdown" }), "rag-promotion.md");
  form.append("chunkSize", "256");
  form.append("chunkOverlap", "32");
  const uploaded = await api(`/knowledge-base/${encodeURIComponent(kbId)}/docs/upload`, {
    method: "POST",
    headers: auth,
    body: form
  });
  const docId = String(uploaded?.id || "");
  if (!docId) throw new Error("upload did not return document id");

  await api(`/knowledge-base/docs/${encodeURIComponent(docId)}/chunk`, {
    method: "POST",
    headers: auth
  });
  const chunks = await waitForChunks(auth, docId);
  const expectedChunkIds = chunks.slice(0, 3).map((chunk) => String(chunk.id));

  const dataset = await api(`/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets`, {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      datasetId: "",
      name: `rag-promotion-dataset-${marker}`,
      description: "Codex RAG strategy promotion smoke dataset",
      enabled: true,
      cases: [
        {
          caseId: `${marker}-case-1`,
          question: "What embedding model and dimension does Seahorse use?",
          expectedKbIds: [String(kbId)],
          expectedDocIds: [docId],
          expectedChunkIds,
          negativeChunkIds: [],
          tags: ["promotion", "embedding"],
          minRecall: 0.5
        },
        {
          caseId: `${marker}-case-2`,
          question: "What evidence is used for production gates?",
          expectedKbIds: [String(kbId)],
          expectedDocIds: [docId],
          expectedChunkIds,
          negativeChunkIds: [],
          tags: ["promotion", "gate"],
          minRecall: 0.5
        }
      ]
    })
  });
  const datasetId = String(dataset?.datasetId || "");
  if (!datasetId) throw new Error("dataset create did not return datasetId");

  const comparison = await api(`/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/compare`, {
    method: "POST",
    headers: auth,
    body: JSON.stringify({
      baselineStrategyName: "vector_only",
      topK: 5,
      strategies: [
        { strategyName: "vector_only", topK: 5, options: {} },
        { strategyName: "hybrid_rrf", topK: 5, options: {} }
      ]
    })
  });
  const winner = String(comparison?.winnerStrategyName || "");
  if (!winner) {
    throw new Error(`comparison did not return winnerStrategyName: ${JSON.stringify(comparison)}`);
  }

  const comparisons = await api(`/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/comparisons`, {
    headers: auth
  });
  const saved = (Array.isArray(comparisons) ? comparisons : [])
    .find((item) => item?.winnerStrategyName === winner && item?.datasetId === datasetId);
  const comparisonId = String(saved?.comparisonId || "");
  if (!comparisonId) {
    throw new Error(`comparison list did not include saved comparison: ${JSON.stringify(comparisons)}`);
  }

  return { kbId: String(kbId), docId, datasetId, comparisonId, winner };
}

async function loginUi(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.locator("#login-password").press("Enter");
  await page.waitForURL(/\/workspace(?:$|[/?#])/, { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
}

async function verifyAndPromoteInUi(page, seeded) {
  const promotionResponses = [];
  page.on("response", async (response) => {
    if (response.url().includes(`/comparisons/${seeded.comparisonId}/promote`)) {
      promotionResponses.push({
        status: response.status(),
        body: await response.text().catch(() => "")
      });
    }
  });

  await page.goto(`${baseUrl}/admin/rag-evaluation/${seeded.kbId}/${seeded.datasetId}`, { waitUntil: "domcontentloaded" });
  await page.getByRole("tab", { name: "策略对比" }).click();
  await page.waitForFunction(
    () => document.body.innerText.includes("vector_only"),
    null,
    { timeout: 20000 }
  );
  await page.waitForFunction(
    (winner) => document.body.innerText.includes(winner),
    seeded.winner,
    { timeout: 20000 }
  );

  const button = page.getByRole("button", { name: "推荐为线上策略" });
  await button.waitFor({ state: "visible", timeout: 15000 });
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes(`/comparisons/${seeded.comparisonId}/promote`),
    { timeout: 20000 }
  );
  await button.click();
  const promoteResponse = await responsePromise;
  if (promoteResponse.status() < 200 || promoteResponse.status() >= 300) {
    const body = await promoteResponse.text().catch(() => "");
    throw new Error(`promotion API returned HTTP ${promoteResponse.status()}: ${body.slice(0, 500)}`);
  }
  const response = promotionResponses.at(-1) || {
    status: promoteResponse.status(),
    body: await promoteResponse.text().catch(() => "")
  };
  if (!response || response.status < 200 || response.status >= 300) {
    throw new Error(`promotion response was not successful: ${JSON.stringify(response)}`);
  }
}

async function verifyPromotion(auth, seeded, marker) {
  const templates = await api(`/knowledge-base/${encodeURIComponent(seeded.kbId)}/retrieval-strategy-templates`, {
    headers: auth
  });
  const promoted = (Array.isArray(templates) ? templates : [])
    .find((template) => template?.templateKey === seeded.winner && template?.recommended === true);
  if (!promoted) {
    throw new Error(`recommended template ${seeded.winner} was not returned: ${JSON.stringify(templates)}`);
  }

  const templateRows = psql(`
select template_key, recommended, enabled
from t_retrieval_strategy_template
where kb_id = '${seeded.kbId}'
  and template_key = '${seeded.winner}'
  and deleted = 0
order by update_time desc
limit 1;
`);
  if (!templateRows[0]?.includes(`${seeded.winner}|1|1`)) {
    throw new Error(`recommended DB row missing or wrong: ${templateRows.join("\\n")}`);
  }

  const auditRows = psql(`
select audit_id, event_type, resource_type, resource_id, redacted_payload
from sa_audit_event
where event_type = 'RETRIEVAL_STRATEGY_PROMOTED'
  and resource_id = '${seeded.kbId}:${seeded.winner}'
  and redacted_payload like '%${seeded.comparisonId}%'
order by occurred_at desc
limit 1;
`);
  if (auditRows.length === 0) {
    throw new Error("promotion audit row was not found");
  }
  return {
    promoted,
    templateRow: templateRows[0],
    auditRow: auditRows[0]
  };
}

const marker = `CODX_RAG_PROMOTION_${Date.now()}`;
const token = await loginApi();
const auth = { Authorization: `Bearer ${token}` };
const seeded = await seedEvaluation(auth, marker);

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });

try {
  await loginUi(page);
  await verifyAndPromoteInUi(page, seeded);
  const screenshot = path.join(artifactDir, `rag-strategy-promotion-${marker}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  const verification = await verifyPromotion(auth, seeded, marker);
  console.log(JSON.stringify({
    ok: true,
    marker,
    kbId: seeded.kbId,
    docId: seeded.docId,
    datasetId: seeded.datasetId,
    comparisonId: seeded.comparisonId,
    winner: seeded.winner,
    templateRow: verification.templateRow,
    auditRow: verification.auditRow,
    screenshot
  }, null, 2));
} finally {
  await browser.close();
}
