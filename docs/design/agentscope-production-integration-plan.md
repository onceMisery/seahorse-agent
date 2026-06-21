# AgentScope 生产级集成落地方案

- 文档状态：落地方案
- 日期：2026-06-20
- 适用分支：`feat/message-tree-spike`
- 关联设计：`docs/design/agentscope-integration-and-loop-refactor.md`
- 目标：把 AgentScope 从“可接入能力”推进到“可灰度、可回滚、可观测、可治理、可验收”的生产级执行后端。

## 0. 当前实施证据

截至 2026-06-21，当前分支已经完成并验证以下内容：

- AgentScope adapter 及上游 kernel 关联测试通过：`mvn -pl seahorse-agent-adapter-agent-agentscope -am test`
  - 结果：kernel 443 个测试，0 失败；AgentScope adapter 83 个测试，0 失败，1 个 live smoke 默认跳过。
- AgentScope application 级路由 smoke 通过：`mvn -pl seahorse-agent-tests -am "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - 结果：`KernelChatInboundService` 在 `ChatMode.AGENT` 下通过真实 `AgentScopeReActExecutor` 执行，返回 `agentscope` 引擎内容，绑定取消句柄，且不执行 RAG pipeline。
- A2A connector 聚焦测试通过：`mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AAgentConnectorTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - 结果：12 个测试，0 失败。
- bootstrap 可执行 jar 构建通过：`mvn -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`。
- 本地 backend 已使用新 jar 重新部署，`/actuator/health` 返回 `{"status":"UP"}`。
- 主机侧验证统一使用 `127.0.0.1`，避免当前 Windows 环境中 `localhost` 优先解析到 IPv6 `::1` 后出现连接挂起。
- shared-secret 真实 E2E 通过：
  - 命令：`.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token -AuthMode shared-secret -MainAgentName seahorse-a`。
  - 主实例 Agent Card 200。
  - 主实例缺失/错误认证返回 401。
  - 主实例正确认证返回 200。
  - 临时远端 Agent Card 200。
  - 临时远端缺失/错误认证返回 401。
  - 临时远端正确认证返回 200。
  - Nacos connector live smoke 通过。
  - 输出 `E2E_RESULT=PASS`。
- tenant-signed 真实 E2E 通过：
  - 命令：`.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token -AuthMode tenant-signed -MainAgentName seahorse-a`。
  - 主实例和远端均验证 HMAC-SHA256 签名。
  - Nacos connector live smoke 通过。
  - 输出 `E2E_RESULT=PASS`。
- E2E 脚本修复了 PowerShell `HMACSHA256::Create()` 在当前环境下产出非 32 字节 HMAC 的问题，改为显式 `HMACSHA256::new(keyBytes)`。
- `AgentScopeA2aE2eScriptContractTests` 已覆盖 tenant-signed 脚本必须使用显式 HMAC-SHA256 构造器。
- `AgentScopeReActAutoConfigurationTests` 已覆盖主应用 component scan 不应在缺少 A2A server 条件时创建 `AgentScopeA2aServerController`。
- `A2aDiscoveryPolicy` 已接入 `AgentScopeA2AAgentConnector`，同租户多候选 Agent Card 会按本地 M3 `mode/namespace/group/clusterName` 匹配分数选择优先目标。
- `AgentScopeA2AAgentConnectorTests` 已覆盖同租户多候选时优先选择同 M3 cluster。
- Agent Card 已携带 `seahorse:a2a:authMode` 与 `seahorse:a2a:healthUrl` tags，connector 会映射到 `RemoteAgentCard.metadata()`。
- `AgentScopeAgentCardFactoryTests` 与 `AgentScopeA2AAgentConnectorTests` 已覆盖 A2A governance metadata 的注册和解析。
- `A2AAgentResolveRequest` 已支持可选 `version`，并保持原两参构造兼容；`AgentScopeA2AAgentConnectorTests` 已覆盖指定版本优先于 latest、指定版本缺失时不降级 latest，以及 invoke metadata version 参与远端解析。
- Agent Card 已支持可选 `seahorse.agentscope.a2a.registration-ttl`，配置后写入 `seahorse:a2a:registeredAt` 和 `seahorse:a2a:expiresAt` tags；`A2aDiscoveryPolicy` 已按 `expiresAt` 避免在多候选时选择过期实例。
- A2A 重复注册策略已落地：`AgentScopeAgentCardRegistrarTests` 已覆盖同 tenant/name/version/url 幂等刷新、同 tenant/name/version 不同 url 默认拒绝、`duplicate-registration-policy=replace` 时允许替换。
- A2A endpoint 生命周期注销已落地：`AgentScopeAgentCardRegistrar` 在注册成功后记录本次 Agent Card 和 Nacos transport 配置，Spring destroy 阶段通过 Nacos 3.x `A2aService.deregisterAgentEndpoint(...)` 注销对应 endpoint；`AgentScopeAgentCardRegistrarTests` 覆盖注销字段与关闭 endpoint 注册时不注销，`AgentScopeReActAutoConfigurationTests` 覆盖 auto-configuration 会把已有 `AiService` 注入 registrar。
- Nacos config center strict startup 启动校验已落地：`AgentScopeConfigCenterStartupValidatorTests` 已覆盖 strict 模式启动前校验 prompt 和 skill repository、prompt 加载失败即启动失败、skill namespace 为空即启动失败、非 strict 模式不阻塞启动。
- AgentScope release gate 脚本已落地：`scripts/agentscope-release-gate.ps1` 串联 AgentScope adapter 测试、application smoke、bootstrap package，并支持可选 shared-secret/tenant-signed live E2E。
- `AgentScopeReleaseGateScriptContractTests` 已覆盖 release gate 脚本必须包含 unit、application smoke、package、shared-secret live 和 tenant-signed live 检查入口。
- bootstrap 可执行 jar 构建再次通过：`mvn -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`。
- AgentScope model bridge 已保持 Seahorse 流式 thinking 语义：`AgentScopeModelBridgeTests` 覆盖底层 `StreamingChatModelPort.onThinking(...)` 会转换成 AgentScope `ThinkingBlock`，`AgentScopeReActExecutorTests` 覆盖 AgentScope thinking event 会继续映射到 Seahorse `StreamCallback.onThinking(...)`。
- AgentScope token usage 传播链路已补齐到流式回调层：
  - `OpenAiCompatibleModelAdapterTests` 覆盖 OpenAI-compatible 流式请求写入 `stream_options.include_usage=true`，并从 SSE `usage` chunk 解析 `prompt_tokens` / `completion_tokens` 到 Seahorse `ChatTokenUsage`。
  - `AgentScopeModelBridgeTests` 覆盖 Seahorse `StreamCallback.onUsage(...)` 会转换成 AgentScope `ChatUsage`。
  - `AgentScopeReActExecutorTests` 覆盖 AgentScope `ModelCallEndEvent.getUsage()` 会继续映射回 Seahorse `StreamCallback.onUsage(...)`。
  - `KernelChatAgentRunStoreTests#shouldRecordAgentModeModelUsageForAgentRun` 覆盖 `ChatMode.AGENT` 下 AgentScope/Agent executor 的 usage 回调会按 run/rollout/user/model 维度写入 `CostUsageRepositoryPort`。
