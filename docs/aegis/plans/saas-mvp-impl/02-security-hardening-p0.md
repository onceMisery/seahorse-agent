# 02 · 安全 P0 加固技术方案（SaaS MVP）

> 状态：已定稿 · 版本：v1.0 · 编写日期：2026-06-04
> 定位：本文档是 SaaS MVP 主线二「域 5 安全」的落地实现方案，覆盖路线图
> `2026-06-04-saas-mvp-execution-roadmap.md` 中标注的四个安全 P0。
> **强绑定**：这四项与块 A（多租户隔离）**同期交付**。单体形态下它们是「待改进」，
> 多租户后它们是「跨租户数据泄露入口」，必须在块 A 的 `TenantContext` 落地后立即闭合。

---

## 1. 目标与范围

### 1.1 目标

把域 5 的四个「决策已建模、但执行/生命周期缺失」的安全缺口补齐到**可强制执行**的程度：

| P0 | 缺口一句话 | 加固后状态 |
|----|-----------|-----------|
| P0-1 密钥管理 | 仅 `create`，无查询/轮转/过期/脱敏展示，且读取无租户过滤 | 元数据可查（只返脱敏）、可轮转、可过期、读取强制租户绑定 |
| P0-2 资源 ACL | 工具网关执行点的资源访问校验默认 `allowAll()`，ACL 引擎从未被调用 | 工具网关执行前强制调用 ACL 引擎，非 `ALLOW` 即阻断 |
| P0-3 沙箱隔离 | 仅校验网络白名单，无文件系统边界/进程隔离/资源限制 | 策略请求扩展 FS/进程/资源维度，越界拒绝 + 制品归属校验 |
| P0-4 连接器凭证 | `bindCredential` 保存前不 verify，无失效检测与过期 | 保存前可验、绑定带过期、失效可检测并自动置 `INVALID` |

### 1.2 范围

- **在范围内**：kernel 应用层服务、domain 决策类、ports 接口、JDBC 适配器、自动配置装配、
  新增/变更 DDL、新增 REST 端点、前端密钥脱敏展示与 ACL 拦截错误提示。
- **不在范围内**：块 A 的 `TenantContext`/`TenantInterceptor`/JWT 解析/RLS 本身（由块 A 提供，本文档直接引用其契约）；
  真实沙箱容器运行时（`SandboxRuntimePort` 的容器化实现）——本文档只补**策略声明与边界判定**，
  容器内核级强制（cgroup/seccomp/namespace）作为 P1 由后续 runtime 切片承接（见 §3.3）。

### 1.3 直接引用的块 A 租户契约（勿重设计）

```text
TenantContext (ThreadLocal):
  TenantContext.getTenantId() : String
  TenantContext.setTenantId(String)
  TenantContext.clear()
JWT claim: tenantId
隔离策略: 应用层 tenant_id 过滤  +  PostgreSQL RLS 兜底
```

> 说明：截至本文档编写，仓库中**尚不存在** `TenantContext` 类（`grep -r TenantContext` 无命中），
> 由块 A 引入。本文档所有「从 `TenantContext.getTenantId()` 取租户」的骨架，均以块 A 完成为前置依赖，
> 标注为 **[依赖块A]**。

---

## 2. 现状（代码级）

四个域的核心类与缺口锚点（行号基于当前 `main`）：

| 能力 | 关键类（真实路径） | 现状证据 |
|------|-------------------|---------|
| 密钥写 | `kernel/.../application/credential/KernelSecretManagementService.java:61-71` | 仅 `create()` |
| 密钥入站口 | `ports/inbound/credential/SecretManagementInboundPort.java` | 仅声明 `create()` |
| 密钥控制器 | `adapter-web/.../SeahorseSecretController.java:53` | 仅 `POST /api/secrets` |
| 密钥存储 | `adapter-repository-jdbc/.../JdbcSecretStoreAdapter.java:49-53,77-89` | `getSecret` 按 `secret_ref` 查，**无 tenant 条件** |
| 密钥表 | `resources/database/seahorse_init.sql:1296-1304` | 有 `rotated_at`，**无** `expires_at`/`status`/`name` |
| ACL 引擎 | `kernel/.../context/AclBackedResourceAccessPolicyPort.java:52-73` | 决策正确，按规则 effect 返回 |
| ACL 工具校验 | `kernel/.../CatalogBackedToolPolicyPort.java:158-177` | 执行点存在，但依赖 `ToolResourceAccessPort` |
| ACL 工具口默认 | `ports/outbound/agent/ToolResourceAccessPort.java` | `allowAll()` 默认实现 |
| ACL 装配 | `spring-boot-starter/.../SeahorseAgentKernelAgentAutoConfiguration.java:243` | `getIfAvailable(ToolResourceAccessPort::allowAll)`，**无 bean 桥接** |
| 沙箱策略 | `kernel/.../sandbox/DefaultSandboxPolicyPort.java:42-55` | 仅网络白名单 |
| 沙箱运行时 | `kernel/.../sandbox/KernelSandboxRuntimeService.java:153-206` | 制品仅 `promptVisible()` 过滤，会话查找不校验租户/请求者 |
| 连接器绑定 | `kernel/.../connector/KernelOpenApiConnectorImportService.java:195-232` | `bindCredential` 保存前不 verify |
| 连接器绑定表 | `resources/database/seahorse_init.sql:1161-1173` | 有 `rotated_at`，**无** `expires_at`/`last_verified_at` |

> 现状结论：四个域的**领域模型与决策逻辑大多已就位**（ACL 规则引擎、密钥脱敏不可逆 `toString`、
> 凭证状态枚举含 `INVALID`），缺的是**执行点接通、生命周期 API、租户边界强制**。
> 这意味着加固成本可控，且不破坏六边形分层——多数改造落在 application 服务 + ports + 装配。

---

## 3. 四个 P0 技术方案

### 3.1 P0-1 · 密钥管理：脱敏查询 + 轮转 + 过期 + 租户绑定

#### 3.1.1 问题证据

**证据 A — 只有创建，没有任何读/改/废生命周期。**
`SecretManagementInboundPort` 仅声明一个方法：

```java
// ports/inbound/credential/SecretManagementInboundPort.java
public interface SecretManagementInboundPort {
    SecretMetadata create(SecretCreateCommand command);   // ← 唯一方法
}
```

