# SaaS MVP 技术方案 01 — 多租户隔离（Multi-Tenancy Isolation）

> 状态：已定稿 ｜ 作者：架构组 ｜ 日期：2026-06-04 ｜ 定位：SaaS 改造**地基**方案
>
> 本方案定义全平台**租户上下文契约（TenantContext）**与**数据隔离机制**。后续 `02-用户与组织体系`、`03-计费与配额`、`04-安全加固` 均依赖本方案定义的契约，引用时以本文 §3.1 的接口签名为准。

---

## 1. 目标与范围

### 1.1 要做什么（In Scope）

| 编号 | 目标 | 优先级 |
|------|------|--------|
| G1 | 定义统一的 `TenantContext`（ThreadLocal）契约，作为全链路租户身份的唯一来源 | P0 |
| G2 | 请求入口通过 `TenantInterceptor` 从 Sa-Token 会话解析 `tenantId` 入栈、请求结束清理 | P0 |
| G3 | 登录时将 `tenantId` 写入 Sa-Token 会话；鉴权链路可随时读取当前租户 | P0 |
| G4 | 审计全部 80 个 JDBC 适配器，确保每条涉及租户数据的查询都带 `tenant_id` 过滤 | P0 |
| G5 | 为 18 张缺列的 P0 核心业务表补 `tenant_id`（DDL + 幂等 schema 升级 + 回填 default） | P0 |
| G6 | 引入 PostgreSQL RLS（行级安全）作为应用层过滤之外的**第二道防线** | P1 |
| G7 | 建立跨租户隔离自动化测试（构造租户 A/B，断言 A 查不到 B） | P0 |

### 1.2 明确不做什么（Out of Scope）

- **不做**租户的注册/开通/计费流程（属 `02`/`03` 方案）。本方案只消费 `tenantId`，不负责其生命周期。
- **不做**物理隔离（独立库/独立 schema per tenant）。MVP 采用**共享库 + 共享 schema + 行级 `tenant_id` 列**的逻辑隔离模型。物理隔离留待企业版。
- **不做**向量库（Milvus）/对象存储（S3/MinIO）/搜索（ES）的租户分区改造——这些适配器已通过 `collection_name` / `key prefix` 隐式隔离，单列出 `05-非关系存储租户化` 方案。本方案仅约束**关系型数据（PostgreSQL）**。
- **不改**现有 `DEFAULT_TENANT_ID = "default"` 的存量数据语义；存量单租户数据统一归属 `default` 租户，平滑过渡。

---

## 2. 现状（代码级）

> 以下结论均经源码核实，文件路径与行号真实可定位。

### 2.1 已有基础：租户字段“半铺开”

- **canonical 常量**：`AgentDefinition.DEFAULT_TENANT_ID = "default"`，定义于 `seahorse-agent-kernel/.../kernel/domain/agent/definition/AgentDefinition.java:54`。领域对象 `AgentDefinition` 构造时通过 `defaultTenant()`（`AgentDefinition.java:148`）对空值兜底为 `"default"`。
- **常量散落**：`"default"` 字面量以 `private static final String DEFAULT_TENANT_ID = "default"` 形式在 **21 处**重复定义（kernel application/memory、ports/outbound/memory、adapter-cache-redis、adapter-repository-jdbc 等），无单一出处。**风险点**：前端 `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx:104` 使用的却是 `"tenant-default"`，与后端 `"default"` **取值不一致**，属潜在缺陷，本方案统一收口。
- **表级覆盖率**：`resources/database/seahorse_init.sql` 共 **96 张表**，其中 **46 张已含 `tenant_id`**（多为记忆体系 `t_*_memory`、`t_memory_*` 与 Agent 体系 `sa_agent_*`），**50 张缺失**。已有字段统一为 `tenant_id VARCHAR(64) DEFAULT 'default'`（部分 `NOT NULL`），见 `seahorse_init.sql:443`（`t_short_term_memory`）等。

### 2.2 缺口一：无租户上下文设施

- **无** `TenantContext` / `TenantContextHolder` / ThreadLocal 租户持有者（全仓 `find` 无匹配类）。
- **无** `TenantInterceptor`。请求入口仅有 `SeahorseSecurityWebMvcConfiguration`（`seahorse-agent-adapter-web/.../web/SeahorseSecurityWebMvcConfiguration.java:38`），其 `addInterceptors()`（`:72`）只注册了 Sa-Token 的 `SaInterceptor` 做登录/角色校验，**未做任何租户解析**。
- **`CurrentUser` 不含 tenantId**：`seahorse-agent-kernel/.../ports/outbound/auth/CurrentUser.java:20` 当前为 `record CurrentUser(Long userId, String username, String role, String avatar)`。
- **`UserRecord` 不含 tenantId**：`ports/outbound/auth/UserRecord.java`，且 `t_user` 表无 `tenant_id`（`seahorse_init.sql:12`）。即当前**用户与租户无归属关系**。

### 2.3 缺口二：登录链路不携带 tenantId

