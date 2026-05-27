# Phase 2：研究型 Web Agent

## 1. 阶段目标

在 Phase 1 的任务闭环上，补齐 C 端 Web 场景最核心的“研究型 Agent”能力：自动拆解问题、搜索和抓取公开 Web 来源、整理证据、生成带引用的报告产物。

本阶段借鉴 Deer Flow 的深度研究体验，但所有工具都运行在服务端受控 adapter 内，不引入本地浏览器自动化、本地文件系统或 shell。

## 2. 当前基础

已具备：

- RAG、Knowledge、ContextPack、Trace、Artifact 最小闭环在 Phase 1 后可用。
- Tool Gateway、Policy、Audit 可作为工具治理入口。
- 前端已经有流式消息、任务时间线、来源卡片和产物面板。

主要缺口：

- 没有面向 Web research 的 search/fetch/read adapter。
- 没有标准研究任务步骤：plan、search、read、synthesize、write、verify。
- 没有网页 prompt injection 防护边界。
- 没有研究报告产物和引用完整性校验。

## 3. 范围

### 3.1 本阶段做

1. 服务端 Web 搜索 adapter。
2. 服务端网页抓取/正文抽取 adapter。
3. 研究任务 step pipeline。
4. 来源可信度和引用完整性检查。
5. 报告 Artifact。
6. 研究类任务模板。

### 3.2 本阶段不做

- 不做本地浏览器自动化。
- 不运行用户脚本。
- 不开放用户自定义搜索 provider 凭据。
- 不做复杂多 Agent mesh。
- 不做跨站登录或绕过 paywall。

## 4. 领域设计

### 4.1 ResearchTaskProfile

建议作为 AgentTemplate 或 task template 配置对象，不新增复杂 DSL。

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `profileId` | string | 模板 ID |
| `name` | string | 展示名 |
| `defaultDepth` | enum | `QUICK`、`STANDARD`、`DEEP` |
| `maxSearchQueries` | int | 最大搜索查询数 |
| `maxSources` | int | 最大来源数 |
| `allowedSourceTypes` | set | `WEB`、`KNOWLEDGE_BASE`、`USER_UPLOAD` |
| `outputArtifactType` | enum | `MARKDOWN_REPORT`、`BRIEFING`、`SOURCE_DIGEST` |
| `costLimitPolicyId` | string | 成本策略 |

### 4.2 ResearchStepType

新增 enum：

```text
PLAN
SEARCH
FETCH
READ
EXTRACT_EVIDENCE
SYNTHESIZE
WRITE_REPORT
VERIFY_CITATIONS
```

这些 step 映射到 AgentStep，不引入工作流引擎。

### 4.3 WebSource

