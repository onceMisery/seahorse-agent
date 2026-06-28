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
const docPath = path.resolve(readArg(
  "--doc",
  process.env.E2E_RAG_DOC || path.join(
    repoRoot,
    "resources",
    "docs",
    "knowledge",
    "biz",
    "biz-oa",
    "OA系统数据安全规范文档.md"
  )
));
const marker = `CODX_RAG_UI_${Date.now()}`;
const startedAt = new Date().toISOString().replace(/[:.]/g, "-");

await fs.mkdir(artifactDir, { recursive: true });

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
    encoding: "utf8",
    shell: false
  });
  if (result.status !== 0) {
    throw new Error(`psql failed:\n${result.stdout}\n${result.stderr}`);
  }
  return result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

async function shot(page, name) {
  const file = path.join(artifactDir, `${startedAt}-${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function visibleTexts(page, selector) {
  return page.locator(selector).evaluateAll((nodes) =>
    nodes
      .map((node, index) => ({
        index,
        text: node.innerText || node.textContent || "",
        aria: node.getAttribute("aria-label"),
        id: node.id,
        className: String(node.className || "")
      }))
      .filter((item) => item.text.trim() || item.aria || item.id)
  );
}

async function chooseFirstRadixOption(page) {
  const option = page.locator('[role="option"]:not([aria-disabled="true"])').first();
  await option.waitFor({ state: "visible", timeout: 10000 });
  const text = (await option.innerText()).trim();
  await option.click();
  return text;
}

async function waitForDbValue(sql, predicate, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  let rows = [];
  while (Date.now() < deadline) {
    rows = psql(sql);
    const value = rows.join("\n");
    if (predicate(value, rows)) {
      return value;
    }
    await new Promise((resolve) => setTimeout(resolve, 2500));
  }
  throw new Error(`Timed out waiting for DB value. Last output:\n${rows.join("\n")}`);
}

async function loginUi(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.fill("#login-username", username);
  await page.fill("#login-password", password);
  await Promise.all([
    page.waitForURL(/\/workspace|\/chat|\/admin/, { timeout: 20000 }),
    page.locator('button[type="submit"]').click()
  ]);
}

async function createKnowledgeBase(page) {
  await page.goto(`${baseUrl}/admin/knowledge`, { waitUntil: "networkidle" });
  await shot(page, "knowledge-empty");
  console.log("knowledge buttons", JSON.stringify(await visibleTexts(page, "button"), null, 2));

  await page.locator(".admin-page-actions button").last().click();
  await page.locator('[role="dialog"]').waitFor({ state: "visible", timeout: 10000 });
  await page.locator('[role="dialog"] input').nth(0).fill(`OA安全规范${Date.now()}`);
  await page.locator('[role="dialog"] button[role="combobox"]').first().click();
  const embeddingLabel = await chooseFirstRadixOption(page);
  await page.locator('[role="dialog"] input').nth(1).fill(`oa${Date.now()}`);

  const [, createResponse] = await Promise.all([
    page.locator('[role="dialog"]').waitFor({ state: "hidden", timeout: 10000 }),
    page.waitForResponse(
      (response) => response.url().includes("/api/knowledge-base") && response.request().method() === "POST",
      { timeout: 20000 }
    ),
    page.locator('[role="dialog"] button[type="submit"]').click()
  ]);
  const createPayload = await createResponse.json().catch(() => null);
  if (!createResponse.ok() || createPayload?.code !== "0") {
    throw new Error(`Create knowledge base failed: HTTP ${createResponse.status()} ${JSON.stringify(createPayload)}`);
  }
  const kbId = await waitForDbValue(
    "select id from t_knowledge_base order by create_time desc limit 1;",
    (value) => value.trim().length > 0,
    20000
  );
  await shot(page, "knowledge-created");
  return { kbId, embeddingLabel };
}

async function uploadAndChunkDocument(page, kbId) {
  await page.goto(`${baseUrl}/admin/knowledge/${encodeURIComponent(kbId)}`, { waitUntil: "networkidle" });
  await shot(page, "documents-empty");
  console.log("document buttons", JSON.stringify(await visibleTexts(page, "button"), null, 2));

  await page.locator(".admin-page-actions button").last().click();
  await page.locator('[role="dialog"]').waitFor({ state: "visible", timeout: 10000 });
  await page.locator('[role="dialog"] input[type="file"]').setInputFiles(docPath);
  await shot(page, "document-file-selected");

  const [, uploadResponse] = await Promise.all([
    page.locator('[role="dialog"]').waitFor({ state: "hidden", timeout: 20000 }),
    page.waitForResponse(
      (response) => response.url().includes(`/api/knowledge-base/${kbId}/docs/upload`),
      { timeout: 60000 }
    ),
    page.locator('[role="dialog"] button[type="submit"]').click()
  ]);
  const uploadPayload = await uploadResponse.json().catch(() => null);
  if (!uploadResponse.ok() || uploadPayload?.code !== "0") {
    throw new Error(`Upload document failed: HTTP ${uploadResponse.status()} ${JSON.stringify(uploadPayload)}`);
  }
  const docId = await waitForDbValue(
    "select id from t_knowledge_document order by create_time desc limit 1;",
    (value) => value.trim().length > 0,
    20000
  );
  await shot(page, "document-uploaded");

  await page.reload({ waitUntil: "networkidle" });
  console.log("document row buttons", JSON.stringify(await visibleTexts(page, "tbody button"), null, 2));
  const chunkButton = page.locator("tbody tr").first().locator('button[title="分块"]').first();
  await chunkButton.click();
  await page.locator('[role="alertdialog"]').waitFor({ state: "visible", timeout: 10000 });
  await shot(page, "chunk-confirm-open");
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes(`/api/knowledge-base/docs/${docId}/chunk`),
      { timeout: 30000 }
    ),
    page.locator('[role="alertdialog"] button').last().click()
  ]);
  const chunkCount = await waitForDbValue(
    `select count(*) from t_knowledge_chunk where doc_id = '${docId}';`,
    (value) => Number(value.trim()) > 0,
    120000
  );
  const vectorCount = await waitForDbValue(
    `select count(*) from t_knowledge_vector where metadata->>'doc_id' = '${docId}';`,
    (value) => Number(value.trim()) > 0,
    120000
  );
  await page.reload({ waitUntil: "networkidle" });
  await shot(page, "chunk-success");
  return { docId, chunkCount: Number(chunkCount.trim()), vectorCount: Number(vectorCount.trim()) };
}

async function askRagQuestion(page) {
  await page.goto(`${baseUrl}/chat`, { waitUntil: "networkidle" });
  await shot(page, "chat-ready");
  const beforeAssistantId = psql("select coalesce(max(id), 0) from t_message where role = 'assistant';")[0] || "0";
  const textarea = page.locator("textarea").first();
  await textarea.fill("请只基于知识库回答：OA 系统中 L4 高敏数据需要哪些控制措施？规范明确写出的 RPO 和 RTO 指标分别是多少？");
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/api/rag/v3/chat"), { timeout: 30000 }).catch(() => null),
    page.locator('button[aria-label*="发送"], button[aria-label*="鍙"]').last().click()
  ]);
  const answer = await waitForDbValue(
    `select content from t_message where role = 'assistant' and id > ${beforeAssistantId} order by create_time desc limit 1;`,
    (value) => value.trim().length > 80,
    240000
  );
  const answerSnippet = answer.replace(/\s+/g, " ").slice(0, 24);
  await page.waitForFunction(
    (snippet) => document.body.innerText.replace(/\s+/g, " ").includes(snippet),
    answerSnippet,
    { timeout: 60000 }
  ).catch(() => null);
  await shot(page, "rag-answer-l4-rpo-rto");
  return answer;
}

function assertRagFacts(answer) {
  const facts = {
    hasL4: /L4|高敏|楂樻晱/.test(answer),
    hasEncrypt: /加密|鍔犲瘑|encrypt/i.test(answer),
    hasRpo10: /RPO[\s\S]{0,80}(10|十|10\s*min|10\s*分钟|10\s*鍒嗛挓)/i.test(answer),
    hasRto2h: /RTO[\s\S]{0,80}(2|两|二|2\s*h|2\s*小时|2\s*小時|2\s*灏忔椂)/i.test(answer)
  };
  if (!Object.values(facts).every(Boolean)) {
    throw new Error(`RAG answer did not contain all expected facts: ${JSON.stringify(facts)}\n${answer.slice(-3000)}`);
  }
  return facts;
}

function verifyTraceHit() {
  const rows = psql(`
select node_name, extra_data::jsonb ->> 'hitCount'
from t_rag_trace_node
where node_name like '%VectorGlobalSearch'
order by create_time desc
limit 1;
`);
  const row = rows[0] || "";
  const hitCount = Number(row.split("|")[1] || 0);
  if (hitCount <= 0) {
    throw new Error(`VectorGlobalSearch did not return hits: ${row}`);
  }
  return row;
}

const browser = await chromium.launch({ headless });
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });
page.on("console", (message) => {
  const text = message.text();
  if (/error|failed|exception/i.test(text)) {
    console.log(`[browser:${message.type()}] ${text}`);
  }
});

try {
  await loginUi(page);
  await shot(page, "login-success");
  const kb = await createKnowledgeBase(page);
  const doc = await uploadAndChunkDocument(page, kb.kbId);
  const answer = await askRagQuestion(page);
  const facts = assertRagFacts(answer);
  const traceRow = verifyTraceHit();
  console.log(JSON.stringify({
    ok: true,
    marker,
    kbId: kb.kbId,
    embeddingLabel: kb.embeddingLabel,
    docId: doc.docId,
    chunkCount: doc.chunkCount,
    vectorCount: doc.vectorCount,
    traceRow,
    facts,
    artifactDir
  }, null, 2));
} finally {
  await browser.close();
}