- 登录入口 `KernelAuthService.login()`（`kernel/application/auth/KernelAuthService.java:47`）校验密码后调 `tokenServicePort.login(String.valueOf(user.id()))`（`:62`），仅传 userId。
- `SaTokenServiceAdapter.login()`（`adapter-web/.../web/SaTokenServiceAdapter.java:26`）实现为 `StpUtil.login(userId)`，**无任何 tenant claim 写入**。
- `SaTokenCurrentUserAdapter.currentUser()`（`adapter-web/.../web/SaTokenCurrentUserAdapter.java:40`）通过 `StpUtil.getLoginIdAsString()` 拿 userId 后回查用户，**未读取 tenant**。

### 2.4 缺口三：Repository 租户过滤不完整

全仓 **80 个 `Jdbc*Adapter`**，仅 **41 个** SQL 中出现 `tenant_id`，**39 个完全无 `tenant_id` 过滤**。其中混杂两类：

- **真漏洞（核心业务表，必须修）**：`JdbcUserRepositoryAdapter`、`JdbcConversationRepositoryAdapter`、`JdbcKnowledgeBaseRepositoryAdapter`、`JdbcKnowledgeDocumentRepositoryAdapter`、`JdbcKnowledgeChunkRepositoryAdapter`、`JdbcMessageFeedbackRepositoryAdapter`、`JdbcIntentTreeRepositoryAdapter`、`JdbcRagTraceRepositoryAdapter` 等。
- **现有过滤靠手工传参，存在绕过**：即便已含 `tenant_id` 的 `JdbcAgentDefinitionRepositoryAdapter`，其 `page()`（`JdbcAgentDefinitionRepositoryAdapter.java:162`）通过**方法参数** `String tenantId` 显式带过滤；但 `findById()`（`:154`）走的 `SQL_FIND_DEFINITION`（`:66`）**WHERE 子句只有 `agent_id = ?`，无 tenant 过滤**——任意租户只要猜到 agentId 即可跨租户读取。这是“靠调用方自觉”的脆弱模式，本方案改为**从 TenantContext 强制注入**。

典型现状 SQL（`JdbcConversationRepositoryAdapter.java:44`）：
```sql
SELECT conversation_id, title, last_time
FROM t_conversation
WHERE user_id = ? AND deleted = 0      -- 仅按 user_id，无 tenant_id
```

### 2.5 已有可复用资产

- **幂等 schema 升级框架**：`JdbcChatSchemaUpgrade`（`adapter-repository-jdbc/.../jdbc/JdbcChatSchemaUpgrade.java:30`）。`upgrade()`（`:40`）编排全部升级；`addColumnIfMissing()`（`:696`）、`tableExists()`（`:715`）、`columnExists()`（`:727`）基于 `information_schema` 实现幂等；`executePostgresPartialIndexOrPlainIndex()`（`:615`）兼容 PostgreSQL 与嵌入式库（测试用 H2）。**本方案的加列升级直接复用该类。**
- **升级注册点**：`SeahorseAgentMemoryRepositoryAutoConfiguration.seahorseJdbcChatSchemaUpgrade()`（`spring-boot-starter/.../spring/SeahorseAgentMemoryRepositoryAutoConfiguration.java:94`），`@ConditionalOnBean(DataSource.class)` + `@AutoConfigureAfter(DataSourceAutoConfiguration.class)`，启动时调 `upgrade()`，失败仅告警不阻断。
- **Sa-Token 框架完整**，会话扩展数据 API（`StpUtil.getSession().set(...)` / `getTokenSession()`）开箱可用。

---

## 3. 技术方案

### 3.0 整体数据流

```
┌─────────────┐   ① 登录(username/pwd)
│  Browser    │ ───────────────────────────────►┐
└─────────────┘                                  │
      ▲                                           ▼
      │                          ┌────────────────────────────────────┐
      │ ⑦ JSON(code/data)        │ SeahorseAuthController → KernelAuth │
      │                          │ Service.login()                    │
      │                          │   tokenServicePort.login(uid,tid)  │  ← tenantId 来自 t_user.tenant_id
      │                          │   StpUtil.login(uid);              │
      │                          │   StpUtil.getSession()             │
      │                          │       .set("tenantId", tid)        │
      │                          └────────────────────────────────────┘
      │
      │  ② 业务请求(携带 Sa-Token)
      │     ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Servlet 拦截器链（注册于 SeahorseSecurityWebMvcConfiguration）        │
│                                                                        │
│   [order=0] TenantInterceptor.preHandle()  ←── 本方案新增              │
│        tid = StpUtil.getSession().getString("tenantId")               │
│        TenantContext.set(tid)               ③ 入栈 ThreadLocal         │
│   [order=1] SaInterceptor   (登录/角色校验，现有)                       │
│                  │                                                      │
│                  ▼                                                      │
│   Controller(ObjectProvider 懒加载) → Kernel Service                    │
│                  │                                                      │
│                  ▼                                                      │
│   Jdbc*Adapter   ④ SQL 自动拼接 WHERE tenant_id = ?                     │
│        String tid = TenantContext.require();                          │
│                  │                                                      │
│                  ▼                                                      │
│   ┌────────────────────────────────────────────────────────┐         │
│   │ PostgreSQL                                               │         │
│   │   ⑤ SET app.tenant_id = '<tid>'  (连接级 GUC)            │         │
│   │   ⑥ RLS POLICY: tenant_id = current_setting('app...')   │  第二道防线
│   └────────────────────────────────────────────────────────┘         │
│   TenantInterceptor.afterCompletion() → TenantContext.clear() 清栈     │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.1 契约：TenantContext（本方案核心，供其他方案引用）

**模块归属**：放在 `seahorse-agent-kernel`（所有 adapter 均依赖 kernel，唯一对所有层可见的位置）。包路径 `com.miracle.ai.seahorse.agent.kernel.tenant`。纯 JDK `ThreadLocal`，无 Spring/框架依赖，符合六边形“领域核心不依赖外部”原则。

**契约签名（其他方案以此为准，勿改名）**：

```java
package com.miracle.ai.seahorse.agent.kernel.tenant;