Web 来源作为 ContextItem 的来源之一，同时保留独立证据对象。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sourceId` | string | 来源 ID |
| `runId` | string | 所属任务 |
| `url` | string | 原始 URL |
| `canonicalUrl` | string | 规范化 URL |
| `title` | string | 标题 |
| `snippet` | string | 搜索摘要 |
| `retrievedAt` | instant | 抓取时间 |
| `sourceTrustLevel` | enum | `UNKNOWN`、`LOW`、`MEDIUM`、`HIGH` |
| `contentHash` | string | 内容 hash |
| `extractionStatus` | enum | `PENDING`、`EXTRACTED`、`FAILED`、`BLOCKED` |

### 4.4 EvidenceItem

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `evidenceId` | string | 证据 ID |
| `sourceId` | string | 来源 |
| `claim` | string | 支撑的结论 |
| `quotePreview` | string | 短摘录，不保存超长原文 |
| `summary` | string | 摘要 |
| `confidence` | double | 置信度 |
| `citationIndex` | int | 报告引用序号 |

## 5. Tool Adapter 设计

### 5.1 WebSearchToolPortAdapter

职责：

- 接收 query、locale、timeRange、maxResults。
- 调用服务端配置的搜索 provider。
- 结果写入 ToolInvocationAudit。
- 转成 WebSource 候选。

输入：

```json
{
  "query": "AI agent web research architecture",
  "locale": "zh-CN",
  "timeRange": "MONTH",
  "maxResults": 10
}
```

输出：

```json
{
  "sources": [
    {
      "title": "Example",
      "url": "https://example.com",
      "snippet": "..."
    }
  ]
}
```

### 5.2 WebFetchToolPortAdapter

职责：

- 抓取 URL。
- 做 MIME、大小、robots/allowlist 策略检查。
- 提取正文。
- 标记网页内容为 `UNTRUSTED_EXTERNAL_CONTENT`。
- 不把网页中的指令作为系统或工具策略执行。

安全规则：

- 默认只允许 http/https。
- 禁止访问内网 IP、localhost、metadata endpoint。
- 单页大小和总抓取量受 quota 限制。
- 抓取失败必须返回结构化错误，不抛出未脱敏异常。

### 5.3 EvidenceExtractor

可以先作为应用服务，不做模型专用端口。

职责：

- 从抓取正文提取关键事实和短摘录。
- 去重相同 URL 和相同内容 hash。
- 写入 ContextPack item。
- 生成 citationJson。

## 6. 应用服务设计

### 6.1 ResearchPlannerService

输入：用户问题、任务模板、上下文。

输出：研究计划。

计划字段：

- 研究目标。
- 关键子问题。
- 搜索 query 列表。
- 预期输出结构。
- 风险提示。

### 6.2 ResearchRunOrchestrator

不引入独立 workflow engine，直接编排 bounded steps：

```text
AgentRun
  -> PLAN
  -> SEARCH
  -> FETCH
  -> READ / EXTRACT_EVIDENCE
  -> SYNTHESIZE
  -> WRITE_REPORT
  -> VERIFY_CITATIONS
  -> AgentArtifact
```

每个 step：

- 写 AgentStep。
- 发 SSE timeline event。
- 写 trace。
- 失败时标记是否可重试。

### 6.3 CitationVerifier

职责：

- 检查报告中的引用编号是否存在。
- 检查关键结论至少有一个来源支撑。
- 检查来源是否被 policy 标记为 blocked。
- 输出结构化校验结果。

## 7. API 设计

### 7.1 发起研究任务

可以复用聊天接口，通过 task template 参数触发：

```http
GET /rag/v3/chat?question=...&taskTemplate=DEEP_RESEARCH
```

后续也可新增：

```http
POST /api/research-runs
```

第一版建议复用聊天接口，避免新入口膨胀。

### 7.2 查询研究来源

```http
GET /api/agent-runs/{runId}/sources
GET /api/agent-runs/{runId}/evidence
```

返回来源和证据摘要，不能返回超长抓取正文。

### 7.3 报告产物

复用 Phase 1：

```http
GET /api/agent-runs/{runId}/artifacts
GET /api/agent-artifacts/{artifactId}
```

## 8. 前端设计

### 8.1 任务入口

在聊天输入附近提供任务模板入口：

- 快速回答。
- 深度研究。
- 网页总结。
- 对比分析。

选中深度研究后，前端只传模板 ID，不暴露内部 step。

### 8.2 研究时间线

时间线文案建议：

- 正在拆解问题。
- 正在搜索资料。
- 正在阅读来源。
- 正在整理证据。
- 正在生成报告。
- 正在校验引用。

### 8.3 来源面板

字段：

- 标题。
- URL 域名。
- 抓取时间。
- 来源可信度。
- 支撑的结论。
- 引用编号。

## 9. 测试计划

后端：

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am test
```

前端：

```powershell
npm run build
```

建议新增测试：

- `WebSearchToolPortAdapterTests`
- `WebFetchSafetyPolicyTests`
- `ResearchPlannerServiceTests`
- `CitationVerifierTests`
- `ResearchRunOrchestratorTests`
- `ResearchSourcePanel.test.tsx`

安全测试：

- URL 指向 `localhost` 被拒绝。
- 网页正文包含“忽略之前指令”不会影响系统 prompt。
- 抓取超大页面被截断或拒绝。
- 报告引用缺失时校验失败。

## 10. 退出标准

