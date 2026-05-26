# Seahorse Agent 非 Web 端过渡设计整改方案

创建日期：2026-05-26

## 1. 结论

当前项目存在非 Web 端或偏企业/本地 Agent 的过渡设计。它们主要来自旧企业级 AI Infra 规划和部分已落地的领域/API 骨架，不一定都是错误实现，但如果不重新标注边界，会误导后续开发继续朝本地 Agent、沙箱、A2A mesh、企业连接器方向扩张。

整改目标不是立即删除所有相关代码，而是把它们从 C 端 Web AI Infra 基线中隔离出去：

> C 端默认只保留云端 Web Agent Runtime、服务端受控工具、用户数据安全、产物和质量闭环；非 Web、本地、mesh、企业连接器能力统一降级为高级扩展或企业版远期能力。

## 2. 已发现的过渡设计

### 2.1 旧文档中的本地/企业假设

证据：

- `docs/company-agent/Seahorse Agent 企业级 AI Infra 分阶段开发规划.md`
- `docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md`
- `docs/company-agent/ai-infra-phases/07-multi-agent-a2a-mesh.md`

问题：

- Phase 5 把 MCP/OpenAPI/凭据/沙箱作为统一阶段，适合企业系统接入，不适合作为 C 端 Web MVP。
- Phase 7 把本地 Agent-as-Tool、A2A、Agent Mesh 作为阶段目标，会把产品推向企业多 Agent 平台。
- 文档中存在“本地 Java 实现、MCP 远程工具、A2A 远程 Agent、LangGraph 工作流、第三方服务统一进入 Seahorse”的口径，和当前 C 端 Web 产品定位不一致。

整改：

- 旧文档保留为企业版历史规划。
- 新开发默认引用 `docs/company-agent/c-web-ai-infra-phases/`。
- 在旧目录 README 中补充范围声明：Phase 5/7 不是 C 端完成标准。

### 2.2 Sandbox 领域和 API 已进入主代码

证据：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/sandbox`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/sandbox/KernelSandboxRuntimeService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/SandboxRuntimeInboundPort.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSandboxController.java`

风险：

- C 端用户可能误以为平台支持代码执行或文件系统工作区。
- 若 API 默认暴露，后续安全边界会显著扩大。
- `SandboxArtifact` 与 C 端通用 `AgentArtifact` 容易形成两个产物事实源。

整改：

- Sandbox 保留为 Phase 4 高级扩展，默认关闭。
- `consumer-web` 模式下 `/api/sandbox/**` 不对普通用户暴露。
- Sandbox 输出必须经过扫描后转换为 `AgentArtifact`，再给前端展示或下载。
- 禁止任何 `LOCAL_HOST`、host shell、宿主机文件系统语义成为默认 runtime type。

### 2.3 A2A / Agent Mesh 语义已进入运行时枚举和服务

证据：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/runtime/AgentRunTriggerType.java` 包含 `A2A`。
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/handoff/LocalAgentAsToolPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/handoff/KernelAgentHandoffService.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentHandoffController.java`

风险：

- C 端任务模板可能误用 A2A trigger。
- LocalAgentAsTool 容易被理解为本地安装 Agent 能力。
- Handoff 和 remote agent 若缺少租户、上下文裁剪、配额和熔断，会扩大数据传播面。

整改：

- `A2A` trigger 保留兼容，但默认模板不得使用。
- `AgentHandoff` API 在 consumer-web 模式下默认关闭。
- LocalAgentAsTool 改名或文档标注为 `InternalAgentAsTool` 语义，避免“本地桌面 Agent”误读。
- 后续如启用，必须作为企业版扩展并满足 delegated access、trace、quota、circuit breaker。

### 2.4 MCP/OpenAPI/凭据能力偏企业连接器

证据：

- `OpenApiConnectorInboundPort`
- `ConnectorCredentialBindingRepositoryPort`
- `SeahorseOpenApiConnectorController`
- `SeahorseSecretController`
- 旧 Phase 5 文档。

风险：

- C 端用户不应配置企业连接器或粘贴凭据。
- MCP server 由用户随意接入会带来工具权限和数据外泄风险。
- 凭据相关 API 若不是 admin only，会破坏 C 端安全模型。

整改：

- C 端默认只保留平台配置的服务端工具 adapter。
- Connector、Secret、MCP 管理入口必须 admin only 且默认关闭。
- ToolCatalog 可展示工具能力，但不展示 secretRef、token、credential details。
- 写操作工具默认 `APPROVAL_REQUIRED`。

### 2.5 文档和前端管理面仍偏企业控制台

证据：

- `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`
- `frontend/src/services/aiInfraService.ts`

风险：

- 管理面聚合 Agent、审批、工具、SRE、成本、准入、灰度，适合管理员，不等于 C 端用户体验。
- 如果把 Admin Console 当作 AI Infra 完成证明，会遗漏聊天内确认、任务恢复、来源卡片、产物面板、用户记忆中心。