public final class TenantContext {
    public static final String DEFAULT_TENANT_ID = "default";   // 收口：全平台唯一出处

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    /** 读取当前租户；未设置返回 null（调用方需自行决定兜底或抛错）。 */
    public static String getTenantId() { return HOLDER.get(); }

    /** 入栈当前租户；空白值归一化为 DEFAULT_TENANT_ID。 */
    public static void setTenantId(String tenantId) {
        HOLDER.set(tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim());
    }

    /** 读取当前租户；未设置抛 IllegalStateException（业务 SQL 强约束入口）。 */
    public static String require() {
        String t = HOLDER.get();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("租户上下文缺失：当前线程未绑定 tenantId");
        }
        return t;
    }

    /** 请求结束/线程归还前必须调用，防止线程池租户串号。 */
    public static void clear() { HOLDER.remove(); }
}
```

> **契约约定（强制）**
> 1. `clear()` 必须在请求结束时调用（`afterCompletion`），否则线程池复用会导致**租户串号**——这是逻辑隔离模型最危险的 bug。
> 2. 业务数据查询一律用 `require()`，宁可抛错也不可静默用 `default` 兜底导致越权。
> 3. 仅明确的系统级/后台任务（无请求上下文）允许显式 `setTenantId(...)` 指定租户后执行，执行完 `clear()`。
> 4. 异步线程（`@Async`、MQ 消费、`DispatcherType.ASYNC`）**不自动继承** ThreadLocal，需在任务投递时显式捕获并在执行端重放（见 §9 风险 R3）。

### 3.2 TenantInterceptor（请求入口入栈/清理）

**模块归属**：`seahorse-agent-adapter-web`，与 `SeahorseSecurityWebMvcConfiguration` 同包。

```java
package com.miracle.ai.seahorse.agent.adapters.web;

import cn.dev33.satoken.stp.StpUtil;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 已登录才解析；未登录请求由 SaInterceptor 拦截，公共路径无需租户。
        if (StpUtil.isLogin()) {
            Object tid = StpUtil.getSession().get("tenantId");
            TenantContext.setTenantId(tid == null ? null : tid.toString());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        TenantContext.clear();   // 必须清栈
    }
}
```

**注册**：在 `SeahorseSecurityWebMvcConfiguration.addInterceptors()`（`:72`）中**先于** `SaInterceptor` 注册（`order` 小者先执行 `preHandle`，`afterCompletion` 逆序，保证清栈在最外层）：

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    // 新增：租户上下文最外层（order=0），afterCompletion 最后执行，确保清栈兜底
    registry.addInterceptor(new TenantInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns("/", "/index.html", "/login", "/features",
                    "/api/features", "/auth/**", "/error", "/assets/**", "/prototype/**")
            .order(0);

    registry.addInterceptor(new SaInterceptor(handler -> { /* 现有逻辑不变 */ }) { /* ... */ })
            .addPathPatterns("/**")
            .excludePathPatterns(/* 现有 */)
            .order(1);
}
```

> **决策**：登录路径 `/auth/**` 在排除列表中，登录请求本身无租户上下文（此时尚未确定租户），登录逻辑在 §3.3 内部直接读 `t_user.tenant_id` 写会话，不依赖拦截器。

### 3.3 登录链路写入 tenantId（Sa-Token 会话）

**决策：tenantId 存 Sa-Token Session，不放进 Token 本身。** 理由：Sa-Token 的 Session 服务端持久化（Redis/内存），改租户归属即时生效、可强制下线；若塞进无状态 JWT claim 则签发后无法吊销，与后续 `04-安全加固` 的会话治理冲突。

改造点（三处，均向后兼容）：

1. **`t_user` 加 `tenant_id`**（§4），`UserRecord` 增 `tenantId` 字段，`JdbcUserRepositoryAdapter` 查询带出。
2. **`TokenServicePort.login` 增加 tenantId 参数**（`ports/outbound/auth/TokenServicePort.java`）：
   ```java
   String login(String userId, String tenantId);
   ```
3. **`SaTokenServiceAdapter` 写会话**（`SaTokenServiceAdapter.java:26`）：
   ```java
   @Override
   public String login(String userId, String tenantId) {
       StpUtil.login(userId);
       StpUtil.getSession().set("tenantId",
               tenantId == null || tenantId.isBlank() ? TenantContext.DEFAULT_TENANT_ID : tenantId);
       return StpUtil.getTokenValue();
   }
   ```