1. 用户能发起深度研究模板任务。
2. 系统能完成搜索、抓取、证据提取、报告生成。
3. 报告有可点击引用。
4. 每个引用能追溯到 WebSource 和 ContextItem。
5. 外部网页内容被标记为不可信资料。
6. 研究任务失败时能显示失败 step 和重试入口。

## 实现指南

### 代码落点

| 类型 | 位置 |
|------|------|
| 领域对象 | `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/research/` |
| 应用服务 | `seahorse-agent-kernel/src/main/java/.../kernel/application/agent/research/` |
| Inbound port | `seahorse-agent-kernel/src/main/java/.../ports/inbound/agent/` |
| Outbound port | `seahorse-agent-kernel/src/main/java/.../ports/outbound/web/`（已有 WebSearchPort/WebFetchPort） |
| JDBC adapter | `seahorse-agent-adapter-repository-jdbc/src/main/java/.../jdbc/` |
| Web controller | `seahorse-agent-adapter-web/src/main/java/.../web/` |

### Step C.1：DurableTaskQueuePort

新增文件：`seahorse-agent-kernel/src/main/java/.../ports/outbound/agent/DurableTaskQueuePort.java`

```java
public interface DurableTaskQueuePort {
    void enqueue(DurableTask task);
    Optional<DurableTask> claimNext(String workerId);
    void ack(String taskId);
    void retry(String taskId, Instant retryAt, String reason);
    void fail(String taskId, String reason);
    void cancel(String runId);
}

public record DurableTask(
    String taskId,
    String runId,
    String stepType,
    int attemptCount,
    Instant createdAt,
    Instant claimedAt,
    String payloadJson
) {}
```

JDBC adapter：`JdbcDurableTaskQueueAdapter.java`

SQL 表：
```sql
CREATE TABLE durable_task_queue (
    task_id       VARCHAR(64) PRIMARY KEY,
    run_id        VARCHAR(64) NOT NULL,
    step_type     VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    worker_id     VARCHAR(64),
    payload_json  TEXT,
    last_error    TEXT,
    retry_at      TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    claimed_at    TIMESTAMP,
    completed_at  TIMESTAMP
);
CREATE INDEX idx_dtq_status_retry ON durable_task_queue (status, retry_at);
CREATE INDEX idx_dtq_run_id ON durable_task_queue (run_id);
```

claimNext 实现要点：`SELECT ... WHERE status = 'PENDING' AND (retry_at IS NULL OR retry_at <= NOW()) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1`

### Step C.2：ResearchStepType 和 ResearchTaskProfile

```java
// ResearchStepType.java
public enum ResearchStepType {
    PLAN,
    SEARCH,
    FETCH,
    EXTRACT_EVIDENCE,
    SYNTHESIZE,
    WRITE_REPORT,
    VERIFY_CITATIONS
}

// ResearchTaskProfile.java
public record ResearchTaskProfile(
    String profileId,
    String name,
    int defaultDepth,
    int maxSearchQueries,
    int maxSources,
    List<String> allowedSourceTypes,
    String outputArtifactType,
    String costLimitPolicyId
) {}
```

### Step C.3：ResearchRunOrchestrator