`KernelSecretManagementService.create()`（:61-71）创建后即返回 `SecretMetadata`，
无 `page`/`get`/`rotate`/`expire`。控制器 `SeahorseSecretController` 仅暴露 `POST /api/secrets`（:53）。
仓库内**不存在** `SecretLookupPort`（`find SecretLookupPort.java` 无命中），
唯一的读取口是运行时用的 `SecretStorePort.getSecret`。

**证据 B — 运行时读取无租户过滤（跨租户读取入口）。**
`JdbcSecretStoreAdapter`：

```java
// JdbcSecretStoreAdapter.java:49-53
private static final String SQL_FIND_BY_REF = """
        SELECT %s FROM sa_secret_ref WHERE secret_ref = ?
        """.formatted(SECRET_COLUMNS);          // ← 仅 secret_ref，无 tenant_id

// :77-89
public Optional<SecretValue> getSecret(String secretRef) {   // ← 入参无 tenantId
    ...
}
```

`SecretStorePort.getSecret(String secretRef)` 签名本身就不带 `tenantId`。多租户后，
任何能拼出/猜出他租户 `secret_ref` 的调用方都能解密读取——这是**最高危的一条**。

**证据 C — 表结构缺过期/状态/可展示元数据。**
`sa_secret_ref`（:1296-1304）只有 `secret_ref, tenant_id, encrypted_value, metadata_json, created_at, rotated_at`。
无 `expires_at`（无法过期）、无 `status`（无法表达 ROTATED/EXPIRED/DISABLED）、
无 `name`/`masked_hint`（前端无法做有意义的脱敏列表）。

**证据 D — 前端已「等」着这套能力。**
`frontend/src/services/securityGovernanceService.ts:42-49` 已定义 `SecretItem { secretId, name, type, maskedValue, description, createTime }`，
但只有 `createSecret`（:145）接了线——前端的列表/脱敏展示能力**已建模，等后端补查询**。

**正向资产（不要推翻）**：`SecretValue.toString()` 恒返 `[REDACTED]`（不可逆打印），
`SecretMetadataPolicy.normalizeMetadataJson` 拒绝把明文塞进 `metadataJson`（:33-39），
`SecretMetadata` 本身就是「无明文」的元数据载体。脱敏的**领域基础已具备**。

#### 3.1.2 修复设计

四个动作，分层落点严格遵守六边形：

1. **元数据查询（只返脱敏）**：新增 `SecretMetadataQueryPort`（outbound），
   `KernelSecretManagementService` 增 `page/get`，强制 `requireRole(admin)` + 强制 `tenantId` 过滤。
   返回类型用现有 `SecretMetadata`（本就无明文），并加 `maskedHint` 字段。
2. **轮转**：新增 `rotate(SecretRotateCommand)`，旧 secretRef 置 `ROTATED`，
   写入新密文，保留同一「逻辑密钥」的 `name` 以便引用方平滑切换。
3. **过期**：`sa_secret_ref` 加 `expires_at` + `status`；运行时读取增加「过期即拒」判定。
4. **租户绑定读取（堵证据 B）**：`SecretStorePort.getSecret` 签名加 `tenantId`，
   SQL 加 `AND tenant_id = ?`；调用方从 **[依赖块A]** `TenantContext.getTenantId()` 取值。

> 决策点：`getSecret` 改签名是**破坏性变更**，影响 `OAuthCredentialProvider`、
> `SecretStoreCredentialProvider`、`McpHttpAutoConfiguration` 三处调用方（见 §11）。
> 选择「改签名」而非「内部偷偷读 TenantContext」，因为 kernel 的 ports 不应隐式依赖 ThreadLocal——
> 租户应作为显式参数穿过端口边界，保持 kernel 可测、可替换。

#### 3.1.3 代码骨架

**domain 新增 `SecretStatus`：**

```java
// kernel/.../domain/credential/SecretStatus.java
public enum SecretStatus { ACTIVE, ROTATED, EXPIRED, DISABLED }
```

**`SecretMetadata` 增字段（保持无明文）：**

```java
public record SecretMetadata(
        String secretRef, String tenantId, String name, String secretType,
        String maskedHint,          // 不可逆预览，如 "sk-…9F2A"，create 时一次性算好
        String metadataJson,
        SecretStatus status, Instant expiresAt,
        Instant createdAt, Instant rotatedAt) { ... }
```

**入站口扩展：**

```java
// ports/inbound/credential/SecretManagementInboundPort.java
public interface SecretManagementInboundPort {
    SecretMetadata create(SecretCreateCommand command);
    SecretMetadataPage page(SecretQuery query);          // 新增：分页，只返脱敏元数据
    SecretMetadata get(String tenantId, String secretRef); // 新增：单条脱敏元数据
    SecretMetadata rotate(SecretRotateCommand command);    // 新增：轮转
    SecretMetadata disable(String tenantId, String secretRef); // 新增：手动废止
}
```

**服务实现关键逻辑：**

```java
// KernelSecretManagementService
@Override
public SecretMetadataPage page(SecretQuery query) {
    currentUserPort.requireRole(ADMIN_ROLE);
    SecretQuery safe = Objects.requireNonNull(query, "query must not be null");
    requireText(safe.tenantId(), "tenantId must not be blank");  // 强制租户过滤
    return secretMetadataQueryPort.page(safe);                   // 仅查元数据，永不解密
}

@Override
public SecretMetadata rotate(SecretRotateCommand command) {
    currentUserPort.requireRole(ADMIN_ROLE);
    SecretRotateCommand safe = Objects.requireNonNull(command, "command must not be null");
    Instant now = clock.instant();
    // 旧引用置 ROTATED；写入新密文（复用 SecretWritePort.putSecret），返回新元数据
    secretMetadataQueryPort.markStatus(safe.tenantId(), safe.secretRef(),
            SecretStatus.ROTATED, now);
    return secretWritePort.putSecret(new SecretWriteCommand(
            requireText(secretRefSupplier.get(), "secretRef must not be blank"),
            safe.tenantId(), safe.newSecretValue(),
            safe.metadataJson(), now));
}
```

**JDBC 读取堵租户漏洞（破坏性签名变更）：**

```java
// SecretStorePort.java
Optional<SecretValue> getSecret(String tenantId, String secretRef);   // 加 tenantId

// JdbcSecretStoreAdapter
private static final String SQL_FIND_BY_REF = """
        SELECT %s FROM sa_secret_ref
        WHERE secret_ref = ? AND tenant_id = ?           -- 加租户条件
          AND status = 'ACTIVE'
          AND (expires_at IS NULL OR expires_at > ?)     -- 过期即不可读
        """.formatted(SECRET_COLUMNS);
```

#### 3.1.4 测试

