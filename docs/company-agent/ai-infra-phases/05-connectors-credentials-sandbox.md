# Phase 5：MCP/OpenAPI/凭据/沙箱

## 1. 阶段目标

安全接入外部工具生态和企业系统。Phase 5 完成后，MCP、OpenAPI、内部工具和高风险执行环境都必须通过 Tool Gateway、Credential Provider、Policy Engine 和 Audit Ledger。

## 2. MCP 安全增强

### 2.1 配置扩展

扩展 `McpHttpAdapterProperties.Server`：

| 字段 | 说明 |
| --- | --- |
| `authType` | `NONE`、`STATIC_BEARER`、`OAUTH2`、`CLIENT_CREDENTIALS`、`USER_DELEGATED` |
| `authorizationServerMetadataUrl` | OAuth metadata |
| `protectedResourceMetadataUrl` | MCP resource metadata |
| `clientId` | OAuth client |
| `clientSecretRef` | secret 引用 |
| `scopes` | 默认 scope |
| `audience` | token audience |
| `resource` | resource indicator |
| `trustPolicyId` | 信任策略 |

### 2.2 MCP 调用流程

```text
McpToolFeature.execute
  -> ToolGateway
  -> PolicyEngine
  -> CredentialProvider.resolve
  -> OAuthTokenPort.getToken
  -> StreamableHttpMcpClient.call
  -> ToolInvocationAudit
```

### 2.3 必须支持

1. Bearer token 注入。
2. token refresh。
3. scope challenge 处理。
4. token 不进入 prompt/log/trace 明文。
5. MCP tool discovery 写 ToolCatalog。

## 3. OpenAPI Connector

### 3.1 新增模型

| 模型 | 说明 |
| --- | --- |
| Connector | 一个外部系统连接器 |
| ConnectorVersion | OpenAPI spec 版本 |
| ConnectorOperation | operationId 到 toolId 的映射 |
| CredentialBinding | connector/tool 与凭据绑定 |

### 3.2 OpenAPI 导入流程

1. 上传 OpenAPI 3.0 spec。
2. 校验 spec。
3. 解析 operation。
4. 为每个 operation 生成 ToolCatalogEntry。
5. 根据 HTTP method 推断 actionType。
6. 根据 path/tag 推断 resourceType。
7. 管理员确认风险等级。

## 4. Credential Vault

### 4.1 端口

```text
SecretStorePort
  putSecret(SecretWriteCommand) -> secretRef
  getSecret(secretRef) -> SecretValue
  rotateSecret(secretRef)

CredentialProviderPort
  resolve(CredentialRequest) -> CredentialMaterial

OAuthTokenPort
  getToken(OAuthTokenRequest) -> OAuthToken
  refreshToken(refreshTokenRef) -> OAuthToken
  revoke(tokenRef)
```

### 4.2 存储规则

- 数据库存 secret 只能存密文。
- 日志只记录 secretRef。
- trace 不记录 token。
- token cache 存 Redis 时必须设置 TTL。
- agent definition 不能直接存 secret。

## 5. Sandbox Runtime

### 5.1 适用场景

- Code Interpreter。
- Browser Automation。
- Shell command。
- 文件转换。
- 临时数据分析。

### 5.2 端口

```text
SandboxRuntimePort
  createSession(SandboxSessionRequest) -> SandboxSession
  execute(SandboxExecutionRequest) -> SandboxExecutionResult
  snapshot(sessionId) -> SandboxSnapshot
  close(sessionId)

SandboxPolicyPort
  decide(SandboxPolicyRequest) -> SandboxPolicyDecision

SandboxArtifactPort
  saveArtifact(...)
  getArtifact(...)
```

### 5.3 安全规则

1. 主 JVM 不执行任意脚本。
2. 网络默认 deny。
3. 文件系统按 run 隔离。
4. artifact 出沙箱前做敏感信息扫描。
5. session 和 artifact 都写 audit。

## 6. 数据库表

```sql
CREATE TABLE sa_connector (
  connector_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_connector_operation (
  operation_id VARCHAR(64) PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  method VARCHAR(16) NOT NULL,
  path VARCHAR(512) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL
);

CREATE TABLE sa_secret_ref (
  secret_ref VARCHAR(128) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  encrypted_value TEXT NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);
```

## 7. API 设计

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/openapi` | 导入 OpenAPI |
| `GET` | `/api/connectors` | connector 列表 |
| `GET` | `/api/connectors/{connectorId}/operations` | operation |
| `POST` | `/api/secrets` | 创建 secretRef |
| `POST` | `/api/sandbox/sessions` | 创建沙箱 session |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | 执行 |

## 8. 测试清单

```powershell
./mvnw -pl seahorse-agent-adapter-mcp-http -Dtest=*McpAuth*Test test
./mvnw -pl seahorse-agent-tests -Dtest=*Connector*Tests test
./mvnw -pl seahorse-agent-tests -Dtest=*Sandbox*Tests test
```

必须覆盖：

1. MCP token 注入。
2. token 不进入日志摘要。
3. OpenAPI spec 生成 ToolCatalogEntry。
4. DELETE operation 默认 high risk。
5. sandbox 网络 deny 生效。
6. artifact 写入 storage 并写 audit。

## 9. 退出条件

1. 外部工具都通过 Tool Gateway。
2. 凭据只通过 secretRef 使用。
3. 高风险执行环境隔离运行。

## 10. 风险控制

- Phase 5 不要求自研完整容器平台，可先接外部 sandbox service。
- 不支持 OAuth 的 MCP server 只能用于低风险内部环境。
- OpenAPI 导入后默认 disabled，管理员确认后启用。