- Agent run metadata snapshot 已补齐：
  - `AgentRun` 与 `AgentRunStartCommand` 已支持 `metadataJson`。
  - `KernelChatInboundService` 在 Agent run 启动前写入 `engine`、agent version、instructions、model config、tool set、skill set、runtime allowed tools 等快照。
  - `AgentRunMetadataContributor` 已作为 kernel 扩展点接入，执行后端可以在 run 启动前贡献不可变 metadata。
  - `AgentScopeRunMetadataContributor` 已在 Nacos config-center 启用时写入 `prompt` 与 `skillRepository` 的 Nacos source/key/version/label/namespace/group/revision 快照。
  - `JdbcAgentRunRepositoryAdapter` 已持久化 `metadata_json`，迁移脚本为 `resources/database/migrations/V37__agent_run_metadata.sql`。
  - `KernelChatAgentRunStoreTests#registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools` 覆盖版本化 skill snapshot 与 runtime tool exposure。
  - `KernelChatAgentRunStoreTests#agentRunMetadataIncludesExecutionBackendPromptSourceSnapshot` 覆盖执行后端 prompt source metadata 合并进 run metadata。
  - `JdbcAgentRunRepositoryAdapterTests#shouldCreateUpdateAndFindRun` 覆盖 metadata round-trip。
- AgentScope/A2A observation 已补强：
  - `AgentScopeReActExecutorTests#streamExecuteRecordsAgentscopeExecuteObservationWithRunDimensions` 覆盖流式 `agentscope.execute` observation。
  - `AgentScopeReActExecutorTests#streamExecuteClassifiesAgentscopeRuntimeErrorsAsRecoverableEvents` 覆盖流式 AgentScope 非审批异常会先映射为 Seahorse `recoverable_error` 事件，再进入 `StreamCallback.onError(...)`。
  - `A2aAgentRemoteInvokerTests#recordsA2aInvokeObservationWithRemoteDimensions` 覆盖 `a2a.invoke` observation。
  - `AgentScopeA2aServerControllerTests#recordsA2aAuthObservationWithAgentDimensions` 覆盖 `a2a.auth` observation。

仍需继续推进的生产级缺口：

- AgentScope RC3 的 `NacosA2aRegistry` 仍无公开 deregister API；Seahorse 已通过 Nacos 3.x `A2aService` 补齐 endpoint deregister，完整 Agent Card 删除仍取决于上游 API。
- M3 元数据、健康优先、指定版本解析、TTL/stale 补偿、重复注册预检和 endpoint 生命周期注销已进入 routing policy/metadata/lifecycle；仍需补齐完整 Agent Card 删除或上游 registry deregister API。
- `engine=agentscope` 已有模块级语义测试和 application 级路由 smoke，且 thinking/content/usage 基础流式语义与 run-level usage 持久化已覆盖；真实模型、长链路 SSE 等价仍需更强的端到端验证。
- Nacos config center 已具备 prompt fallback/strict、skill repository 版本化、strict startup 启动校验和 run metadata source snapshot；剩余缺口是 Nacos/AgentScope 上游若暴露更精确 config revision，需要替换当前以 version/label 表达的等价 revision。
- OTEL 当前复用 `ObservationPort`，核心 AgentScope/A2A observation 已有测试覆盖；直接 OTEL/Studio trace 展示仍按生产环境基础设施联调决定。

## 1. 结论

AgentScope 不应替换 Seahorse 的产品控制面。正确集成方式是：

- Seahorse 继续拥有租户隔离、模型路由、工具审批、配额、审计、记忆治理、SSE/API 契约。
- AgentScope 作为可插拔执行后端，提供 ReAct 执行、A2A 协议、Nacos 服务发现、Nacos 配置中心、Studio/Trace 集成能力。
- `engine=kernel` 保持默认和回滚路径，`engine=agentscope` 作为灰度后端逐步放量。

“完美集成”的工程定义不是没有风险，而是满足以下条件：