4. **`KernelAuthService.login`**（`KernelAuthService.java:62`）传入用户租户：
   ```java
   String token = tokenServicePort.login(String.valueOf(user.id()), user.tenantId());
   ```
5. **`CurrentUser` / `SaTokenCurrentUserAdapter`** 增 tenantId（`CurrentUser.java:20`、`SaTokenCurrentUserAdapter.java:40`），使 kernel 业务可经 `CurrentUserPort` 拿到租户，与 `TenantContext` 双通道一致（`TenantContext` 供 JDBC 底层无侵入读取，`CurrentUser` 供应用层显式使用）。

### 3.4 Repository 租户过滤改造（决策：集中式注入，非逐方法传参）

**决策**：放弃“每个 Repository 方法新增 `String tenantId` 形参”的侵入式做法（既改接口又靠调用方自觉，2.4 已证脆弱）。改为**适配器内部从 `TenantContext.require()` 取租户**，SQL 模板内置 `tenant_id = ?`。好处：① kernel 端口签名基本不变，改动收敛在 adapter-repository-jdbc；② 无法被调用方“忘记传”而绕过。

详见 §5.3 改造前/后样例。

### 3.5 PostgreSQL RLS（第二道防线）

**决策**：RLS 作为**纵深防御**，不替代应用层过滤。即使某条 SQL 漏拼 `tenant_id`，数据库层 POLICY 仍兜底拦截。代价是每个数据库连接在使用前必须 `SET app.tenant_id`。

**连接级 GUC 注入**：HikariCP 连接从池中借出后、执行业务 SQL 前，需执行 `SET app.tenant_id = '<tid>'`。采用 **AOP 环绕事务边界** 或 **`DataSource` 包装器** 在每次取连接时注入（见 §5.4）。用 `SET`（会话级）而非 `SET LOCAL`（事务级）配合连接归还时 reset，避免连接复用残留。

---

## 4. 数据模型 / DDL

### 4.1 缺 tenant_id 表清单（核实结论）

`seahorse_init.sql` 96 张表中 **50 张缺 `tenant_id`**。按隔离必要性分三级：

#### P0 — 18 张核心业务表（必须直接加列，本方案落地）

| # | 表名 | 说明 | 现有归属键 |
|---|------|------|-----------|
| 1 | `t_user` | 用户表（租户成员归属根） | username |
| 2 | `t_conversation` | 会话 | user_id |
| 3 | `t_conversation_summary` | 会话摘要 | user_id |
| 4 | `t_message` | 消息 | user_id |
| 5 | `sa_conversation_attachment` | 会话附件 | user_id |
| 6 | `t_message_feedback` | 消息反馈 | user_id |
| 7 | `t_knowledge_base` | 知识库（根聚合） | created_by |
| 8 | `t_knowledge_document` | 知识文档 | kb_id |
| 9 | `t_knowledge_chunk` | 知识块 | kb_id/doc_id |
| 10 | `t_intent_node` | 意图节点 | kb_id |
| 11 | `t_query_term_mapping` | 查询词映射 | domain |
| 12 | `t_rag_trace_run` | RAG 追踪运行 | user_id |
| 13 | `t_sample_question` | 推荐问题 | （全局/租户级） |
| 14 | `sa_agent_version` | Agent 版本（隶属 sa_agent_definition） | agent_id |
| 15 | `t_retrieval_strategy_template` | 检索策略模板 | — |
| 16 | `t_retrieval_evaluation_dataset` | 检索评测数据集 | — |
| 17 | `t_retrieval_evaluation_run` | 检索评测运行 | — |
| 18 | `t_retrieval_evaluation_comparison` | 检索评测对比 | — |

#### P1 — 子表（经父表 JOIN 继承租户，加列防御性，二期）

`t_knowledge_document_chunk_log`、`t_knowledge_document_schedule`、`t_knowledge_document_schedule_exec`、`t_rag_trace_node`、`t_ingestion_pipeline(_node)`、`t_ingestion_task(_node)`、`sa_agent_step`、`sa_agent_checkpoint`、`sa_agent_run_lease`、`sa_agent_publish_check`、`sa_agent_tool_binding`、`sa_context_item`、`sa_sandbox_execution`、`sa_sandbox_artifact`、`sa_production_gate_report`、`sa_eval_candidate`、`sa_eval_sample`、`t_memory_conflict_log`、`t_memory_quality_snapshot`（约 19 张）。

#### P2 — 系统/全局表（不需要或暂不加，逐表决策）

`t_outbox_event`、`t_knowledge_vector`、`sa_durable_task_queue`、`sa_agent_run_event_buffer`、`t_memory_maintenance_run`、`t_agent_extension_status`、`sa_tool_catalog`、`sa_connector_version`、`sa_connector_operation`、`sa_agent_template`、`sa_ai_model_config`（约 13 张）。其中 `sa_tool_catalog`/`sa_ai_model_config`/`sa_agent_template` 属**平台级共享配置**，若产品决定“租户可定制”再纳入 P1。