- 单元（负向，最重要）：`getSecret(tenantB, refOfTenantA)` 必须返回 `Optional.empty()`（跨租户读取被堵）。
- 单元（负向）：已 `EXPIRED`/`DISABLED`/`ROTATED` 的 ref，`getSecret` 返回空。
- 单元：`page`/`get` 返回的 `SecretMetadata` 序列化后**不含密文**（断言 JSON 不含原值、含 `maskedHint`）。
- 单元：非 admin 调用 `page/rotate/disable` 抛「权限不足」（复用 `CurrentUserPort.requireRole`）。
- 单元：`rotate` 后旧 ref `status==ROTATED`、新 ref `status==ACTIVE` 且密文不同。

---

### 3.2 P0-2 · 资源 ACL：工具网关执行点强制阻断

#### 3.2.1 问题证据

**证据 A — 执行点的资源校验默认放行。**
工具执行走 `LocalToolGatewayPort.invoke()`（:144-218），其裁决依赖 `ToolPolicyPort.decide()`，
真实实现 `CatalogBackedToolPolicyPort.validateResourceAccess()`：

```java
// CatalogBackedToolPolicyPort.java:158-177
private PolicyDecision validateResourceAccess(ToolPolicyRequest request, ToolCatalogEntry tool) {
    if (request.resourceRefs().isEmpty()) return null;
    ToolResourceAccessDecision accessDecision = resourceAccessPort.decide(...);  // ← 关键
    if (accessDecision != null && accessDecision.allowed()) return null;
    return deny(ToolPolicyReasonCodes.RESOURCE_FORBIDDEN, reason);   // 阻断路径存在！
}
```

阻断路径（`RESOURCE_FORBIDDEN`）**是存在的**。问题在 `resourceAccessPort` 的来源：

```java
// ToolResourceAccessPort.java
static ToolResourceAccessPort allowAll() {        // 默认实现：恒放行
    return request -> ToolResourceAccessDecision.allow();
}
```

**证据 B — 没有任何 bean 把真实 ACL 引擎桥接进来。**
装配处只会拿到 `allowAll`：

```java
// SeahorseAgentKernelAgentAutoConfiguration.java:233-244
public ToolPolicyPort seahorseCatalogBackedToolPolicyPort(
        ..., ObjectProvider<ToolResourceAccessPort> toolResourceAccessPort) {
    return new CatalogBackedToolPolicyPort(...,
            toolResourceAccessPort.getIfAvailable(ToolResourceAccessPort::allowAll)); // ← 永远兜底 allowAll
}
```

全仓库 `grep "ToolResourceAccessPort "` 无任何 `@Bean` 产出它。
而真实 ACL 引擎 `AclBackedResourceAccessPolicyPort`（决策正确，:52-73）只被
`KernelContextPackBuilderService.selectedItems()`（:95-103）消费——**只在「上下文包构建」时拦截，
工具执行时完全不接通**。

**结论修正**：用户口径「决策后仅 log、未真正阻断」更精确的描述是——
**ACL 引擎在工具执行点根本没被调用**（被 `allowAll` 短路），
`AuditedResourceAccessPolicyPort`（:54-60）的 log 发生在 context-pack 路径，不在工具网关。
工具网关有阻断能力，但被默认放行喂了空。

#### 3.2.2 修复设计

**不改 `LocalToolGatewayPort`、不改 `CatalogBackedToolPolicyPort`**——它们的阻断逻辑已正确。
只需新增一个**桥接适配器** `AclBridgingToolResourceAccessPort implements ToolResourceAccessPort`，
把工具侧请求翻译为 ACL 引擎的 `ResourceAccessRequest`，逐个 `resourceRef` 调用
`ResourceAccessPolicyPort.decide()`，任一非 `ALLOW` 即 `deny`，并注册为 bean。

> 决策点：放在 application 层做「适配/翻译」而非塞进 `CatalogBackedToolPolicyPort`，
> 因为后者是纯策略，不应直接依赖 context 域的 `ResourceAccessPolicyPort`；
> 桥接器是端口间的 anti-corruption 翻译层，符合六边形。

#### 3.2.3 代码骨架

```java
// kernel/.../application/agent/AclBridgingToolResourceAccessPort.java
public class AclBridgingToolResourceAccessPort implements ToolResourceAccessPort {

    private final ResourceAccessPolicyPort resourceAccessPolicyPort;  // 真实 ACL 引擎

    @Override
    public ToolResourceAccessDecision decide(ToolResourceAccessRequest request) {
        if (request.resourceRefs().isEmpty()) {
            return ToolResourceAccessDecision.allow();
        }
        for (Map.Entry<String, String> ref : request.resourceRefs().entrySet()) {
            AccessDecision decision = resourceAccessPolicyPort.decide(new ResourceAccessRequest(
                    request.tenantId(),
                    AccessSubjectType.USER_DELEGATED_AGENT,   // agent 代用户执行
                    request.userId(),
                    ResourceAction.READ,                      // 后续可按 tool.actionType 细化
                    new ResourceRef(ref.getKey(), ref.getValue(),
                            request.tenantId(), request.userId(), null)));
            if (decision.effect() != AccessDecisionEffect.ALLOW) {
                return ToolResourceAccessDecision.deny(
                        "RESOURCE_ACL_DENY:" + ref.getKey() + ":" + decision.reasonCode());
            }
        }
        return ToolResourceAccessDecision.allow();
    }
}
```

**装配（新增 bean，仅在 ACL 引擎存在时生效）：**

```java
// SeahorseAgentKernelAgentAutoConfiguration（或 RegistryAutoConfiguration，遵守 @AutoConfigureAfter）
@Bean
@ConditionalOnAgentRuntimeEnabled
@ConditionalOnBean(ResourceAccessPolicyPort.class)
@ConditionalOnMissingBean(ToolResourceAccessPort.class)
public ToolResourceAccessPort seahorseAclBridgingToolResourceAccessPort(
        ResourceAccessPolicyPort resourceAccessPolicyPort) {
    return new AclBridgingToolResourceAccessPort(resourceAccessPolicyPort);
}
```

> **CLAUDE.md 合规**：该 bean 依赖 `ResourceAccessPolicyPort`（由 RegistryAutoConfiguration 产出），
> 若放在 AgentAutoConfiguration，必须在其 `@AutoConfigureAfter` 中声明 RegistryAutoConfiguration，
> 否则 `@ConditionalOnBean` 可能因装配顺序错判为缺失。**[架构决策]** 二者当前的 after 关系，
> 落地时核对 `AutoConfiguration.imports` 第 6 层声明。