```java
@Service
public class ResearchRunOrchestrator {
    private final DurableTaskQueuePort taskQueue;
    private final ResearchPlannerService planner;
    private final WebSearchPort webSearch;
    private final WebFetchPort webFetch;
    private final AgentRunEventBufferPort eventBuffer;
    
    // 启动研究任务：创建 AgentRun，入队第一步
    public String startResearch(String userId, ResearchTaskProfile profile, String query) {
        var run = AgentRun.create(userId, "research", profile.profileId());
        agentRunRepository.save(run);
        
        var task = new DurableTask(
            UUID.randomUUID().toString(), run.runId(),
            ResearchStepType.PLAN.name(), 0, Instant.now(), null,
            objectMapper.writeValueAsString(Map.of("query", query, "profile", profile))
        );
        taskQueue.enqueue(task);
        return run.runId();
    }
    
    // Worker 轮询消费
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        taskQueue.claimNext(workerId).ifPresent(this::executeTask);
    }
    
    private void executeTask(DurableTask task) {
        var stepType = ResearchStepType.valueOf(task.stepType());
        try {
            // 写入 AgentStep（开始）
            emitStepStarted(task.runId(), stepType);
            
            var result = switch (stepType) {
                case PLAN -> executePlan(task);
                case SEARCH -> executeSearch(task);
                case FETCH -> executeFetch(task);
                case EXTRACT_EVIDENCE -> executeExtract(task);
                case SYNTHESIZE -> executeSynthesize(task);
                case WRITE_REPORT -> executeWriteReport(task);
                case VERIFY_CITATIONS -> executeVerify(task);
            };
            
            // 写入 AgentStep（完成）
            emitStepFinished(task.runId(), stepType);
            taskQueue.ack(task.taskId());
            
            // 入队下一步
            enqueueNextStep(task.runId(), stepType, result);
        } catch (RetryableException e) {
            taskQueue.retry(task.taskId(), 
                Instant.now().plusSeconds(30 * (task.attemptCount() + 1)), 
                e.getMessage());
        } catch (Exception e) {
            taskQueue.fail(task.taskId(), e.getMessage());
            emitRecoverableError(task.runId(), stepType, e.getMessage());
        }
    }
    
    private void enqueueNextStep(String runId, ResearchStepType current, Object result) {
        var next = switch (current) {
            case PLAN -> ResearchStepType.SEARCH;
            case SEARCH -> ResearchStepType.FETCH;
            case FETCH -> ResearchStepType.EXTRACT_EVIDENCE;
            case EXTRACT_EVIDENCE -> ResearchStepType.SYNTHESIZE;
            case SYNTHESIZE -> ResearchStepType.WRITE_REPORT;
            case WRITE_REPORT -> ResearchStepType.VERIFY_CITATIONS;
            case VERIFY_CITATIONS -> null; // 完成
        };
        if (next != null) {
            taskQueue.enqueue(new DurableTask(..., next.name(), ...));
        } else {
            emitRunFinished(runId);
        }
    }
}
```

### Step C.4：WebSource 和 EvidenceItem

```java
public record WebSource(
    String sourceId,
    String runId,
    String url,
    String title,
    String snippet,
    Instant retrievedAt,
    SourceTrustLevel trustLevel,
    String contentHash,
    ExtractionStatus extractionStatus
) {}

public record EvidenceItem(
    String evidenceId,
    String sourceId,
    String claim,
    String quotePreview,
    String summary,
    double confidence,
    int citationIndex
) {}
```

### Step C.5：CitationVerifier

```java
@Service
public class CitationVerifier {
    public VerificationResult verify(AgentArtifact report, List<EvidenceItem> evidence) {
        // 1. 解析报告中的引用标号 [1], [2], ...
        // 2. 检查每个引用是否有对应 EvidenceItem
        // 3. 检查 EvidenceItem 的 source 是否可达
        // 4. 返回 verified/unverified/missing 分类
    }
}
```

### Step C.6：前端研究时间线

修改文件：`frontend/src/components/chat/AgentTracePanel.tsx`

增加研究步骤的中文标签映射：
```typescript
const RESEARCH_STEP_LABELS: Record<string, string> = {
  PLAN: '规划研究方向',
  SEARCH: '搜索相关资料',
  FETCH: '抓取网页内容',
  EXTRACT_EVIDENCE: '提取关键证据',
  SYNTHESIZE: '综合分析',
  WRITE_REPORT: '撰写报告',
  VERIFY_CITATIONS: '验证引用',
};
```

### 测试清单

必须覆盖：
- `ResearchRunOrchestratorTests` — 完整步骤流转
- `DurableTaskQueuePortTests` — enqueue/claim/ack/retry/fail/cancel
- `WebSearchToolPortAdapterTests`（已有）
- `WebFetchSafetyPolicyTests`（已有）— localhost/内网 URL 被拒绝
- `CitationVerifierTests` — 引用缺失时失败
- `ResearchSourcePanel.test.tsx` — 前端来源面板渲染