- 配置切换即可在 `kernel` 和 `agentscope` 之间回滚。
- AgentScope 执行语义与现有内核契约兼容。
- A2A 调用具备租户级认证、重放保护和明确错误分类。
- Nacos 3.x 同时承担注册中心和配置中心职责。
- M3 元数据不只展示在 Agent Card 中，还参与发现、路由和治理决策。
- 可观测性复用现有 `ObservationPort`，按配置接入 OTEL，不影响默认业务路径。
- 真实 E2E 能证明 Agent Card、认证、Nacos 发现、跨实例 JSON-RPC 调用和清理行为。

## 2. 当前现状

### 2.1 已具备的基础

当前代码已经具备 AgentScope 集成的主要骨架：

- 根 `pom.xml` 已锁定 `io.agentscope:agentscope` 版本为 `2.0.0-RC3`。
- `seahorse-agent-adapter-agent-agentscope` 模块已经存在。
- AgentScope 相关扩展已纳入依赖管理：
  - `agentscope`
  - `agentscope-extensions-nacos-a2a`
  - `agentscope-extensions-a2a-server`
  - `agentscope-extensions-nacos-prompt`
  - `agentscope-extensions-nacos-skill`
  - `agentscope-extensions-studio`
- `docker-compose.yml` 和 `docker-compose.full.yml` 使用 `nacos/nacos-server:v3.2.1`。
- A2A 服务端入口暴露在 `/a2a`：
  - `GET /a2a` 返回 Agent Card。
  - `POST /a2a` 接收 JSON-RPC `message/send`。
- A2A 远端调用已经通过 `A2aAgentRemoteInvoker` 走 JSON-RPC。
- Nacos A2A 注册和发现已经通过 AgentScope 的 `NacosA2aRegistry`、`NacosAgentCardResolver` 接入。
- 配置中心已有 Nacos prompt/skill 的接入点。
- Studio lifecycle hook 已有自动配置入口。
- 可观测性已有 `ObservationPort`，AgentScope 可通过该端口复用现有 Micrometer/Tracing 基础设施。

依赖要求固定如下：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope</artifactId>
    <version>2.0.0-RC3</version>
</dependency>
```

### 2.2 已识别的不足

当前集成还不能称为生产级，主要差距如下：

- A2A 默认共享密钥模式适合本地和内部 smoke，但生产需要租户签名和重放保护。
- Nacos 注册能工作；重复注册、健康优先、陈旧实例治理已形成 Seahorse 侧补偿路径，endpoint 注销通过 Nacos 3.x `A2aService` 在 Spring destroy 阶段执行，完整 Agent Card 删除仍受 AgentScope RC3 API 限制。
- M3 元数据已经能出现在 Agent Card/metadata 中，但需要进入发现和路由策略。
- AgentScope executor 需要进一步证明与 kernel executor 的语义等价；当前已覆盖基础 content/thinking/usage 流式输出、工具审批和 run-level token/cost 持久化，错误分类仍需继续补齐。
- Nacos config center 需要明确 strict/fallback 启动语义、prompt/skill 版本快照和热更新边界。
- OTEL 不应硬依赖引入；应通过现有观察端口按配置启用。
- 真实 E2E 需要成为可重复命令，不能依赖手工验证。

### 2.3 已知外部限制

AgentScope `2.0.0-RC3` 的 `NacosA2aRegistry` 当前暴露注册能力，但未暴露明确的 deregister/unregister API。当前采用分层策略：

- 短中期：在 Seahorse 侧用健康检查、TTL、实例版本和 resolver 过滤规避陈旧实例。
- 已落地：通过 Nacos Java SDK 的 `A2aService.deregisterAgentEndpoint(...)` 封装受控 endpoint 注销适配器。
- 后续增强：推动 AgentScope registry 暴露完整 Agent Card deregister/delete API，或在 Nacos SDK 提供稳定 card 删除语义后接入。

在该限制解除前，验收标准把“陈旧实例不被选中”和“本实例停止时注销 endpoint”作为必要目标，把“删除完整 Agent Card 记录”作为受 AgentScope RC3 API 限制的增强项。

## 3. 实现意图

### 3.1 控制面和执行面分离

Seahorse 继续作为控制面：

- 负责租户上下文、用户上下文、模型策略、工具策略、审批、审计、配额、记忆、前端流式协议。
- 对外保持现有 Web/SSE/API 契约。
- 通过配置选择执行后端。

AgentScope 作为执行面：

- 执行 ReAct 过程。
- 暴露 Agent Card 和 A2A JSON-RPC。
- 使用 Nacos 3.x 做服务注册发现和配置中心。
- 对接 Studio/Trace 能力。

### 3.2 单一执行抽象

所有执行后端必须通过同一端口进入：

```text
Web/API/SSE
  -> Kernel Application Service
  -> ReActExecutorPort
      -> Kernel ReAct Executor
      -> AgentScope ReAct Executor