#### 3.2.4 测试

- 单元（负向，核心）：ACL 中存在一条 `DENY` 规则命中 `resourceRef`，
  `AclBridgingToolResourceAccessPort.decide` 返回 `allowed()==false`，reason 含 `RESOURCE_ACL_DENY`。
- 集成（负向，端到端越权）：构造 agent 调用工具携带他人/他租户 `resourceRef`，
  断言 `LocalToolGatewayPort.invoke` 返回 `ToolInvocationResult.failed`，错误码 `RESOURCE_FORBIDDEN`，
  且 `ToolPort.invoke` **从未被调用**（用 spy 验证真实工具未触达）。
- 单元：装配测试——存在 `ResourceAccessPolicyPort` bean 时，
  `ToolResourceAccessPort` 不再是 `allowAll`，而是 `AclBridgingToolResourceAccessPort`。
- 回归：`resourceRefs` 为空时放行（不影响无资源工具）。

---

### 3.3 P0-3 · 沙箱隔离：文件系统边界 + 进程隔离 + 资源限制

#### 3.3.1 问题证据

**证据 A — 策略只看网络。**
`DefaultSandboxPolicyPort.decide()` 全部逻辑：

```java
// DefaultSandboxPolicyPort.java:42-55
public SandboxPolicyDecision decide(SandboxPolicyRequest request) {
    if (!safeRequest.networkRequested())  return allow(VALID_REQUEST);
    if (networkPolicy == DENY_ALL)        return deny(NETWORK_DENIED_BY_DEFAULT);
    if (!allowlistedHosts.containsAll(...)) return deny(NETWORK_HOST_NOT_ALLOWLISTED);
    return allow(VALID_REQUEST);
}
```

没有任何文件系统路径、进程归属、内存/CPU 维度的判定。
`SandboxPolicyReasonCode` 枚举也只有网络相关码（`VALID_REQUEST, DEFAULT_DENY,
NETWORK_DENIED_BY_DEFAULT, NETWORK_HOST_NOT_ALLOWLISTED, RUNTIME_UNSUPPORTED, SESSION_NOT_FOUND`）。

**证据 B — 策略请求缺维度。**
`SandboxPolicyRequest`（record）只有 `tenantId, runId, runtimeType, networkRequested, requestedHosts`。
没有 `requesterUserId`（无法做进程归属校验）、没有请求的文件路径、没有资源上限声明。

**证据 C — 制品无归属校验（跨运行/跨租户泄露）。**
`KernelSandboxRuntimeService.execute()`（:174-180）对产物只做 `promptVisible()` 过滤
（`scanStatus==CLEAN && sensitivity!=SECRET`），**不校验产物生成者 == 请求者**。
`listArtifacts(sessionId)`（:201-206）先 `findSessionOrThrow` 再列产物，
但 `findSession`（:225-237）只按 `sessionId` 查，**不校验 session.tenantId 与当前租户一致**——
传入他租户 `sessionId` 即可列其制品。

**证据 D — 无真实运行时适配器。**
`grep "implements SandboxRuntimePort"` 仅命中测试；生产只有 `SandboxRuntimePort.unsupported()` 兜底。
因此内核级强制（cgroup/seccomp/namespace）**当前无落点**，资源限制只能先做到「策略声明 + 拒绝越界请求」，
真正的容器强制随未来 runtime 适配器交付（P1）。

#### 3.3.2 修复设计

分两层，**本期（P0）只做内核可判定的策略层**：

1. **扩展 `SandboxPolicyRequest`**：加 `requesterUserId`、`requestedPaths`（List）、`resourceLimits`（内存/CPU/超时）。
2. **扩展 `DefaultSandboxPolicyPort`**：加文件系统边界判定（默认拒绝 `/etc`、`/root`、`/proc`、`/sys`、`/var/run` 等敏感前缀，
   只允许会话工作目录前缀）、资源上限合法性判定（请求超过租户档位即拒）。
3. **新增 reason code**：`FILESYSTEM_PATH_DENIED`、`RESOURCE_LIMIT_EXCEEDED`、`PROCESS_OWNER_MISMATCH`。
4. **制品/会话归属校验**：`KernelSandboxRuntimeService` 的 `findSessionOrThrow` 增加
   `session.tenantId().equals(expectedTenantId)` 校验，不一致抛拒绝；`listArtifacts`/`execute`
   传入 `requesterUserId` 并校验 `session` 归属。租户来源 **[依赖块A]** `TenantContext.getTenantId()`。
5. **进程隔离（验证 artifact 生成者 == 请求者）**：制品落库前记录 `executionId` 已有（`SandboxArtifact.executionId`），
   列制品时联表校验该 `execution` 的 `session.requesterUserId == 当前请求者`。

> 决策点：P0 阶段「资源限制」= **声明 + 越界请求拒绝**，不等于内核强制。
> 必须在文档与验收里讲清这条边界，避免给出「已做到 CPU/内存硬隔离」的错觉——
> 真正硬隔离依赖容器 runtime（P1）。这是安全方案诚实性的底线。

#### 3.3.3 代码骨架

```java
// 扩展后的 SandboxPolicyRequest（ports/outbound/agent）
public record SandboxPolicyRequest(String tenantId, String runId, String requesterUserId,
                                   SandboxRuntimeType runtimeType,
                                   boolean networkRequested, List<String> requestedHosts,
                                   List<String> requestedPaths,          // 新增
                                   SandboxResourceLimits resourceLimits) { ... } // 新增

// domain 新增
public record SandboxResourceLimits(long maxMemoryBytes, int maxCpuMillis, long maxWallClockMillis) {}

// DefaultSandboxPolicyPort 增量
private static final List<String> DENIED_PATH_PREFIXES =
        List.of("/etc", "/root", "/proc", "/sys", "/var/run", "/dev");

public SandboxPolicyDecision decide(SandboxPolicyRequest request) {
    // ... 原网络判定保持不变 ...
    for (String path : request.requestedPaths()) {
        String normalized = normalizePath(path);                  // 解析 ../、符号链接后比较
        if (DENIED_PATH_PREFIXES.stream().anyMatch(normalized::startsWith)
                || !normalized.startsWith(sessionWorkDirPrefix(request))) {
            return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.FILESYSTEM_PATH_DENIED);
        }
    }
    if (exceedsTenantLimits(request.tenantId(), request.resourceLimits())) {
        return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.RESOURCE_LIMIT_EXCEEDED);
    }
    return SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST);
}
```

