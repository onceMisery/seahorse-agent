# Seahorse Agent 未实现功能详细设计汇总

## 文档概述

本目录包含 Seahorse Agent 项目中规划但尚未完全实现的功能的详细设计文档。这些设计基于对现有代码、设计文档和行业最佳实践的深入分析。

**生成日期**: 2026-05-31
**最近校准**: 2026-06-08
**基于版本**: 当前 main 分支
**分析依据**: README.md、企业级 AI Infra 分阶段开发规划、智能体记忆系统架构设计等

---

## 实现状态总览

### 整体实现率：84.0% (21/25 功能已实现)

| 类别 | 已实现 | 部分实现 | 未实现 | 总计 |
|------|--------|----------|--------|------|
| 混合检索与重排 | 6 | 0 | 0 | 6 |
| 元数据抽取与治理 | 6 | 0 | 0 | 6 |
| 智能体记忆系统 | 4 | 0 | 0 | 4 |
| AI Infra 基础 | 4 | 0 | 0 | 4 |
| 术语映射在线扩展 | 1 | 0 | 0 | 1 |
| MCP/OpenAPI/凭据/沙箱 | 0 | 3 | 0 | 3 |
| Agent Factory | 0 | 1 | 0 | 1 |
| Multi-Agent/A2A | 0 | 1 | 0 | 1 |

---

## 未实现功能清单

### 1. MCP OAuth2 安全增强 ⚠️ 部分实现

**当前状态**: 基础 MCP HTTP 适配器已实现，但缺少 OAuth 2.1 支持

**缺失组件**:
- OAuth 2.1 Client Credentials Flow
- Scope Challenge 处理
- Token 刷新与撤销机制
- 凭据加密存储
- 多租户 token 隔离

**设计文档**: [01-MCP-OAuth2-安全增强设计.md](./01-MCP-OAuth2-安全增强设计.md)

**优先级**: 🔴 高 - 企业生产环境必需

**预计工期**: 6 周

**依赖**:
- Credential Vault 基础设施
- Redis 或 JDBC 存储适配器
- 审计日志系统

---

### 2. OpenAPI Connector ⚠️ 部分实现

**当前状态**: 导入、解析、operation 风险初判、凭据绑定、启停、Tool Catalog 发布和前端基础页面已实现；真实 OpenAPI tool invocation、spec diff、dry-run、凭据验证和发布治理仍需产品化。

**功能描述**:
- 上传 OpenAPI 3.0 规范
- 自动解析 operationId 为 toolId
- 支持多种认证方式（apiKey、bearer、OAuth2）
- Operation 风险分级
- Dry-run 和 Mock 测试

**设计文档**: [02-OpenAPI-Connector-设计.md](./02-OpenAPI-Connector-设计.md)

**优先级**: 🟡 中 - 扩展工具生态系统

**预计工期**: 4 周

**依赖**:
- Tool Gateway
- Credential Vault
- OpenAPI Parser 库

---

### 3. Sandbox Runtime ⚠️ 部分实现

**当前状态**: 端口定义存在，但无实际隔离实现

**功能描述**:
- Code Interpreter 沙箱
- Browser Automation 隔离
- Shell/Command 执行隔离
- 文件系统隔离
- 网络访问控制

**设计文档**: [03-Sandbox-Runtime-设计.md](./03-Sandbox-Runtime-设计.md)

**优先级**: 🔴 高 - 安全执行高风险操作

**预计工期**: 8 周

**依赖**:
- 容器技术（Docker/Podman）
- 网络策略
- 文件系统隔离

---

### 4. Agent Factory UI ⚠️ 部分实现

**当前状态**: Agent 列表、创建、详情、编辑、发布校验、发布弹窗、回滚弹窗和工具绑定页面已实现；缺少创建向导、结构化策略编辑、test run、version diff、readiness/rollout 联动等完整 Studio 工作流。

**功能描述**:
- Agent 创建向导
- 模板选择器
- Prompt/Instruction 编辑器
- 工具选择器
- 测试运行面板
- 发布门禁检查
- Agent Catalog

**设计文档**: [04-Agent-Factory-UI-设计.md](./04-Agent-Factory-UI-设计.md)

**优先级**: 🟡 中 - 提升业务 Agent 创建效率

**预计工期**: 6 周

**依赖**:
- Agent Registry 后端 API
- Tool Catalog API
- Evaluation API

---

### 5. Multi-Agent / A2A / Agent Mesh ⚠️ 部分实现

**当前状态**: 本地 Agent-as-Tool、handoff 记录、深度/环路检查、A2A child run、JDBC 存储、查询与取消 API 已实现；Supervisor/Workflow Team、Remote A2A、Remote Agent Registry 和 Mesh 控制面仍未落地。

**功能描述**:
- Agent-as-Tool 模式
- Supervisor 模式
- Workflow Team 模式
- A2A 协议支持
- Agent Mesh 控制面
- 冲突仲裁机制

**设计文档**: [05-Multi-Agent-A2A-设计.md](./05-Multi-Agent-A2A-设计.md)

**优先级**: 🟢 低 - 高级协作能力

**预计工期**: 10 周