```

约束：

- `ReActExecutorPort` 是执行后端唯一选择点。
- `AgentLoopRequest`、`AgentLoopResult`、`StreamCallback`、`StreamCancellationHandle` 保持兼容。
- 前端不感知后端是 kernel 还是 agentscope。
- 回滚只需要配置 `seahorse.agent.executor.engine=kernel`。

### 3.3 A2A 安全边界

A2A 不是内部裸 RPC，必须具备租户级安全模型：

- `GET /a2a` Agent Card 可以公开，用于发现。
- `POST /a2a` 必须认证。
- 生产模式使用 tenant-signed HMAC。
- 所有远端调用必须携带 tenant、agent、timestamp、nonce、body hash、signature。
- 服务端必须校验租户匹配、签名正确、时间窗口合法、nonce 未重放。
- 跨租户发现和跨租户调用必须被拒绝。

### 3.4 Nacos 双角色

Nacos 3.x 同时承担两个职责：

- 注册中心：Agent Card 注册、发现、健康元数据、M3 元数据。
- 配置中心：prompt、skill、版本、label、strict/fallback 策略。

这两个职责需要共享一致的 namespace/group/M3 配置，避免“发现走一套命名，配置走另一套命名”。

### 3.5 M3 模式

M3 模式不应停留在展示层。目标行为：

- 注册时携带 M3 元数据。
- 发现时读取 M3 元数据。
- 路由时优先同租户、同 namespace、同 group、同 cluster。
- 异常时只允许在同租户范围内降级。

## 4. 目标架构

```text
Frontend / API
    |
Seahorse Web Controllers
    |
Kernel Application Services
    |
ReActExecutorPort
    |---------------- Kernel Executor
    |
    |---------------- AgentScope Executor
                         |
                         |-- Model Bridge
                         |   复用 Seahorse 模型路由、凭据、超时、token/cost 记录
                         |
                         |-- Tool Bridge
                         |   复用 Seahorse 工具策略、审批、网关、审计
                         |
                         |-- Memory Bridge
                         |   复用 Seahorse 会话上下文和长期记忆治理
                         |
                         |-- A2A Server
                         |   GET Agent Card / POST JSON-RPC message/send
                         |
                         |-- A2A Client
                         |   通过 Nacos 解析远端 Agent Card 并发起 JSON-RPC
                         |
                         |-- Nacos Config Center
                         |   prompt / skill / version / label / fallback
                         |
                         |-- Observation / Studio
                             复用 ObservationPort，按配置导出 OTEL/Studio 事件
```

## 5. 目标配置模型

```yaml
seahorse:
  agent:
    executor:
      engine: kernel # kernel | agentscope

  agentscope:
    a2a:
      enabled: true
      register-enabled: true
      tenant-id: tenant-a
      agent-name: seahorse-a
      version: 1.0.0
      description: Seahorse Agent
      path: /a2a
      url: http://backend:9090/a2a
      preferred-transport: jsonrpc

      auth-mode: shared-secret # none | shared-secret | tenant-signed
      auth-header-name: X-Seahorse-A2A-Token
      shared-secret: ${SEAHORSE_A2A_SHARED_SECRET:}
      allowed-timestamp-skew: 5m
      nonce-ttl: 10m
      registration-ttl: 0s # 0 disables TTL stale filtering metadata
      duplicate-registration-policy: reject # reject | replace

    nacos:
      server-addr: nacos:8848
      namespace: public
      group: DEFAULT_GROUP
      m3:
        enabled: true
        mode: M3
        namespace: seahorse-agent
        group: DEFAULT_GROUP
        cluster-name: local

    config-center:
      enabled: true
      prompt-key: seahorse.agent.prompt
      prompt-version: stable
      prompt-label: default
      skill-namespace: agent-skills
      skill-version: v1
      skill-label: stable
      strict-startup: false

    studio:
      enabled: false
      studio-url: http://localhost:3000
      tracing-url: http://localhost:16686

  observability:
    tracing:
      enabled: false
      otlp-endpoint: http://otel-collector:4317
      sampling-rate: 0.1
```

默认策略：

- 本地开发可以使用 `shared-secret`。
- 生产环境使用 `tenant-signed`。
- `none` 只允许本地临时调试，不进入发布配置。
- `strict-startup=false` 时 Nacos 配置中心不可用仍可使用本地 fallback 启动。
- `strict-startup=true` 时关键 prompt/skill 配置缺失必须失败启动。

## 6. A2A 认证方案

### 6.1 模式

| 模式 | 用途 | 行为 |
| --- | --- | --- |
| `none` | 本地临时调试 | 不校验认证；发布配置禁止使用 |
| `shared-secret` | 本地 E2E、内部兼容 | 校验 `X-Seahorse-A2A-Token` |
| `tenant-signed` | 生产默认 | 校验租户签名、时间窗口、nonce、防重放 |

### 6.2 tenant-signed 请求头

```text
X-Seahorse-A2A-Tenant: tenant-a
X-Seahorse-A2A-Agent: seahorse-a
X-Seahorse-A2A-Timestamp: 2026-06-20T12:00:00Z
X-Seahorse-A2A-Nonce: 018f2a4a-...
X-Seahorse-A2A-Body-SHA256: <hex sha256>
X-Seahorse-A2A-Signature: <hex hmac sha256>
```

签名载荷：

```text
tenantId + "\n" +
agentName + "\n" +
timestamp + "\n" +
nonce + "\n" +
bodySha256
```

签名算法：

```text
HMAC-SHA256(secret, payload)
```

### 6.3 错误分类

| 场景 | HTTP 状态 |
| --- | --- |
| 缺少认证信息 | 401 |
| 签名错误 | 401 |
| timestamp 超出允许窗口 | 401 |
| 租户或 agent 不匹配 | 403 |
| nonce 重放 | 409 |
| 服务端未配置 secret | 503 |

## 7. Nacos 注册中心方案

### 7.1 注册元数据

Agent 注册到 Nacos 时必须包含：

```text
tenantId
agentName
version
protocolVersion
jsonrpcUrl
preferredTransport
authMode
healthUrl
registeredAt
expiresAt
m3.mode
m3.namespace
m3.group
m3.clusterName
```

Agent Card 中也必须能看到对应的 tenant/M3 信息，便于诊断和跨系统兼容。

### 7.2 重复注册策略

| 场景 | 策略 |
| --- | --- |
| 同 tenant、agent、version、url | 幂等刷新 |
| 同 tenant、agent、version、不同 url | 默认拒绝或按配置替换 |
| 同 tenant、agent、不同 version | 允许共存 |
| `setAsLatest=true` | 标记该版本为优先版本 |

配置项：

```yaml
seahorse:
  agentscope:
    a2a:
      duplicate-registration-policy: reject # reject | replace