```java
// KernelSandboxRuntimeService：会话归属校验
private SandboxSession findSessionOrThrow(String sessionId, String expectedTenantId) {
    SandboxSession session = findSession(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sandbox session not found"));
    if (!session.tenantId().equals(expectedTenantId)) {          // 跨租户即拒
        throw new IllegalStateException("Sandbox session tenant mismatch");
    }
    return session;
}
```

> 路径归一化必须先解析 `..`、符号链接再比较前缀，否则 `/tmp/work/../../etc/passwd`
> 可绕过前缀检查——这是 FS 边界的常见绕过点，骨架中以 `normalizePath` 标注，落地需用
> `Path.toRealPath()`/规范化后判定。

#### 3.3.4 测试

- 单元（负向）：`requestedPaths` 含 `/etc/passwd` → `FILESYSTEM_PATH_DENIED`。
- 单元（负向，绕过）：`/tmp/work/../../etc/shadow` 归一化后仍被拒。
- 单元（负向）：`resourceLimits` 超租户档位 → `RESOURCE_LIMIT_EXCEEDED`。
- 单元（负向，跨租户制品）：`listArtifacts(他租户 sessionId)` 抛 tenant mismatch，不返回任何制品。
- 单元（负向，进程归属）：列制品时 `execution` 的请求者 != 当前请求者 → 过滤/拒绝。
- 回归：无路径、无网络、限额在档位内的请求仍 `VALID_REQUEST`。

---

### 3.4 P0-4 · 连接器凭证：绑定前验证 + 失效检测 + 过期

#### 3.4.1 问题证据

**证据 A — 绑定保存前不 verify。**
`KernelOpenApiConnectorImportService.bindCredential()`（:195-232）：

```java
// :209-221
credentialBindingRepository.findActive(...).ifPresent(b -> repo.save(b.rotate(now))); // 旧绑定轮转
ConnectorCredentialBinding binding = credentialBindingRepository.save(
        new ConnectorCredentialBinding(..., ConnectorCredentialBindingStatus.ACTIVE, ...)); // 直接 ACTIVE
```

凭证 `credentialRef` 未经任何「能否真正调通目标 API」的验证就落库为 `ACTIVE`。
启用算子时 `requireActiveCredentialBinding()`（:374-387）也只校验**存在**，不校验**有效**。

**证据 B — 有 INVALID 状态枚举，但无人写入。**
`ConnectorCredentialBindingStatus` 含 `ACTIVE, ROTATED, DISABLED, INVALID`，
但 `grep INVALID` 在业务代码中无赋值点——失效检测能力**只建了模型没接逻辑**。

**证据 C — 表与领域对象无过期/校验时间。**
`sa_connector_credential_binding`（:1161-1173）有 `rotated_at`，无 `expires_at`、无 `last_verified_at`、无 `verify_status`。
`ConnectorCredentialBinding` record 有 `rotate(Instant)` 方法，但无 `expire()`/`invalidate()`/`markVerified()`。

**正向资产**：绑定时「旧 active 自动 rotate」（:209-210）已实现，即**轮转链已具备**，
只缺「保存前验证」「过期」「失效置位」三件事。

#### 3.4.2 修复设计

1. **绑定前验证**：新增 outbound `ConnectorCredentialVerificationPort`，`bindCredential` 在 `save` 前调用
   `verify(connector, operation, authType, credentialRef)`，失败则**拒绝绑定**（抛错，不落 `ACTIVE`）。
   验证不可用时（端口缺失）按配置降级为「跳过验证 + 审计标记 unverified」，避免阻断离线导入。
2. **过期**：`sa_connector_credential_binding` 加 `expires_at`、`last_verified_at`、`verify_status`；
   `ConnectorCredentialBindingCommand` 增 `expiresAt`（可选）。
3. **失效检测**：新增定时/按需 `detectInvalid` 路径——对到期或验证失败的绑定置 `INVALID`（落地证据 B 的枚举）；
   `requireActiveCredentialBinding` 收紧为「存在 且 ACTIVE 且未过期 且 verify_status!=FAILED」。

> 决策点：验证端口**失败默认拒绝**（fail-closed），但**端口缺失默认放行 + 标记 unverified**（fail-open）。
> 前者是安全要求（坏凭证不该 ACTIVE），后者是可用性妥协（离线/未配验证器时不阻断导入流程）。
> 两种「失败」语义不同，必须在实现里区分，否则要么挡住正常导入、要么放过坏凭证。

#### 3.4.3 代码骨架

```java
// ports/outbound/agent/ConnectorCredentialVerificationPort.java
public interface ConnectorCredentialVerificationPort {
    CredentialVerificationResult verify(String tenantId, String connectorId,
                                        String operationId, CredentialAuthType authType,
                                        String credentialRef);
    static ConnectorCredentialVerificationPort skip() {           // 缺省：跳过但标记 unverified
        return (t, c, o, a, r) -> CredentialVerificationResult.skipped();
    }
}

// KernelOpenApiConnectorImportService.bindCredential 增量（save 前）
CredentialVerificationResult vr = verificationPort.verify(
        connector.tenantId(), connectorId, operationId, authType, credentialRef);
if (vr.failed()) {                                                // fail-closed
    throw new IllegalStateException("credential verification failed: " + vr.reason());
}
ConnectorCredentialBindingStatus initialStatus = ConnectorCredentialBindingStatus.ACTIVE;
Instant lastVerifiedAt = vr.verified() ? now : null;             // skipped → null
ConnectorCredentialBinding binding = credentialBindingRepository.save(
        new ConnectorCredentialBinding(bindingId(...), connector.tenantId(), connectorId,
                operationId, authType, credentialRef, initialStatus, boundBy, now,
                /*rotatedAt*/ null, safeCommand.expiresAt(), lastVerifiedAt, vr.status()));
```

```java
// ConnectorCredentialBinding 增方法
public ConnectorCredentialBinding invalidate(Instant at, String reason) {
    return new ConnectorCredentialBinding(..., ConnectorCredentialBindingStatus.INVALID, ...);
}
public boolean isUsable(Instant now) {
    return status == ConnectorCredentialBindingStatus.ACTIVE
            && (expiresAt == null || expiresAt.isAfter(now));
}
```

```java
// requireActiveCredentialBinding 收紧
boolean usable = credentialBindingRepository
        .findActive(connector.tenantId(), op.connectorId(), op.operationId(), op.authType())
        .filter(b -> b.isUsable(clock.instant()))      // 不止存在，还要可用
        .isPresent();
if (!usable) throw new IllegalStateException(ACTIVE_CREDENTIAL_BINDING_REQUIRED);
```

