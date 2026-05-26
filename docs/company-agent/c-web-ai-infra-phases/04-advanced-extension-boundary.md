# Phase 4：高级扩展边界

## 1. 阶段目标

把代码解释器、企业连接器、云端沙箱、Agent-as-Tool、A2A、Remote Agent 等高级能力从 C 端 Web 基线中隔离出来，形成可选、受控、可下线的扩展层。

本阶段不是鼓励立即实现这些能力，而是定义边界，避免项目再次滑回本地安装 Agent 或企业 mesh 的旧路线。

## 2. 进入条件

只有满足以下条件后，才进入本阶段：

1. Phase 1 的 Web 任务闭环稳定。
2. Phase 2 的研究型 Web Agent 可用。
3. Phase 3 的用户记忆、文件、反馈、额度和滥用防护已上线。
4. 安全、审计、成本和资源隔离已有明确证据。
5. 有明确业务需求：付费专业版、企业版或受控代码解释器。

## 3. 范围分层

| 能力 | C 端默认 | 专业版可选 | 企业版可选 |
| --- | --- | --- | --- |
| 服务端 Web search/fetch | 是 | 是 | 是 |
| 云端文件转换 | 否 | 是 | 是 |
| 云端代码解释器 | 否 | 是 | 是 |
| OpenAPI 企业连接器 | 否 | 否 | 是 |
| MCP 企业连接器 | 否 | 否 | 是 |
| Agent-as-Tool | 否 | 有条件 | 是 |
| A2A / Remote Agent | 否 | 否 | 是 |
| 本地安装 Agent | 否 | 否 | 否，除非单独产品线 |

## 4. 云端代码解释器边界

### 4.1 允许场景

- CSV/XLSX 数据分析。
- 图表生成。
- 文件格式转换。
- 受控 Python 计算。

### 4.2 禁止场景

- 访问用户本机文件系统。
- 执行宿主机 shell。
- 任意联网。
- 持久运行后台进程。
- 访问内网地址、metadata endpoint、localhost。

### 4.3 领域边界

现有 `kernel.domain.agent.sandbox` 可以保留为扩展域，但对 C 端默认不可见。

必须新增或明确：

| 对象 | 要求 |
| --- | --- |
| `SandboxRuntimeType` | 只能有 `CLOUD_CONTAINER`、`MANAGED_INTERPRETER` 等云端类型；不得默认 `LOCAL_HOST` |
| `SandboxPolicy` | 网络默认 deny，文件系统按 run 隔离 |
| `SandboxArtifact` | 只能转换为 `AgentArtifact` 后出现在用户端 |
| `SandboxSession` | 必须绑定 userId、tenantId、runId、ttl |

## 5. 企业连接器边界

### 5.1 OpenAPI/MCP 接入原则

- 连接器只能由管理员配置。
- 默认 disabled。
- 每个 operation 必须进入 ToolCatalog。
- 每个 operation 必须有 riskLevel、actionType、resourceType。
- 凭据只通过 secretRef 使用，不进入 prompt、trace、前端。
- 高风险写操作默认需要 Approval。

### 5.2 C 端限制

C 端用户不能：

- 粘贴 MCP server URL 后直接启用。
- 上传 OpenAPI spec 后直接执行。
- 配置 bearer token。
- 调用企业写操作。

## 6. Agent-as-Tool 和 A2A 边界

### 6.1 允许的最小形态

专业版或企业版可以引入内部 Agent-as-Tool，但必须满足：

- 只能由平台配置。
- 子 Agent 输入必须经过上下文裁剪。
- 不传 private memory，除非有 delegated access。
- parent/child run 必须有 trace 关联。
- 成本和 quota 归因到 parent run。

### 6.2 A2A / Remote Agent

A2A 只作为企业版扩展，不进入 C 端基线。

必须满足：

- Remote Agent 默认 disabled。
- 远程 Agent Card 需要管理员审核。
- 外部身份映射到本地 tenant/user/agent policy。
- 远程结果作为 untrusted tool result 进入 ContextPack。
- 下游失败、超时、熔断必须写 AgentStep。

## 7. 配置门禁

建议新增统一配置：

```yaml
seahorse-agent:
  cweb:
    mode: consumer-web
  advanced:
    sandbox-enabled: false
    enterprise-connectors-enabled: false
    agent-mesh-enabled: false
    local-agent-enabled: false
```

约束：

- `consumer-web` 模式下，sandbox、enterprise connectors、agent mesh 默认关闭。
- 即使 class 存在，Web API 也不能对普通用户暴露。
- 管理端入口必须根据能力开关隐藏。

## 8. API 暴露规则

| API | C 端默认 | 条件 |
| --- | --- | --- |
| `/api/sandbox/**` | 不开放 | 专业版/企业版开启，且 admin/user policy 允许 |
| `/api/connectors/**` | 不开放 | 企业版开启，admin only |
| `/api/secrets/**` | 不开放 | 企业版开启，admin only |
| `/api/agent-handoffs/**` | 不开放 | 企业版开启，admin only 或内部 service |
| `/api/remote-agents/**` | 不开放 | 企业版开启，admin only |

## 9. 实施切片

### Task 4.1：能力开关与路由门禁

- 新增 advanced feature properties。
- Web controller 增加能力开关校验。
- 前端 admin console 根据开关隐藏高级入口。

验收：

- 默认 consumer-web 配置下，高级 API 返回 404 或明确禁用错误。
- 普通用户看不到高级入口。

### Task 4.2：Sandbox 出口收敛

- Sandbox artifact 不直接暴露。
- 只有扫描通过后转换为 AgentArtifact。
- 主动内容默认 attachment。

验收：

- Sandbox artifact 不能绕过 AgentArtifact 下载策略。

### Task 4.3：Connector 管理隔离

- Connector、Credential、MCP/OpenAPI 入口 admin only。
- operation 默认 disabled。
- 写操作默认 approval required。

验收：

- C 端用户不能创建 connector 或 secret。
- 高风险 operation 未审批不能执行。

### Task 4.4：Agent Mesh 冻结声明

- 文档、配置和 UI 均标记为企业版远期扩展。
- C 端 task template 不允许使用 A2A trigger。

验收：

- 默认模板中没有 A2A/remote agent。
- consumer-web 模式无法注册 remote agent。

## 10. 测试计划

后端：

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am test
```

建议新增测试：

- `AdvancedFeatureGateTests`
- `SandboxApiDisabledByDefaultTests`
- `ConnectorAdminOnlyTests`
- `RemoteAgentDisabledByDefaultTests`
- `SandboxArtifactToAgentArtifactPolicyTests`

## 11. 退出标准

1. consumer-web 默认配置下，没有本地 Agent、沙箱、连接器、mesh 入口暴露给用户。
2. 高级能力必须显式开启。
3. 高级能力的所有输出都回到 AgentArtifact、ContextPack、Audit、Quota。
4. 文档和 UI 都明确高级能力不是 C 端 AI Infra 完成标准。