```

落地规则：

- `reject` 是默认策略，避免同一 tenant/name/version 被不同实例静默覆盖。
- `replace` 仅允许在受控发布或故障切换窗口使用，注册器继续调用 AgentScope `registerAgent(...)`，由 Nacos/AgentScope 的写入语义完成替换。
- 注册器通过 `AgentCardResolver` 做 best-effort 预检；resolver 不存在或查询失败时不阻塞注册，避免 Nacos 短暂不可用导致实例无法启动。
- 该策略不能替代完整 Agent Card 删除。AgentScope RC3 暂无公开 card deregister API，因此重复注册治理、TTL/stale 过滤和 Nacos endpoint deregister 共同作为短中期补偿。

### 7.3 解析和路由策略

解析远端 agent 时按以下顺序筛选：

1. 强制同 tenant。
2. 优先 exact agent/version。
3. 未指定 version 时选择 latest。
4. 优先同 M3 namespace/group/cluster。
5. 优先健康实例。
6. 同租户内允许降级，不允许跨租户降级。
7. 无可用实例时返回明确发现失败。

### 7.4 陈旧实例治理

由于 AgentScope RC3 暂未暴露完整 Agent Card 注销 API，落地策略分三层：

- 短期：resolver 根据 health、metadata、TTL 或调用失败结果避免选中陈旧实例。
  - 配置 `seahorse.agentscope.a2a.registration-ttl` 后，Agent Card 会携带 `registeredAt` 和 `expiresAt`。
  - 多候选解析时，`A2aDiscoveryPolicy` 会优先选择未过期实例；未携带 TTL 的实例保持中性评分，避免破坏旧注册数据。
- 已落地：`AgentScopeAgentCardRegistrar.destroy()` 对注册成功的当前实例调用 Nacos 3.x `A2aService.deregisterAgentEndpoint(...)`，注销字段与 AgentScope `NacosA2aRegistry` 注册 endpoint 的字段保持一致。
- 后续：升级 AgentScope API 或 Nacos SDK 稳定删除语义后接入完整 Agent Card deregister。

验收以“停止远端实例后 endpoint 被注销，且 resolver 不会继续成功选中不可用实例”为准。

## 8. Nacos 配置中心方案

### 8.1 Prompt 配置

配置项：

```text
prompt-key
prompt-version
prompt-label
strict-startup
local fallback prompt
```

行为：

- 新 run 使用当前加载的 prompt。
- 已开始的 run 固定使用启动时 prompt snapshot。
- run metadata 记录 prompt key/version/label/source。
- Nacos 不可用且 `strict-startup=false` 时使用本地 fallback。
- Nacos 不可用且 `strict-startup=true` 时启动失败并输出明确错误。
- strict startup 启动阶段必须主动加载配置的 prompt key，缺失、空值或 Nacos 异常均应阻止应用继续服务流量。

### 8.2 Skill 配置

配置项：

```text
skill-namespace
skill-version
skill-label
strict-startup
local fallback skills
```

行为：

- 新 run 使用当前 skill snapshot。
- Tool exposure 必须经过 Seahorse 工具策略过滤。
- skill 版本写入 run metadata，便于审计和回放。
- strict startup 启动阶段必须主动读取配置的 skill namespace，仓库不可用或返回空 skill 列表时阻止启动。

## 9. AgentScope Executor 语义对齐方案

### 9.1 输入输出契约

`AgentScopeReActExecutor` 必须完整遵守现有执行端口：

- 输入：`AgentLoopRequest`
- 输出：`AgentLoopResult`
- 流式：`StreamCallback`
- 取消：`StreamCancellationHandle`
- 引擎标识：`engineId() = "agentscope"`

### 9.2 模型桥接

AgentScope 模型调用不能绕过 Seahorse 模型治理：

- 模型 ID、base url、api key、超时、重试策略由 Seahorse 提供。
- token usage 必须从模型适配器通过 `StreamCallback.onUsage(...)` 保留到 AgentScope `ChatUsage`，并从 AgentScope `ModelCallEndEvent` 映射回 Seahorse 回调；Agent 模式下的 usage 必须继续写入 `CostUsageRepositoryPort`，保留 run/rollout/user/model 维度供成本汇总和配额治理使用。
- 模型错误分类映射到现有错误体系。
- streaming delta 映射为现有 SSE 内容事件。

### 9.3 工具桥接

AgentScope tool 调用必须复用 Seahorse 工具体系：

- 工具 schema 从 Seahorse ToolRegistry 暴露。
- 工具执行走 Seahorse ToolGateway。
- 审批仍由 Seahorse ApprovalPort 决定。
- 需要审批时返回 `WAITING_APPROVAL`，不在 AgentScope 内绕过。
- 工具结果和异常转换为现有 Step/ToolCall 结构。

### 9.4 记忆桥接

记忆归 Seahorse 治理：

- 会话历史映射成 AgentScope message context。
- RAG/长期记忆仍通过 Seahorse 检索和审计。
- AgentScope 不直接写长期记忆。
- memory snapshot 信息写入 run metadata。

### 9.5 退出原因映射

| AgentScope 场景 | Seahorse exitReason |
| --- | --- |
| 正常生成最终回答 | `FINAL_ANSWER` |
| 达到 step/token/time 限制 | `TRUNCATED` |
| 工具需要人工审批 | `WAITING_APPROVAL` |
| 模型或协议错误 | 失败异常，保留明确错误分类 |

## 10. 可观测性和 OTEL 方案

### 10.1 默认策略

默认不直接展示 OTEL 专属能力，也不引入强制 OTEL 依赖。AgentScope 集成先复用现有 `ObservationPort`：

- `ObservationPort` 为 noop 时不改变业务行为。
- 已启用 Micrometer/Tracing 时自动产出指标或 trace。
- OTEL export 由现有基础设施配置决定。

### 10.2 Span/Metric 命名

建议观察点：

```text
agentscope.execute
a2a.auth
a2a.invoke
nacos.register
nacos.resolve
config.fetch
model.turn
tool.call
```

建议属性：

```text
tenantId
agentName
engine
runId
remoteAgent
protocolVersion
a2a.authMode
m3.mode
m3.namespace
m3.group
m3.clusterName
```

### 10.3 Studio

Studio 只在显式配置时启用：

```yaml
seahorse:
  agentscope:
    studio:
      enabled: true