#### 3.4.4 测试

- 单元（负向）：`verify` 返回 failed → `bindCredential` 抛错，仓库**无** `ACTIVE` 记录落库。
- 单元：`verify` skipped → 绑定 `ACTIVE` 但 `verify_status==SKIPPED`、`last_verified_at==null`，审计含 unverified 标记。
- 单元（负向）：已过期绑定 `isUsable(now)==false`，`enableOperation` 抛 `ACTIVE_CREDENTIAL_BINDING_REQUIRED`。
- 单元：`detectInvalid` 对到期绑定置 `INVALID`，状态可被后续查询观察到。
- 回归：`verify` verified + 未过期 → 启用算子成功，行为同现状。

---

## 4. 数据模型 / DDL

> 全部为**增量**变更（`ALTER TABLE ... ADD COLUMN`），不破坏既有数据；新列给安全默认值。
> RLS 策略由块 A 统一对带 `tenant_id` 的表施加，本文档新增列不改变 RLS 适用面。

### 4.1 `sa_secret_ref`（P0-1）

```sql
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS name         VARCHAR(128);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS secret_type  VARCHAR(32);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS masked_hint  VARCHAR(64);   -- 不可逆预览
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS expires_at   TIMESTAMP;
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS rotated_by   VARCHAR(64);
ALTER TABLE sa_secret_ref
  ADD CONSTRAINT chk_sa_secret_ref_status CHECK (status IN ('ACTIVE','ROTATED','EXPIRED','DISABLED'));
-- 运行时读取按 (tenant_id, secret_ref, status) 命中
CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant_status
  ON sa_secret_ref(tenant_id, status, secret_ref);
```

### 4.2 `sa_connector_credential_binding`（P0-4）

```sql
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS expires_at       TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS verify_status    VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE sa_connector_credential_binding
  ADD CONSTRAINT chk_sa_ccb_verify_status CHECK (verify_status IN ('UNVERIFIED','SKIPPED','VERIFIED','FAILED'));
```

### 4.3 沙箱（P0-3）

沙箱策略维度（路径/资源限额）**本期不落表**——它们是「每次请求的入参」，由 `SandboxPolicyRequest` 携带、
策略端即时判定，不需要持久化新表。`sa_sandbox_session`（:1395-1405）已有 `tenant_id`，归属校验直接复用，无 DDL 变更。
若后续要持久化「每会话允许的工作目录/限额档位」，再作为 P1 增列（**[架构决策]** 是否需要）。

---

## 5. API 契约（新增端点）

统一返回包络沿用现有 `ApiResponse<T>`（`ApiResponses.requireService`），controller 用 `ObjectProvider` 懒加载，
受 `AdvancedFeatureGate` 门控（`SECRET_MANAGEMENT` / `CONNECTOR_MANAGEMENT`）。所有端点 admin-only。

### 5.1 密钥（P0-1）— `SeahorseSecretController` 扩展

| 方法 | 路径 | 入参 | 返回（脱敏） |
|------|------|------|-------------|
| GET  | `/api/secrets` | `tenantId`(必), `current`, `size`, `status?` | `PageResult<SecretMetadata>`，仅元数据 + `maskedHint` |
| GET  | `/api/secrets/{secretRef}` | path `secretRef` + `tenantId`(query, 必) | `SecretMetadata`（脱敏） |
| POST | `/api/secrets/{secretRef}/rotate` | body `{ tenantId, newSecretValue, metadataJson? }` | 新 `SecretMetadata` |
| POST | `/api/secrets/{secretRef}/disable` | body `{ tenantId }` | 置 `DISABLED` 后的 `SecretMetadata` |
| POST | `/api/secrets`（保持） | `{ tenantId, secretValue, metadataJson, name?, secretType?, expiresAt? }` | `SecretMetadata` |

> **铁律**：以上任何响应体**永不含明文**。`secretValue`/`newSecretValue` 仅出现在请求体。
> 列表/详情返回 `maskedHint`（如 `sk-…9F2A`）供人识别，但不可逆推。

### 5.2 连接器凭证（P0-4）— `SeahorseOpenApiConnectorController` 扩展

| 方法 | 路径 | 变化 |
|------|------|------|
| PUT  | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 请求体加可选 `expiresAt`；**保存前 verify**，失败返回 4xx + 错误码 |
| GET  | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 返回项增 `expiresAt`/`lastVerifiedAt`/`verifyStatus`（不含 credentialRef 明文值，本就只是引用） |

### 5.3 ACL（P0-2）

**无新增端点**。ACL 管理端点（`/api/resource-acl-rules*`）已存在（`SeahorseResourceAclController`），
本 P0 只补「执行点接通」，是装配层变更，对外 API 不变。

---

## 6. 前端影响

### 6.1 密钥脱敏展示（P0-1）

- `frontend/src/services/securityGovernanceService.ts` 已有 `SecretItem`（:42-49）与 `createSecret`（:145），
  需补 `listSecrets/getSecret/rotateSecret/disableSecret` 四个函数，对接 §5.1 端点。
- 列表页渲染 `maskedHint` 而非真实值；详情页不提供「查看明文」入口（后端也不返）。
- 轮转交互：弹窗输入新值 → 调 `rotate` → 成功后列表刷新，旧引用显示 `ROTATED` 标签。
- 过期/禁用：列表展示 `status` + `expiresAt`，临期高亮。

### 6.2 ACL 强制后的错误提示（P0-2）

- 工具调用因 ACL 被拒时，后端返回错误码 `RESOURCE_FORBIDDEN`（reason 含 `RESOURCE_ACL_DENY:<type>:<code>`）。
- 前端在 Agent 运行/工具调用结果区，将该错误码映射为中文友好提示，例如：
  「该操作访问的资源未授权（资源 ACL 拒绝），请联系管理员在『资源 ACL』中授权后重试。」
- 不泄露被拒资源的敏感细节，只给可操作指引。

### 6.3 连接器凭证（P0-4）

- 绑定表单加「有效期（可选）」；提交后若 verify 失败，展示后端返回的失败原因（如「凭证无法调通目标 API」）。
- 绑定列表展示 `verifyStatus`（已验证/未验证/失败）与 `expiresAt`，失效/过期项以警示色标注。

---

## 7. 任务清单（P0）