整改：

- Admin Console 保留为运营/管理面。
- C 端新增用户侧任务体验，不复用管理面作为主入口。
- 前端导航区分“用户任务体验”和“管理员 AI Infra 控制面”。

## 3. 整改设计

### 3.1 新增运行模式

建议新增配置：

```yaml
seahorse-agent:
  product-mode: consumer-web
  advanced:
    sandbox-enabled: false
    connector-management-enabled: false
    agent-handoff-enabled: false
    remote-agent-enabled: false
    local-agent-enabled: false
```

枚举：

```text
ProductMode
  CONSUMER_WEB
  PROFESSIONAL_WEB
  ENTERPRISE_PLATFORM
```

默认值必须是 `CONSUMER_WEB`。

### 3.2 API 暴露矩阵

| API | Consumer Web | Professional Web | Enterprise Platform |
| --- | --- | --- | --- |
| `/rag/v3/chat` | 开启 | 开启 | 开启 |
| `/api/agent-runs/**` | 本人资源 | 本人资源 | 按租户/角色 |
| `/api/agent-artifacts/**` | 本人资源 | 本人资源 | 按租户/角色 |
| `/api/approvals/**` | 本人 inline confirmation | 本人 + 管理员 | 管理员/审批人 |
| `/api/sandbox/**` | 关闭 | 可选开启 | 可选开启 |
| `/api/connectors/**` | 关闭 | 关闭 | admin only |
| `/api/secrets/**` | 关闭 | 关闭 | admin only |
| `/api/agent-handoffs/**` | 关闭 | 内部可选 | admin/internal |
| `/api/remote-agents/**` | 关闭 | 关闭 | admin only |

### 3.3 包和命名整改

不要求一次性移动包，但应在文档和后续代码中明确：

| 当前概念 | 整改后语义 |
| --- | --- |
| `LocalAgentAsToolPort` | `InternalAgentAsToolPort` 或企业扩展 |
| `SandboxArtifact` | 高级扩展内部产物，不直接给 C 端 |
| `SandboxRuntimeType` | 不允许默认本地宿主机类型 |
| `AgentRunTriggerType.A2A` | 企业扩展 trigger，不进入 C 端模板 |
| MCP/OpenAPI connector | 平台管理工具，不是用户侧配置 |

### 3.4 文档入口整改

新增或更新：

- C 端当前基线：`docs/company-agent/c-web-ai-infra-phases/README.md`
- 非 Web 端整改：本文档。
- 旧企业目录 README：追加“企业版历史规划，不作为 C 端默认范围”的声明。

后续所有 C 端需求评审必须先引用 C 端阶段文档。

## 4. 可实施任务清单

### Task R1：增加 ProductMode 与高级能力开关

改动范围：

- starter properties。
- web governance configuration。
- controller guard。
- 前端能力开关 service。

验收：

- 默认配置为 `CONSUMER_WEB`。
- sandbox、connector、handoff、remote agent API 默认不可用。

### Task R2：高级 API 路由门禁

改动范围：

- `SeahorseSandboxController`
- `SeahorseOpenApiConnectorController`
- `SeahorseSecretController`
- `SeahorseAgentHandoffController`

验收：

- consumer-web 模式下普通用户访问返回 404 或明确 disabled。
- enterprise-platform 模式下仍受 admin 权限控制。

### Task R3：C 端模板禁用非 Web trigger

改动范围：

- TaskTemplate。
- AgentRunStart validation。
- 前端模板入口。

验收：

- C 端模板不能选择 `A2A` trigger。
- C 端模板不能绑定 remote agent provider。

### Task R4：SandboxArtifact 出口整改

改动范围：

- Sandbox runtime service。
- Artifact service。
- ObjectStorage 下载策略。

验收：

- SandboxArtifact 不直接下载。
- 转换为 AgentArtifact 后必须有 scanStatus。
- 主动内容默认 attachment。

### Task R5：文档和导航整改

改动范围：

- 文档 README。
- Admin Console 文案。
- 用户侧导航。

验收：

- 用户侧页面不出现本地 Agent、A2A、mesh、sandbox 文案。
- 管理面明确是管理员控制台，不是 C 端任务体验。

## 5. 测试建议

后端：

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am test
```

前端：

```powershell
npm run build
```

新增测试：

- `ProductModeDefaultTests`
- `AdvancedControllerGuardTests`
- `ConsumerWebTemplateValidationTests`
- `SandboxArtifactExitPolicyTests`
- `AdminConsoleFeatureFlagTests`

## 6. 退出标准

1. 文档层明确 C 端 Web 当前基线和企业/高级扩展边界。
2. 默认配置不会暴露 sandbox、connector、handoff、remote agent。
3. C 端任务模板不能触发 A2A、remote agent、本地 agent。
4. Sandbox 产物不能绕过 AgentArtifact 安全策略。
5. 前端用户侧不出现非 Web 端能力入口。