```

要求：

- Studio failure 默认不影响 agent 执行。
- trace id 或 run id 能关联 Studio 调试视图。
- 生产环境可以关闭 Studio，仅保留指标和 trace。

## 11. 真实 E2E 验收方案

### 11.1 E2E 覆盖范围

真实 E2E 需要启动主后端和临时远端后端，覆盖：

- 主实例 `GET /a2a` 返回 200。
- 主实例 Agent Card 包含 tenant/M3 信息。
- 主实例 `POST /a2a` 缺少认证返回 401。
- 主实例 `POST /a2a` 错误认证返回 401。
- 主实例 `POST /a2a` 正确认证返回 200。
- 临时远端实例 `GET /a2a` 返回 200。
- 临时远端实例 Agent Card 包含 tenant/M3 信息。
- 临时远端实例 `POST /a2a` 认证行为正确。
- Nacos 能发现临时远端 agent。
- 本地 connector 能通过 Nacos 解析远端，并完成 JSON-RPC `message/send`。
- E2E 完成后清理临时容器。

### 11.2 推荐命令

```powershell
.\scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9090/a2a `
  -RemotePort 9092 `
  -NacosServer 127.0.0.1:8848 `
  -TenantId tenant-a `
  -SharedSecret seahorse-local-a2a-token
```

### 11.3 期望输出

```text
MAIN_CARD_OK=200
MAIN_POST_NO_AUTH=401
MAIN_POST_WRONG_TOKEN=401
MAIN_POST_AUTH=200
REMOTE_CARD_OK=200
REMOTE_POST_NO_AUTH=401
REMOTE_POST_WRONG_TOKEN=401
REMOTE_POST_AUTH=200
REMOTE_DIRECT_OK
NACOS_CONNECTOR_SMOKE_OK
E2E_RESULT=PASS
```

### 11.4 tenant-signed E2E

生产认证模式需要单独跑：

```powershell
$env:SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE = "tenant-signed"

.\scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9090/a2a `
  -RemotePort 9092 `
  -NacosServer 127.0.0.1:8848 `
  -TenantId tenant-a `
  -SharedSecret seahorse-local-a2a-token `
  -AuthMode tenant-signed `
  -MainAgentName seahorse-a
```

## 12. 分阶段落地计划

### M0：基线 E2E 固化

目标：把当前可运行的 A2A/Nacos 链路变成可重复工程证据。

修改文件：

- 新增 `scripts/agentscope-a2a-e2e.ps1`
- 新增 `seahorse-agent-adapter-agent-agentscope/src/test/java/.../AgentScopeA2aE2eScriptContractTests.java`
- 更新 `docs/ops/agentscope-release-gates.md`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2aE2eScriptContractTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token
```

通过标准：

- 输出 `E2E_RESULT=PASS`。
- 没有残留 `seahorse-a2a-e2e-*` 容器。

### M1：A2A 安全增强

目标：支持生产可用的 tenant-signed 认证。

修改文件：

- 新增 `A2aAuthMode.java`
- 新增 `A2aRequestSigner.java`
- 新增 `A2aRequestAuthenticator.java`
- 修改 `AgentScopeProperties.java`
- 修改 `AgentScopeA2aServerController.java`
- 修改 `A2aAgentRemoteInvoker.java`
- 扩展 `AgentScopeA2aServerControllerTests.java`
- 扩展 `A2aAgentRemoteInvokerTests.java`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2aServerControllerTests,A2aAgentRemoteInvokerTests,AgentScopePropertiesTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

通过标准：

- `none`、`shared-secret`、`tenant-signed` 三种模式均有测试。
- 缺少签名、错误签名、过期 timestamp、重放 nonce、租户不匹配均被拒绝。

### M2：Nacos 服务治理和 M3 路由

目标：让 Nacos 发现结果可治理，M3 元数据参与路由。

修改文件：

- 修改 `AgentScopeAgentCardRegistrar.java`
- 修改 `AgentScopeAgentCardFactory.java`
- 修改 `AgentScopeA2AAgentConnector.java`
- 新增 `A2aDiscoveryPolicy.java`
- 新增 `A2aEndpointHealthProbe.java`
- 新增 `A2aEndpointHealthStatus.java`
- 新增 `A2aDuplicateRegistrationPolicy.java`
- 扩展 `AgentScopeA2AAgentConnectorTests.java`
- 扩展 `AgentScopeAgentCardFactoryTests.java`
- 新增 `AgentScopeAgentCardRegistrarTests.java`
- 扩展 `AgentScopeA2ALiveSmokeTest.java`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AAgentConnectorTests,AgentScopeAgentCardFactoryTests,AgentScopeAgentCardRegistrarTests,AgentScopeA2ALiveSmokeTest" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