**P0-1 密钥**
- [ ] DDL：`sa_secret_ref` 增 `name/secret_type/masked_hint/status/expires_at/rotated_by` + 约束 + 索引（§4.1）
- [ ] domain：新增 `SecretStatus`；`SecretMetadata` 增字段
- [ ] ports：`SecretManagementInboundPort` 增 `page/get/rotate/disable`；新增 `SecretMetadataQueryPort`、`SecretQuery`、`SecretRotateCommand`、`SecretMetadataPage`
- [ ] ports（破坏性）：`SecretStorePort.getSecret` 增 `tenantId` 参数
- [ ] service：`KernelSecretManagementService` 实现 `page/get/rotate/disable`（admin + 强制租户过滤）
- [ ] adapter：`JdbcSecretStoreAdapter` 读取加 `tenant_id`/`status`/`expires_at` 条件；实现元数据查询
- [ ] 调用方改造：`OAuthCredentialProvider`、`SecretStoreCredentialProvider`、`McpHttpAutoConfiguration` 传 `tenantId`（**[依赖块A]** TenantContext）
- [ ] controller：`SeahorseSecretController` 增 4 端点（§5.1）
- [ ] 前端：`securityGovernanceService.ts` 增 4 函数 + 列表/轮转/禁用 UI

**P0-2 资源 ACL**
- [ ] 新增 `AclBridgingToolResourceAccessPort implements ToolResourceAccessPort`
- [ ] 装配 bean（`@ConditionalOnBean(ResourceAccessPolicyPort.class)`）+ 核对 `@AutoConfigureAfter` 顺序
- [ ] 前端：`RESOURCE_FORBIDDEN` → 中文错误提示映射

**P0-3 沙箱**
- [ ] domain：新增 `SandboxResourceLimits`；`SandboxPolicyReasonCode` 增 `FILESYSTEM_PATH_DENIED/RESOURCE_LIMIT_EXCEEDED/PROCESS_OWNER_MISMATCH`
- [ ] ports：`SandboxPolicyRequest` 增 `requesterUserId/requestedPaths/resourceLimits`
- [ ] policy：`DefaultSandboxPolicyPort` 加 FS 边界（含路径归一化）+ 资源限额判定
- [ ] service：`KernelSandboxRuntimeService` 会话/制品归属校验（**[依赖块A]** TenantContext）

**P0-4 连接器凭证**
- [ ] DDL：`sa_connector_credential_binding` 增 `expires_at/last_verified_at/verify_status` + 约束（§4.2）
- [ ] ports：新增 `ConnectorCredentialVerificationPort`（含 `skip()` 缺省）
- [ ] domain：`ConnectorCredentialBinding` 增 `expiresAt/lastVerifiedAt/verifyStatus` 字段 + `invalidate()/isUsable()`
- [ ] service：`bindCredential` 保存前 verify（fail-closed）；`requireActiveCredentialBinding` 收紧为 `isUsable`
- [ ] command：`ConnectorCredentialBindingCommand` 增 `expiresAt`
- [ ] 前端：绑定表单有效期 + verify 失败提示 + 列表状态展示

**横切**
- [ ] 负向测试套件（§8）全绿
- [ ] `AutoConfiguration.imports` 装配回归（六层顺序未破）

---

## 8. 测试策略（重点：越权 / 泄露的负向测试）

安全方案的核心不是「正向能用」，而是「**越权被拦、泄露被堵**」。以下负向用例为验收硬门槛：

| 编号 | 类型 | 场景 | 期望（被拦/被堵的证据） |
|------|------|------|----------------------|
| N-1 | 跨租户读密钥 | `getSecret(tenantB, refOfTenantA)` | `Optional.empty()`，SQL 命中 0 行 |
| N-2 | 密钥明文泄露 | `page/get` 响应序列化 | JSON 断言**不含**原始密文，仅 `maskedHint` |
| N-3 | 过期密钥使用 | `status=EXPIRED` 的 ref 运行时读取 | 返回空，凭证解析失败 |
| N-4 | ACL 越权工具调用 | agent 携他人 `resourceRef` 调工具 | `invoke` 返回 `RESOURCE_FORBIDDEN`，真实 `ToolPort.invoke` **未被调用**（spy 验证） |
| N-5 | ACL DENY 规则命中 | 存在 DENY 规则 | 桥接器返回 `allowed=false`，reason 含 `RESOURCE_ACL_DENY` |
| N-6 | 沙箱读敏感路径 | `requestedPaths=[/etc/passwd]` | `FILESYSTEM_PATH_DENIED` |
| N-7 | 沙箱路径绕过 | `/tmp/work/../../etc/shadow` | 归一化后仍 `FILESYSTEM_PATH_DENIED` |
| N-8 | 沙箱跨租户制品 | `listArtifacts(他租户 sessionId)` | 抛 tenant mismatch，0 制品 |
| N-9 | 沙箱超限请求 | `resourceLimits` 超档位 | `RESOURCE_LIMIT_EXCEEDED` |
| N-10 | 坏凭证绑定 | `verify` 返回 failed | `bindCredential` 抛错，仓库无 ACTIVE 记录 |
| N-11 | 过期凭证启用 | 绑定 `expires_at` 已过 | `enableOperation` 抛 `ACTIVE_CREDENTIAL_BINDING_REQUIRED` |

**分层**：N-1/2/3/5/6/7/9/10/11 为 kernel 单元测试（快、确定）；N-4/N-8 需 starter 层集成测试（验证装配与端到端阻断）。
**正向回归**：每个 P0 至少 1 条「合法路径行为不变」用例，防止加固误伤（§3.x 末「回归」项）。

---

## 9. 验收标准