**依赖**:
- Agent Registry
- Tool Gateway
- Policy Engine
- Durable Runtime

---

### 6. 企业生产化硬化 ⚠️ 部分实现

**当前状态**: 基础监控和审计已实现，缺少完整的 SRE 能力

**缺失组件**:
- SLA 管理
- 红队自动化
- 灰度发布
- 成本配额管理
- 完整的评估平台

**设计文档**: [06-企业生产化硬化设计.md](./06-企业生产化硬化设计.md) *(未在本轮展开)*

**优先级**: 🟡 中 - 生产环境稳定性

**预计工期**: 持续迭代

---

## 记忆系统待实施阶段

虽然记忆系统的核心读路径已完成，但以下阶段仍需实施：

### Phase 2: 规则版记忆写入闭环 ⚠️ 部分实现

**状态**: 基础设施已就绪，运行闭环以 `/memories/readiness` 证据为准

**待完善**:
- 更多可解释的写入候选来源
- 覆盖摘要、明确事实、明确偏好等不同类型

**参考**: [智能体记忆系统架构设计.md](../智能体记忆系统架构设计.md)

### Phase 5: 衰减、质量评估与冲突治理 ⚠️ 部分实现

**待实施**:
- 仿生衰减算法（`e^(-λt)` 时间衰减）
- 独立质量评估器（五维度评估）
- 冲突检测与自动修复
- 质量报表与观测

**新增仓储能力需求**:
- `ShortTermMemoryMaintenancePort`
- `t_memory_conflict_log` 表
- `t_memory_quality_snapshot` 表

### 其他待实施功能

| 功能 | 状态 | 说明 |
|------|------|------|
| MemoryVectorPort 向量检索闭环 | ⏳ 待实施 | 接入 Milvus 实现语义记忆检索 |
| 多级摘要策略 | ⏳ 待实施 | L2 会话级、L3 跨会话主题、L4 用户画像 |
| 关键事实提取器 | ⏳ 待实施 | 自动提取用户偏好/属性/决策等 |
| Token 预算管理 | ⏳ 待实施 | 智能分配和截断记忆 Token |
| 知识图谱集成 | 🔮 远期规划 | 可选集成 Neo4j |
| 多模态记忆 | 🔮 远期规划 | 支持图片、音频等 |
| 记忆可解释性 | 🔮 远期规划 | 提供记忆来源和推理路径 |

已从待实施清单移除：术语映射 JDBC 实现和在线 keyword 检索消费已由 `JdbcQueryTermExpansionAdapter`、JDBC 自动配置、`KernelRetrievalEngine` 透传和 `KeywordSearchChannelFeature` 消费闭合。

---

## 实施优先级建议

### 立即优先 (1-2 周)

1. **MCP OAuth2 基础实现**
   - 实现 Client Credentials Flow
   - 实现 Token 缓存
   - 基础凭据存储

2. **Sandbox Runtime 真实隔离执行**
   - 实现容器化 runtime adapter
   - 补齐 runtime profile、session TTL/cleanup metadata 和内容级 artifact 扫描
   - 接入 sandbox-backed tools

### 中期优先 (2-4 周)

1. **OpenAPI Connector 产品化**
   - OpenAPI invocation adapter
   - spec diff 与 operation review
   - dry-run、凭据验证与调用审计

2. **Agent Factory Studio**
   - 创建向导
   - 结构化策略编辑
   - 测试运行、发布门禁、版本 diff

3. **记忆系统 Phase 5**
   - 衰减算法实现
   - 质量评估器
   - 冲突检测

### 长期规划 (1-2 月)

1. **Multi-Agent / A2A**
   - 本地 handoff 完成态回写与授权矩阵
   - Supervisor / Workflow Team
   - Remote A2A 与 Agent Mesh 控制面

2. **企业生产化硬化**
   - SLA 管理
   - 红队自动化
   - 灰度发布

3. **Agent Mesh 控制面**
   - 服务发现
   - 路由策略
   - 熔断降级

---

## 数据库表结构需求

### 已具备基础的表

- 检索治理、元数据治理、知识库扩展和记忆系统基础表已存在。
- Agent 注册、版本、运行、审批、quota、rollout、readiness、audit 等 AI Infra 基础表已存在。
- OpenAPI Connector 的 connector/version/operation/credential binding 基础表已存在。
- Sandbox session/execution/artifact 基础表已存在。
- Agent handoff、agent template、publish check、version activation 等协作和 Factory 基础表已存在。

### 后续需要新增或调整的表

| 方向 | 表或字段 | 说明 |
| --- | --- | --- |
| MCP OAuth2 | `sa_mcp_credential`、token cache 表或 Redis schema | 仍需 OAuth token、scope challenge、refresh/revoke 生命周期 |
| OpenAPI Connector | `sa_connector_dry_run`、operation review 字段、version diff 字段 | 详见 [OpenAPI Connector 详细设计](./02-OpenAPI-Connector-设计.md) |
| Sandbox Runtime | runtime profile、内容级 artifact scan record、session TTL/close metadata | 详见 [Sandbox Runtime 详细设计](./03-Sandbox-Runtime-设计.md) |
| Agent Factory UI | 完整 version history/diff 查询所需索引或视图 | 详见 [Agent Factory UI 详细设计](./04-Agent-Factory-UI-设计.md) |
| Multi-Agent/A2A | `sa_agent_collaboration_policy`、`sa_remote_agent`、`sa_agent_team` | 详见 [Multi-Agent / A2A 详细设计](./05-Multi-Agent-A2A-设计.md) |
| 记忆质量治理 | `t_memory_conflict_log`、`t_memory_quality_snapshot` | 用于衰减、质量评估、冲突治理和报表 |