通过标准：

- metadata 包含 tenant、agent、version、jsonrpcUrl、transport、M3 字段。
- 解析策略强制同租户。
- 指定 version 时优先解析 exact version，且不得静默降级到 latest。
- 同 M3 cluster 优先。
- 配置 registration TTL 后，过期候选不会在存在未过期候选时被选中。
- 同 tenant/name/version/url 重复注册保持幂等；同 tenant/name/version 不同 url 默认拒绝，`replace` 策略下允许替换。
- 无健康实例时给出明确失败。
- 陈旧实例不会继续作为首选目标。

### M3：AgentScope 执行语义补齐

目标：`engine=agentscope` 成为真实执行后端，而不只是 A2A 演示路径。

修改文件：

- 修改 `AgentScopeReActExecutor.java`
- 修改 `ReActAgentScopeAgentClient.java`
- 修改 `AgentScopeModelBridge.java`
- 修改 `AgentScopeToolFactory.java`
- 扩展 `AgentScopeReActExecutorTests.java`
- 扩展 `AgentScopeModelBridgeTests.java`
- 扩展 `AgentScopeToolFactoryTests.java`
- 在 `seahorse-agent-tests` 增加 `KernelChatInboundServiceAgentScopeEngineSmokeTests`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am test
mvn -pl seahorse-agent-tests -am "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl seahorse-agent-tests test
```

通过标准：

- 同一组任务在 `engine=kernel` 和 `engine=agentscope` 下输出语义兼容。
- `ChatMode.AGENT` 能经 `KernelChatInboundService` 调用真实 `AgentScopeReActExecutor`，并保持 StreamCallback、取消句柄绑定和 RAG bypass 契约。
- 工具审批保持 `WAITING_APPROVAL` 行为。
- AgentScope 流式非审批异常会发出 `recoverable_error` 事件并继续触发 `onError(...)`，避免前端和 run 记录丢失错误分类。
- SSE 事件序列不破坏前端契约。
- 切回 `kernel` 无需代码变更。

### M4：Nacos 配置中心产品化

目标：prompt/skill 配置具备版本、label、fallback 和 strict startup 语义。

修改文件：

- 修改 `AgentScopeProperties.java`
- 修改 `AgentScopePromptConfigCenter.java`
- 修改 `AgentScopePromptProvider.java`
- 修改 `AgentScopeReActAutoConfiguration.java`
- 新增 `AgentRunMetadataContributor.java`
- 新增 `AgentScopeRunMetadataContributor.java`
- 新增 `AgentScopeConfigCenterStartupValidator.java`
- 新增或扩展 `AgentScopePromptConfigCenterTests.java`
- 新增 `AgentScopeRunMetadataContributorTests.java`
- 新增 `AgentScopeConfigCenterStartupValidatorTests.java`
- 扩展 `AgentScopePropertiesTests.java`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopePromptConfigCenterTests,AgentScopeConfigCenterStartupValidatorTests,AgentScopePropertiesTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

通过标准：

- `strict-startup=false` 时 Nacos 不可用能 fallback。
- `strict-startup=true` 时关键配置缺失会失败启动。
- `strict-startup=true` 时 prompt key 和 skill namespace 在启动阶段被主动校验，失败时应用不继续服务流量。
- Agent run metadata 至少记录 engine、agent version、instructions、model config、tool set、skill set 与 runtime allowed tools。
- prompt version/label/source 能进入 run metadata；如果 prompt 由 Nacos config center 加载，还需要记录 config key、namespace、group、revision 或等价版本信息。
- skill repository namespace/version/label/source 能进入 run metadata。

### M5：可观测性和 Studio

目标：AgentScope 执行和 A2A 调用可被追踪、计量、诊断。

修改文件：

- 新增 `AgentScopeObservationSupport.java`
- 修改 `AgentScopeReActExecutor.java`
- 修改 `A2aAgentRemoteInvoker.java`
- 修改 `AgentScopeA2aServerController.java`
- 修改 `AgentScopeAgentCardRegistrar.java`
- 修改 `AgentScopePromptConfigCenter.java`
- 修改 `AgentScopeStudioLifecycle.java`

验收：

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeReActExecutorTests,A2aAgentRemoteInvokerTests,AgentScopeA2aServerControllerTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

通过标准：

- `agentscope.execute`、`a2a.invoke`、`a2a.auth` 有 observation 覆盖。
- 流式 `engine=agentscope` 执行也必须启动并关闭 `agentscope.execute` observation scope。
- Observation noop 时业务行为不变。
- 启用现有 tracing 基础设施后能看到 run/a2a 维度属性。

### M6：发布门禁

目标：避免 AgentScope 集成回归。

修改文件：

- 新增或更新 `docs/ops/agentscope-release-gates.md`
- 新增 `scripts/agentscope-release-gate.ps1`
- 新增 `AgentScopeReleaseGateScriptContractTests`
- 在 CI 中配置 AgentScope unit/focused/live profile

验收：

```powershell
.\scripts\agentscope-release-gate.ps1
mvn -pl seahorse-agent-adapter-agent-agentscope -am test
mvn -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token
```

通过标准：

- PR 至少跑 AgentScope unit/focused tests。
- nightly 跑 live A2A E2E。
- release smoke 必须包含真实 Nacos discovery 和 JSON-RPC invocation。

## 13. 部署和回滚方案

### 13.1 部署前检查

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am test
mvn -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
```