1. **密钥**：跨租户读取（N-1）100% 返回空；任意查询端点响应不含明文（N-2）；轮转后旧引用 `ROTATED`、新引用 `ACTIVE`；过期引用不可用（N-3）。
2. **ACL**：存在 `ResourceAccessPolicyPort` bean 时，`ToolResourceAccessPort` 装配为 `AclBridgingToolResourceAccessPort`（非 `allowAll`）；DENY 规则命中的工具调用被阻断且真实工具未执行（N-4）。
3. **沙箱**：敏感路径/绕过路径/超限/跨租户制品四类负向用例（N-6~N-9）全部被拒；合法请求行为不变。**显式声明**：本期资源限制为「策略层拒绝越界请求」，内核级硬隔离随 P1 容器 runtime 交付。
4. **连接器**：verify 失败的绑定不落 `ACTIVE`（N-10）；过期绑定无法启用算子（N-11）；`verify_status` 可观测。
5. **分层合规**：所有改造遵守六边形（domain 在 kernel、决策在 application、IO 在 adapter）；新增 bean 的 `@AutoConfigureAfter` 经核对，`AutoConfiguration.imports` 六层装配回归通过。
6. **多租户绑定**：所有「按 tenant 过滤/校验」的点，租户来源为 **[依赖块A]** `TenantContext.getTenantId()`，无硬编码 `DEFAULT_TENANT_ID` 残留。

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| `SecretStorePort.getSecret` 改签名 | 破坏 3 处调用方编译 | 一次性改齐（§11 调用方清单）；CI 编译即暴露遗漏 |
| 依赖块 A 未就绪 | TenantContext 取不到租户 | P0 代码以「显式 tenantId 参数」穿透端口，块 A 仅负责在 web 层注入；可先用请求参数/JWT 临时桥接联调 |
| ACL 桥接装配顺序错判 | `@ConditionalOnBean` 误判缺失 → 仍 allowAll | 落地核对 `@AutoConfigureAfter(RegistryAutoConfiguration)`；加装配测试 N-4 兜底 |
| ACL 全量强制致误拦 | 历史无 ACL 规则的资源被 `DefaultResourceAccessPolicyPort` 默认决策影响 | 桥接器复用现有 `ResourceAccessPolicyPort` 链（ACL 命中走规则、未命中走 default delegate），与 context-pack 路径同语义，行为一致可预期 |
| 沙箱「资源限制」被误读为硬隔离 | 安全承诺虚高 | 文档/验收/PR 描述统一声明 P0=策略层、P1=容器强制 |
| 凭证 verify 引入外呼延迟/失败 | 绑定流程变慢/受外部影响 | verify 端口缺失时 fail-open + 标记 unverified；verify 存在时 fail-closed；超时按失败处理但可配 skip |
| 路径归一化不彻底 | FS 边界被符号链接绕过 | 用 `toRealPath()` 解析后比较；N-7 专测绕过 |

---

## 11. 参考文件锚点

**P0-1 密钥**
- `seahorse-agent-kernel/.../application/credential/KernelSecretManagementService.java`（:61-71 仅 create）
- `seahorse-agent-kernel/.../domain/credential/SecretMetadata.java`（无明文元数据载体）
- `seahorse-agent-kernel/.../domain/credential/SecretMetadataPolicy.java`（:33-39 拒明文入元数据）
- `seahorse-agent/ports/inbound/credential/SecretManagementInboundPort.java`（仅 create）
- `seahorse-agent/ports/outbound/credential/SecretStorePort.java`（`getSecret` 无 tenantId）
- `seahorse-agent/ports/outbound/credential/SecretValue.java`（`toString`→`[REDACTED]`）
- `seahorse-agent-adapter-repository-jdbc/.../JdbcSecretStoreAdapter.java`（:49-53 无 tenant；:77-89）
- `seahorse-agent-adapter-web/.../SeahorseSecretController.java`（:53 仅 POST）
- 调用方（getSecret 改签名波及）：`OAuthCredentialProvider.java`（:54,:63）、`SecretStoreCredentialProvider.java`（:43）、`seahorse-agent-adapter-mcp-http/.../McpHttpAutoConfiguration.java`（:71-73）
- 前端：`frontend/src/services/securityGovernanceService.ts`（:42-49 SecretItem；:145 createSecret）
- DDL：`resources/database/seahorse_init.sql`（:1296-1307 sa_secret_ref）

**P0-2 资源 ACL**
- `seahorse-agent-kernel/.../application/agent/LocalToolGatewayPort.java`（:144-218 invoke）
- `seahorse-agent-kernel/.../application/agent/CatalogBackedToolPolicyPort.java`（:158-177 validateResourceAccess→RESOURCE_FORBIDDEN）
- `seahorse-agent/ports/outbound/agent/ToolResourceAccessPort.java`（allowAll 默认）
- `seahorse-agent-kernel/.../application/agent/context/AclBackedResourceAccessPolicyPort.java`（:52-73 引擎）
- `seahorse-agent-kernel/.../application/agent/context/AuditedResourceAccessPolicyPort.java`（:54-60 决策+log）
- `seahorse-agent-kernel/.../application/agent/context/KernelContextPackBuilderService.java`（:95-103 唯一现存执行点）
- `seahorse-agent-kernel/.../application/agent/context/KernelResourceAclManagementService.java`（ACL 规则管理）
- 装配：`seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelAgentAutoConfiguration.java`（:233-244）、`SeahorseAgentKernelRegistryAutoConfiguration.java`（:267-284 引擎装配）

**P0-3 沙箱**
- `seahorse-agent-kernel/.../application/agent/sandbox/DefaultSandboxPolicyPort.java`（:42-55 仅网络）
- `seahorse-agent-kernel/.../application/agent/sandbox/KernelSandboxRuntimeService.java`（:153-206 制品/会话）
- `seahorse-agent-kernel/.../domain/agent/sandbox/SandboxPolicyReasonCode.java`（仅网络码）
- `seahorse-agent/ports/outbound/agent/SandboxPolicyRequest.java`（缺维度）
- `seahorse-agent-kernel/.../domain/agent/sandbox/SandboxArtifact.java`（promptVisible）
- DDL：`resources/database/seahorse_init.sql`（:1395-1438 sandbox 三表）

**P0-4 连接器凭证**
- `seahorse-agent-kernel/.../application/agent/connector/KernelOpenApiConnectorImportService.java`（:195-232 bindCredential；:374-387 requireActiveCredentialBinding）
- `seahorse-agent-kernel/.../domain/agent/connector/ConnectorCredentialBinding.java`（rotate，无 expire/invalidate）
- `seahorse-agent-kernel/.../domain/agent/connector/ConnectorCredentialBindingStatus.java`（含未用的 INVALID）
- `seahorse-agent/ports/outbound/agent/ConnectorCredentialBindingRepositoryPort.java`（findActive 仅查存在）
- `seahorse-agent-adapter-repository-jdbc/.../JdbcConnectorCredentialBindingRepositoryAdapter.java`（:65-83 查询）
- `seahorse-agent-adapter-web/.../SeahorseOpenApiConnectorController.java`（:93-116 绑定端点）
- DDL：`resources/database/seahorse_init.sql`（:1161-1176 sa_connector_credential_binding）

**横切**
- `docs/aegis/plans/2026-06-04-saas-mvp-execution-roadmap.md`（:356-365 域 5 安全 P0；:403 绑定 Phase 1 理由）
- 块 A 契约：`TenantContext`（由块 A 引入，当前仓库未命中）