> **架构决策**：`t_sample_question`、`t_retrieval_*`、`sa_ai_model_config` 究竟“全局共享”还是“租户私有”需产品确认；本方案按**租户私有**处理（更安全，全局数据可用 `tenant_id='default'` 表示公共）。

### 4.2 P0 加列 DDL（对齐现有 `tenant_id VARCHAR(64)` 惯例）

以 `t_user` 为例，其余 17 张同构：

```sql
-- 1) 加列（可空，先不加 NOT NULL，便于回填）
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default';

-- 2) 回填存量数据为 default 租户
UPDATE t_user SET tenant_id = 'default' WHERE tenant_id IS NULL;

-- 3) 收紧为 NOT NULL（回填完成后）
ALTER TABLE t_user ALTER COLUMN tenant_id SET NOT NULL;

-- 4) 复合索引：tenant_id 作为最左前缀，覆盖高频查询
CREATE INDEX IF NOT EXISTS idx_user_tenant ON t_user (tenant_id, username);
```

各表建议索引（`tenant_id` 置最左，匹配现有 `idx_*_lifecycle_user_status` 模式）：

| 表 | 索引 DDL |
|----|---------|
| `t_conversation` | `CREATE INDEX IF NOT EXISTS idx_conv_tenant_user ON t_conversation (tenant_id, user_id, last_time);` |
| `t_message` | `CREATE INDEX IF NOT EXISTS idx_msg_tenant_conv ON t_message (tenant_id, conversation_id, user_id, create_time);` |
| `t_knowledge_base` | `CREATE INDEX IF NOT EXISTS idx_kb_tenant ON t_knowledge_base (tenant_id, name);` |
| `t_knowledge_document` | `CREATE INDEX IF NOT EXISTS idx_doc_tenant_kb ON t_knowledge_document (tenant_id, kb_id);` |
| `t_knowledge_chunk` | `CREATE INDEX IF NOT EXISTS idx_chunk_tenant_doc ON t_knowledge_chunk (tenant_id, kb_id, doc_id);` |

### 4.3 RLS DDL（P0 18 张表统一启用）

```sql
-- 以 t_knowledge_base 为例，18 张表逐一执行
ALTER TABLE t_knowledge_base ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_base FORCE ROW LEVEL SECURITY;   -- 表 owner 也受约束，防超管绕过

CREATE POLICY rls_tenant_isolation ON t_knowledge_base
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
```

> - `current_setting('app.tenant_id', true)`：第二参 `true` = `missing_ok`，未设置时返回 NULL 而非报错；NULL 与任何 `tenant_id` 比较为 false，**默认拒绝**（fail-safe）。
> - `USING` 管 SELECT/UPDATE/DELETE 可见行，`WITH CHECK` 管 INSERT/UPDATE 写入行，二者都设防止“写入到别的租户”。
> - `FORCE` 确保连接所用 DB 角色即使是表 owner 也走 RLS（应用连接不应用 superuser）。

---

## 5. 后端实现骨架

### 5.1 新增类清单

| 类 | 模块 | 职责 |
|----|------|------|
| `TenantContext` | kernel `.../kernel/tenant` | ThreadLocal 契约（§3.1） |
| `TenantInterceptor` | adapter-web `.../adapters/web` | 入栈/清栈（§3.2） |
| `JdbcTenantSchemaUpgrade` | adapter-repository-jdbc `.../jdbc` | P0 加列 + RLS 幂等升级（§5.2） |
| `TenantConnectionPreparer` | adapter-repository-jdbc `.../jdbc` | 连接级 `SET app.tenant_id`（§5.4） |

### 5.2 JdbcTenantSchemaUpgrade（复用现有幂等框架）

完全复刻 `JdbcChatSchemaUpgrade`（`JdbcChatSchemaUpgrade.java:30`）的 `addColumnIfMissing` / `tableExists` / `executePostgresPartialIndexOrPlainIndex` 私有方法风格：

```java
package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/** 为 P0 核心业务表补 tenant_id 列、索引并启用 RLS。幂等，可重复执行。 */
public class JdbcTenantSchemaUpgrade {

    private static final List<String> P0_TABLES = List.of(
            "t_user", "t_conversation", "t_conversation_summary", "t_message",
            "sa_conversation_attachment", "t_message_feedback", "t_knowledge_base",
            "t_knowledge_document", "t_knowledge_chunk", "t_intent_node",
            "t_query_term_mapping", "t_rag_trace_run", "t_sample_question",
            "sa_agent_version", "t_retrieval_strategy_template",
            "t_retrieval_evaluation_dataset", "t_retrieval_evaluation_run",
            "t_retrieval_evaluation_comparison");

    private final JdbcTemplate jdbcTemplate;

    public JdbcTenantSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    public void upgrade() {
        for (String table : P0_TABLES) {
            if (!tableExists(table)) {
                continue;
            }
            addColumnIfMissing(table, "tenant_id", "VARCHAR(64) DEFAULT 'default'");
            jdbcTemplate.execute("UPDATE " + table + " SET tenant_id = 'default' WHERE tenant_id IS NULL");
            createTenantIndexIfMissing(table);
            enableRlsIfPostgres(table);
        }
    }

    private void createTenantIndexIfMissing(String table) {
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_tenant ON " + table + " (tenant_id)");
    }

    /** RLS 仅 PostgreSQL 支持；嵌入式测试库（H2）静默跳过，与现有 executePostgres... 容错风格一致。 */
    private void enableRlsIfPostgres(String table) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            jdbcTemplate.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
            jdbcTemplate.execute(
                "DROP POLICY IF EXISTS rls_tenant_isolation ON " + table);
            jdbcTemplate.execute(
                "CREATE POLICY rls_tenant_isolation ON " + table +
                " USING (tenant_id = current_setting('app.tenant_id', true))" +
                " WITH CHECK (tenant_id = current_setting('app.tenant_id', true))");
        } catch (Exception ignored) {
            // 非 PostgreSQL 或已存在：以 init SQL 为准，不阻断启动
        }
    }

    // addColumnIfMissing / tableExists / columnExists：照搬 JdbcChatSchemaUpgrade 实现
}
```