### 13.2 本地 compose 配置

关键环境变量：

```text
SEAHORSE_AGENT_EXECUTOR_ENGINE=agentscope
SEAHORSE_AGENTSCOPE_A2A_ENABLED=true
SEAHORSE_AGENTSCOPE_A2A_REGISTER_ENABLED=true
SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE=shared-secret
SEAHORSE_A2A_SHARED_SECRET=seahorse-local-a2a-token
SEAHORSE_AGENTSCOPE_NACOS_SERVER_ADDR=nacos:8848
```

生产认证改为：

```text
SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE=tenant-signed
```

### 13.3 健康检查

```powershell
curl http://127.0.0.1:9090/actuator/health
curl http://127.0.0.1:9090/a2a
```

通过标准：

- `/actuator/health` 返回 `UP`。
- `/a2a` 返回 Agent Card。
- `POST /a2a` 无认证返回 401。

### 13.4 回滚

立即回滚执行引擎：

```text
SEAHORSE_AGENT_EXECUTOR_ENGINE=kernel
```

关闭 A2A 注册：

```text
SEAHORSE_AGENTSCOPE_A2A_REGISTER_ENABLED=false
```

关闭配置中心：

```text
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_ENABLED=false
```

关闭 Studio：

```text
SEAHORSE_AGENTSCOPE_STUDIO_ENABLED=false
```

## 14. Worktree 开发流程

AgentScope 相关开发应使用 worktree 隔离：

```powershell
git fetch origin main
git worktree add ..\seahorse-agent-agentscope main
cd ..\seahorse-agent-agentscope
git switch -c feat/agentscope-production-integration
```

如果当前分支已经承载相关开发，应先合并 main：

```powershell
git fetch origin main
git merge origin/main
```

提交原则：

- M0-M6 每个里程碑独立提交。
- 不混入 message-tree、role-card、MCP 等无关变更。
- 每个提交包含测试或文档证据。

推荐提交粒度：

```text
test: add agentscope a2a e2e contract
feat: harden agentscope a2a authentication
feat: add nacos m3 discovery policy
feat: align agentscope executor semantics
feat: productize agentscope nacos config center
feat: instrument agentscope execution and a2a calls
docs: add agentscope release gates
```

## 15. 风险和处理策略

| 风险 | 影响 | 处理策略 | 回滚 |
| --- | --- | --- | --- |
| AgentScope executor 与 kernel 行为不一致 | 用户可见行为变化 | kernel/agentscope 等价测试，SSE golden test | `engine=kernel` |
| A2A tenant-signed 误拒合法请求 | 远端 agent 调用失败 | 保留 shared-secret 兼容模式，分阶段灰度 | `auth-mode=shared-secret` |
| Nacos 陈旧实例被选中 | A2A 调用失败或超时 | endpoint deregister、health/TTL/metadata 过滤，停止实例 E2E | 禁用注册或清理实例 |
| Nacos 配置中心不可用 | prompt/skill 无法加载 | `strict-startup=false` fallback | 关闭 config center |
| Studio/OTEL 影响主链路 | 执行不稳定 | 默认 noop/best-effort | 关闭 Studio/tracing |
| AgentScope RC3 registry API 缺少完整 Agent Card 删除 | 服务治理不完整 | Nacos endpoint deregister、resolver 规避陈旧实例，推动上游 API | 保持注册关闭或手工清理 |

## 16. 完成定义

AgentScope 集成达到生产级，需要同时满足：

- `io.agentscope:agentscope:2.0.0-RC3` 依赖固定且可构建。
- Nacos 3.x 在 compose 和生产配置中同时作为注册中心、配置中心。
- `/a2a` Agent Card 公开可访问，包含 tenant/M3 元数据。
- A2A POST 支持 `shared-secret` 和 `tenant-signed`。
- tenant-signed 覆盖签名、timestamp、nonce、防重放、租户校验。
- Nacos 发现强制租户隔离，M3 元数据参与路由偏好。
- A2A 解析支持 exact version，未指定 version 时才使用 latest 语义。
- A2A Agent Card 支持 TTL 元数据，resolver 在多候选时规避已过期实例。
- A2A 注册支持重复注册治理：默认拒绝冲突 url，受控场景可配置 `replace`。
- `engine=agentscope` 能执行真实模型任务，工具审批和 SSE 契约保持兼容。
- Prompt/skill 配置支持 Nacos 版本化和 fallback/strict startup。
- AgentScope/A2A 核心链路接入现有 ObservationPort。
- 真实 E2E 自动化验证 Agent Card、认证、发现、调用、清理。
- 发布门禁要求 unit、focused、live smoke 至少分层覆盖。
- `engine=kernel` 始终是即时回滚路径。

## 17. 建议执行顺序

1. 固化 M0 E2E，先建立真实验收证据。
2. 完成 M1 A2A 安全，先解决生产暴露风险。
3. 完成 M2 Nacos/M3 治理，确保发现和路由确定。
4. 完成 M3 executor 语义等价，证明 AgentScope 可以作为真实执行后端。
5. 完成 M4 config center，使 prompt/skill 进入 Nacos 配置治理。
6. 完成 M5 observation/studio，增强诊断能力但不阻塞主路径。
7. 完成 M6 release gates，将 E2E 和发布证据纳入常规流程。

这个顺序的原因是：先证明链路，再加固安全和治理，然后扩大执行语义，最后补齐运维和发布门禁。