---

## 前端页面需求

### 已具备基础入口

- Agent 管理：列表、创建、详情、编辑、发布弹窗、回滚弹窗、工具绑定。
- 工具管理：Tool Catalog、Tool Detail、Tool Invocation Audit。
- OpenAPI Connector：导入、列表、详情、operation 启停、凭据绑定。
- 安全治理：Resource ACL、Access Decision、Quota Policy、Approval Center。
- RAG 评测：数据集、策略模板、版本质量对比。
- 记忆治理、审计、成本分析、Sandbox 基础页面。

### 后续需要产品化的页面能力

| 方向 | 后续能力 |
| --- | --- |
| Agent Factory | 创建向导、结构化策略编辑、test run panel、publish gate dashboard、version diff |
| OpenAPI Connector | spec diff、operation review、dry-run、secret picker、调用审计摘要 |
| Sandbox Runtime | session 列表、artifact detail/preview、policy preview |
| Multi-Agent/A2A | handoff tree、collaboration policy、team builder、remote agent registry、mesh health |
| MCP 管理 | MCP Server OAuth 配置、scope challenge、token 状态、凭据轮换 |
| 记忆系统 | 记忆质量仪表板、冲突检测与修复、时间线和关系图 |
| 监控运维 | SLA、红队结果、灰度/rollout、SRE health |

---

## 技术债务

### 高优先级

1. **Starter 依赖治理**
   - 已完成 bootstrap 默认依赖迁移：默认应用只依赖 `starter-core`，不再依赖 `starter-all`
   - 已用 Maven Enforcer 禁止 bootstrap 回退到聚合 `starter-all`
   - 后续继续翻转 `starter-core`/`starter` 坐标方向，并补齐 core-only context 验证

2. **元数据治理 JDBC 深层拆分**
   - 默认端口 Bean 已通过 `JdbcMetadataGovernanceRepositoryDelegate` 和 `JdbcMetadataPortAdapters` 细粒度化
   - 后续继续把 `JdbcMetadataGovernanceRepositoryAdapter` 中的 SQL、row mapper 和事务边界迁移到子域 adapter

3. **端口准入规则**
   - 建立端口新增评审机制
   - 避免端口膨胀

### 中优先级

1. **大类治理制度化**
   - 超过 500 行的类建立拆分触发器
   - 超过 800 行且跨 3 个以上职责时列为 P1 候选

2. **测试覆盖率提升**
   - 核心链路测试覆盖率 > 80%
   - 关键适配器集成测试

---

## 参考资料

### 内部文档

- [README.md](../../../README.md)
- [企业级 AI Infra 分阶段开发规划](../../../../docs/company-agent/Seahorse%20Agent%20企业级%20AI%20Infra%20分阶段开发规划.md)
- [智能体记忆系统架构设计](../智能体记忆系统架构设计.md)
- [混合检索与重排详细设计](../混合检索与重排详细设计.md)
- [企业级元数据抽取与治理管道设计](../企业级元数据抽取与治理管道设计.md)

### 外部参考

- [OpenAI Agents SDK](https://developers.openai.com/api/docs/guides/agents)
- [Google ADK Memory](https://adk-labs.github.io/adk-docs/sessions/memory/)
- [Microsoft Foundry Agent Service](https://learn.microsoft.com/en-us/azure/foundry/agents/overview)
- [AWS Bedrock AgentCore](https://aws.amazon.com/bedrock/agentcore/faqs/)
- [MCP Authorization Specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
- [LangGraph Durable Execution](https://docs.langchain.com/oss/python/langgraph/durable-execution)
- [OWASP Agentic AI Threats](https://genai.owasp.org/resource/agentic-ai-threats-and-mitigations/)

---

## 贡献指南

如果你要实施这些未实现的功能，请遵循以下流程：

1. **阅读相关设计文档**：理解功能目标、架构设计和实施计划
2. **创建实施分支**：从 `main` 分支创建 feature 分支
3. **遵循代码规范**：参考 [代码规范.md](../../../../docs/zh/content/开发指南/代码规范.md)
4. **编写测试**：单元测试覆盖率 > 80%，关键路径需要集成测试
5. **更新文档**：同步更新 API 文档、架构文档和用户文档
6. **提交 PR**：详细描述变更内容、测试结果和影响范围

---

## 更新日志

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-05-31 | 1.0 | 初始版本，基于代码分析创建 | Claude |

---

## 联系方式

如有疑问或建议，请通过以下方式联系：

- 提交 Issue
- 发起 Discussion
- 联系项目维护者