**注册**（仿 `SeahorseAgentMemoryRepositoryAutoConfiguration.java:94`，遵守 CLAUDE.md 第 6 层规则，`@AutoConfigureAfter(DataSourceAutoConfiguration.class)`）：

```java
@Bean
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
        havingValue = "jdbc", matchIfMissing = true)
@ConditionalOnMissingBean(JdbcTenantSchemaUpgrade.class)
public JdbcTenantSchemaUpgrade seahorseJdbcTenantSchemaUpgrade(DataSource dataSource) {
    JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
    try {
        upgrade.upgrade();
    } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).warn("Tenant schema upgrade failed; continuing", e);
    }
    return upgrade;
}
```

> **架构决策**：放入现有 `SeahorseAgentMemoryRepositoryAutoConfiguration` 还是新建 `SeahorseAgentTenantAutoConfiguration` 并加入 `AutoConfiguration.imports`（第 1 层 after DataSource）。建议新建，职责清晰；新增类须登记到 `spring-boot-starter/.../META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

### 5.3 Repository 改造前/后样例（以 JdbcAgentDefinitionRepositoryAdapter 为例）

**改造前**（`JdbcAgentDefinitionRepositoryAdapter.java:66`，findById 无租户过滤 = 越权读）：
```java
private static final String SQL_FIND_DEFINITION = """
        SELECT agent_id, tenant_id, name, ...
        FROM sa_agent_definition
        WHERE agent_id = ?
        """;

@Override
public Optional<AgentDefinition> findById(String agentId) {
    return jdbcTemplate.query(SQL_FIND_DEFINITION, this::mapDefinition, agentId.trim())
            .stream().findFirst();
}
```

**改造后**（SQL 内置 `tenant_id = ?`，租户从 `TenantContext` 注入，调用方无法绕过）：
```java
private static final String SQL_FIND_DEFINITION = """
        SELECT agent_id, tenant_id, name, ...
        FROM sa_agent_definition
        WHERE agent_id = ? AND tenant_id = ?
        """;

@Override
public Optional<AgentDefinition> findById(String agentId) {
    if (!hasText(agentId)) {
        return Optional.empty();
    }
    return jdbcTemplate.query(SQL_FIND_DEFINITION, this::mapDefinition,
                    agentId.trim(), TenantContext.require())   // ← 强制租户
            .stream().findFirst();
}
```

对应核心业务表（`JdbcConversationRepositoryAdapter.java:44`）：
```java
// 前：WHERE user_id = ? AND deleted = 0
// 后：WHERE tenant_id = ? AND user_id = ? AND deleted = 0
// 参数表首位补 TenantContext.require()
```

> **审计方法**：① `grep -rln "tenant_id" Jdbc*Adapter.java` 得 41/80 基线；② 对 39 个无过滤适配器逐一判定 P0/P1/P2；③ P0 全部改造后，编写**静态校验测试**：扫描所有 `Jdbc*Adapter` 的 P0 表 SQL 常量，断言含 `tenant_id`（防回退）。

### 5.4 TenantConnectionPreparer（RLS 连接级 GUC）

```java
package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 在事务/查询执行前，把当前租户写入连接会话变量，供 RLS POLICY 读取。 */
public class TenantConnectionPreparer {

    private final JdbcTemplate jdbcTemplate;

    public TenantConnectionPreparer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void apply() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            // set_config(name, value, is_local=false)：会话级，连接归还 reset 前有效
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, false)", String.class, tenantId);
        }
    }
}
```

> **架构决策（RLS 实现）**：连接级注入有三种落点，二期定夺：
> 1. **AOP 切面**绕 `@Transactional` 方法边界调用 `apply()`（侵入小，但非事务的单条查询覆盖不到）；
> 2. **HikariCP `connectionInitSql`** 仅支持静态 SQL，无法动态传 tenantId，**不适用**；
> 3. **包装 `DataSource#getConnection`**，借出连接即 `SET`，归还前 `RESET app.tenant_id`（最严密，推荐）。
>
> MVP 采用方案 2（HikariCP Proxy），Phase 2 可切换方案 1。

---

## 6. 任务清单

### P0（MVP 必须）

- [ ] **T1** 新增 `TenantContext`（kernel `.../kernel/tenant`），含 `getTenantId/setTenantId/require/clear` + 收口 `DEFAULT_TENANT_ID`
- [ ] **T2** 新增 `TenantInterceptor`（adapter-web），在 `SeahorseSecurityWebMvcConfiguration.addInterceptors()` 以 `order(0)` 注册，`afterCompletion` 清栈
- [ ] **T3** `t_user` 加 `tenant_id`；`UserRecord` + `JdbcUserRepositoryAdapter` 带出 tenantId
- [ ] **T4** `TokenServicePort.login(userId, tenantId)`；`SaTokenServiceAdapter` 写 `StpUtil.getSession().set("tenantId", ...)`；`KernelAuthService.login` 传租户
- [ ] **T5** `CurrentUser` + `SaTokenCurrentUserAdapter` 增 tenantId 字段
- [ ] **T6** `JdbcTenantSchemaUpgrade`：18 张 P0 表加列 + 回填 default + 索引（复用幂等框架）
- [ ] **T7** 新建 `SeahorseAgentTenantAutoConfiguration` 并登记 `AutoConfiguration.imports`（after DataSource），注册 T6 的 upgrade bean
- [ ] **T8** 改造 18 张 P0 表对应的 `Jdbc*Adapter`：SQL 内置 `tenant_id = ?`，参数取 `TenantContext.require()`
- [ ] **T9** 收口 21 处散落 `DEFAULT_TENANT_ID` 常量，统一引用 `TenantContext.DEFAULT_TENANT_ID`；修正前端 `"tenant-default"` → `"default"`
- [ ] **T10** 跨租户隔离自动化测试（§7）
- [ ] **T11** P0 SQL 静态校验测试：断言 P0 适配器 SQL 常量含 `tenant_id`（防回退）

### P1（纵深防御 / 二期）

- [ ] **T12** `JdbcTenantSchemaUpgrade` 扩展：18 张 P0 表 `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY`
- [ ] **T13** `TenantConnectionPreparer` + 连接级 `SET app.tenant_id`（选型见 §5.4）
- [ ] **T14** P1 子表（约 19 张）加列 + 适配器过滤
- [ ] **T15** 异步链路（`@Async`/MQ 消费）租户透传（§9 R3）
- [ ] **T16** RLS 启用后的回归压测（索引有效性、`current_setting` 开销）

---

## 7. 测试策略

### 7.1 跨租户隔离测试（核心，T10）

构造租户 A、B 各一套数据，断言互相不可见。基于现有 `seahorse-agent-tests` 集成测试模块 + Testcontainers PostgreSQL（与生产同构，覆盖 RLS）。

```java
@Test
void tenantA_cannotSeeTenantB_knowledgeBase() {
    // given：A、B 各建一个知识库
    TenantContext.setTenantId("tenant-A");
    Long kbA = knowledgeBaseRepo.create(newKb("A 的知识库"));
    TenantContext.setTenantId("tenant-B");
    Long kbB = knowledgeBaseRepo.create(newKb("B 的知识库"));

    // when：以 A 身份查询
    TenantContext.setTenantId("tenant-A");
    List<KnowledgeBase> listForA = knowledgeBaseRepo.listAll();

    // then：A 只看到自己的，且无法按 id 读到 B 的
    assertThat(listForA).extracting(KnowledgeBase::name).containsExactly("A 的知识库");
    assertThat(knowledgeBaseRepo.findById(kbB)).isEmpty();   // 跨租户读取必须为空

    TenantContext.clear();
}
```

### 7.2 测试矩阵

| 场景 | 断言 |
|------|------|
| 应用层过滤 | A 的 `listAll/page/findById` 不含 B 的数据 |
| RLS 兜底 | 故意构造一条**漏拼 `tenant_id`** 的原生 SQL，验证 RLS 仍拦截（设 `app.tenant_id='tenant-A'` 查不到 B） |
| 未设租户 fail-safe | `app.tenant_id` 未设置时，RLS 查询返回空集（`current_setting(...,true)` = NULL） |
| 写入越权防护 | A 上下文下 INSERT 一条 `tenant_id='tenant-B'`，被 `WITH CHECK` 拒绝 |
| 线程串号 | 拦截器 `afterCompletion` 后 `TenantContext.getTenantId()` 为 null |
| 登录写入 | 登录后 `StpUtil.getSession().get("tenantId")` 等于 `t_user.tenant_id` |
| 存量兼容 | 升级后存量数据 `tenant_id = 'default'`，default 租户可正常读写 |

### 7.3 静态防回退测试（T11）

反射/扫描所有 `Jdbc*Adapter` 中针对 P0 表的 SQL 字符串常量，断言包含 `tenant_id`，防止后续开发新增无过滤查询。

---

## 8. 验收标准

1. **契约就位**：`TenantContext` 四个方法（`getTenantId/setTenantId/require/clear`）+ `DEFAULT_TENANT_ID` 落地于 kernel，被 web 与 jdbc 两个 adapter 引用。
2. **链路贯通**：登录 → 会话写 tenantId → 拦截器入栈 → JDBC 读 `require()` → 请求结束清栈，端到端可在集成测试中验证。
3. **18 张 P0 表**全部含 `tenant_id`，存量数据回填为 `default`，幂等升级可重复执行无副作用。
4. **隔离生效**：§7.1 跨租户测试通过；A 无法通过 `listAll/page/findById` 任一路径读到 B 的数据。
5. **RLS 兜底（P1）**：故意漏过滤的 SQL 在 RLS 下仍隔离；未设 `app.tenant_id` 时默认拒绝。
6. **无串号**：并发请求下 ThreadLocal 无残留（清栈测试通过）。
7. **常量收口**：全仓 `DEFAULT_TENANT_ID` 单一出处，前后端取值一致（均为 `"default"`）。
8. **不破坏存量**：default 租户下所有现有功能回归通过。

---

## 9. 风险与缓解

| ID | 风险 | 影响 | 缓解 |
|----|------|------|------|
| R1 | **ThreadLocal 串号**：清栈遗漏导致线程池复用时 B 拿到 A 的租户 | 严重越权 | 拦截器 `afterCompletion` 强制 `clear()`（最外层 order=0，逆序最后执行）；RLS 第二道防线；清栈测试 |
| R2 | **漏改适配器**：39 个无过滤适配器若 P0 遗漏 | 越权读 | 分级清单逐表核销；T11 静态校验测试持续守护；RLS 兜底 |
| R3 | **异步丢上下文**：`@Async`/MQ 消费/`DispatcherType.ASYNC` 不继承 ThreadLocal | 异步任务无租户/错租户 | 投递时显式捕获 tenantId，执行端 `setTenantId` 重放后 `clear`；列为 P1（T15）；`TenantInterceptor` 已对 ASYNC 派发不重复入栈 |
| R4 | **RLS 性能**：`current_setting` 每查询调用 + 全表加 POLICY | 查询变慢 | `tenant_id` 置复合索引最左前缀；RLS 作为 P1 灰度；压测（T16） |
| R5 | **连接未设 GUC**：连接借出未 `SET app.tenant_id`，RLS 默认拒绝 → 查不到数据 | 功能不可用 | `current_setting(...,true)` 容错 + 连接包装器统一注入（§5.4）；先上应用层过滤，RLS 灰度 |
| R6 | **登录改接口**：`TokenServicePort.login` 加参数 | 编译/测试波及 | 同 PR 改全部实现与调用方（仅 `SaTokenServiceAdapter` + `KernelAuthService` + 对应测试） |
| R7 | **存量数据归属**：default 租户承载所有历史数据 | 多租户开通后 default 数据语义模糊 | 明确 `default` = 平台内置/迁移前数据；新租户开通走 `02` 方案分配独立 tenantId |
| R8 | **跨租户共享数据**：`sa_tool_catalog`/`sa_ai_model_config` 等全局配置若误加严格 RLS | 平台配置不可读 | P2 表逐一决策；全局表用 `tenant_id='default'` 表示公共并放宽 POLICY，或不启 RLS |

---

## 10. 参考文件锚点

| 主题 | 文件:行号 |
|------|-----------|
| canonical 租户常量 | `seahorse-agent-kernel/.../kernel/domain/agent/definition/AgentDefinition.java:54`、`:148` |
| 前端不一致常量 | `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx:104`（`"tenant-default"`） |
| 建表脚本 | `resources/database/seahorse_init.sql`（96 表；已含 tenant_id 范例 `:443` `t_short_term_memory`） |
| 安全拦截器注册点 | `seahorse-agent-adapter-web/.../web/SeahorseSecurityWebMvcConfiguration.java:38`、`addInterceptors():72` |
| 当前用户契约 | `seahorse-agent-kernel/.../ports/outbound/auth/CurrentUser.java:20`、`CurrentUserPort.java` |
| Sa-Token 登录适配 | `seahorse-agent-adapter-web/.../web/SaTokenServiceAdapter.java:26` |
| Sa-Token 当前用户适配 | `seahorse-agent-adapter-web/.../web/SaTokenCurrentUserAdapter.java:40` |
| 登录服务 | `seahorse-agent-kernel/.../kernel/application/auth/KernelAuthService.java:47`、`:62` |
| Token 端口 | `seahorse-agent-kernel/.../ports/outbound/auth/TokenServicePort.java` |
| 用户记录 | `seahorse-agent-kernel/.../ports/outbound/auth/UserRecord.java` |
| 幂等 schema 升级框架 | `seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcChatSchemaUpgrade.java:30`、`upgrade():40`、`addColumnIfMissing():696`、`executePostgresPartialIndexOrPlainIndex():615` |
| 升级注册范例 | `seahorse-agent-spring-boot-starter/.../spring/SeahorseAgentMemoryRepositoryAutoConfiguration.java:94` |
| Repo 过滤反例（findById 无过滤） | `seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcAgentDefinitionRepositoryAdapter.java:66`、`page():162` |
| 核心表查询反例 | `seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcConversationRepositoryAdapter.java:44` |
| 自动配置 imports | `seahorse-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

> 审计基线数据：JDBC 适配器 **80 个**，含 `tenant_id` 过滤 **41 个**，无过滤 **39 个**；建表 **96 张**，含 `tenant_id` **46 张**，缺失 **50 张**（P0 18 / P1 ~19 / P2 ~13）。
