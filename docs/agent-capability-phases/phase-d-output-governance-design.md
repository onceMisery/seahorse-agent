# Phase D 详细设计：输出可信度治理与上下文降维

> **2026-05-24 修订说明（接入点降级）：**
> 本文原描述要求 Phase D 第一阶段以 `OutputGovernancePhaseDecorator` 包装 `PhaseHandler`。当前代码**没有 `PhaseHandler` runtime**（详见 phase-c 文档 2026-05-24 修订说明）。
> 因此实施按 `docs/aegis/specs/2026-05-24-design-alignment-next-development.md` 第 6 节执行：
> - **接入点**：当前真实 owner 是 `KernelAgentLoop`；Phase D MVP（切片 1a）以独立 `OutputGovernanceService` 在 `KernelAgentLoop` 生成 final answer 后被调用。
> - **不得**为了照本文档新建空壳 `PhaseHandler` runtime；这会制造第二个 runtime owner。
> - **不得**把 validator 逻辑写进 `KernelAgentLoop.run()` 私有方法；治理逻辑放在独立 service。
> - 未来如确实引入 phase runtime（属于大型 runtime 改造），再迁移接入点；届时本文 decorator 描述可作为参考实现。
>
> 文档其余部分（Validator 列表、`SelfHealingLoop`、`ContextReducerPort`、观测事件需求）**目标不变**，但分阶段拆为 1a / 1b / 1c / 1d / 1e（详见 spec §6.2）。
> 观测事件命名按项目现有 kebab-case 风格使用 `agent-output-validation` / `agent-output-self-heal` / `agent-output-validation-failed`，**不**用本文档若干段落出现的点号风格。

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 前置依赖：Phase C Agent Task Runtime 和 Skill phase 已可用。  
> 本阶段目标：让 Agent 阶段输出具备结构校验、失败自愈、可人工修订、上下文可控和可观测能力。

---

## 1. 范围与原则

### 1.1 范围

- 新增 `OutputValidatorPort`。
- 新增 JSON Schema、Mermaid、DDL、Markdown 结构校验。
- 新增 `SelfHealingLoop`。
- 新增 `ContextReducerPort`，控制阶段间上下文体积。
- 将校验结果和修复过程落库。

### 1.2 非目标

- 不在第一阶段执行真实 DDL。
- 不承诺 Mermaid 全语义渲染，只做语法和基础结构检查。
- 不把 validator 写进 Web 层；所有校验由 kernel application 调用端口。

---

## 2. 类设计与接口定义

### 2.1 领域对象

```java
public enum OutputArtifactType {
    JSON,
    MARKDOWN,
    MERMAID,
    DDL
}

public enum ValidationSeverity {
    WARNING,
    ERROR
}

public record OutputValidationIssue(
        ValidationSeverity severity,
        String code,
        String path,
        String message) {
}

public record OutputValidationResult(
        boolean valid,
        List<OutputValidationIssue> issues,
        Map<String, Object> normalizedPayload) {
}
```

### 2.2 端口

```java
public interface OutputValidatorPort {
    OutputValidationResult validate(OutputValidationRequest request);
}

public record OutputValidationRequest(
        String taskId,
        String phaseId,
        OutputArtifactType artifactType,
        String schemaRef,
        String content,
        Map<String, Object> context) {
}

public interface ContextReducerPort {
    ContextSlice reduce(ContextReduceRequest request);
}
```

### 2.3 实现类

| 类 | 职责 |
|---|---|
| `CompositeOutputValidator` | 按 artifactType 分派 validator |
| `JsonSchemaOutputValidator` | JSON schema 校验和 normalized payload |
| `MermaidOutputValidator` | Mermaid flowchart / sequence 基础语法校验 |
| `DdlOutputValidator` | 禁止危险语句、表名规范、字段规范 |
| `MarkdownStructureValidator` | 检查必备章节 |
| `SelfHealingLoop` | 校验失败后调用 LLM 修复，最多重试 |
| `DefaultContextReducer` | 对 snapshot、retrieval chunks、history 做切片 |
| `OutputGovernancePhaseDecorator` | 包装 `PhaseHandler`，统一执行校验和自愈 |

---

## 3. 数据库表结构设计

### 3.1 输出校验运行表

```sql
CREATE TABLE IF NOT EXISTS t_agent_output_validation_run (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    phase_id VARCHAR(128) NOT NULL,
    snapshot_id VARCHAR(64),
    artifact_type VARCHAR(32) NOT NULL,
    schema_ref VARCHAR(256),
    valid SMALLINT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    issues_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    normalized_payload JSONB,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_output_validation_task
ON t_agent_output_validation_run (task_id, phase_id, create_time DESC);

COMMENT ON TABLE t_agent_output_validation_run IS 'Agent 输出校验运行记录表';
```

### 3.2 上下文切片表

```sql
CREATE TABLE IF NOT EXISTS t_agent_context_slice (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    phase_id VARCHAR(128) NOT NULL,
    source_snapshot_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    token_budget INTEGER NOT NULL,
    estimated_tokens INTEGER NOT NULL,
    payload JSONB NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_context_slice_task
ON t_agent_context_slice (task_id, phase_id, create_time DESC);

COMMENT ON TABLE t_agent_context_slice IS 'Agent 阶段上下文降维切片表';
```

---

## 4. API 接口规范

Phase D 的主能力由 PhaseHandler 内部调用。对外提供调试和查看接口。

### 4.1 查询校验记录

```http
GET /api/seahorse-agent/agent-tasks/{taskId}/output-validations
```

响应：

```json
{
  "code": "0",
  "data": [
    {
      "phaseId": "technical_design",
      "artifactType": "DDL",
      "valid": false,
      "retryCount": 1,
      "issues": [
        {"severity": "ERROR", "code": "DDL_DROP_FORBIDDEN", "message": "DROP TABLE is forbidden"}
      ]
    }
  ]
}
```

### 4.2 手动触发校验（调试）

```http
POST /api/seahorse-agent/output-validator/_debug
```

仅本地调试开启，不作为生产公开入口。

---

## 5. 实现步骤

1. 新增 output domain DTO。
2. 新增 `OutputValidatorPort`、`ContextReducerPort`。
3. 实现 `JsonSchemaOutputValidator`。
4. 实现 `MermaidOutputValidator`，第一阶段支持 flowchart/sequence 基础 token 检查。
5. 实现 `DdlOutputValidator`：
   - 禁止 `DROP`、`TRUNCATE`、`DELETE FROM`。
   - 表名必须 `t_` 前缀。
   - 字段时间使用 `create_time`、`update_time`。
   - 软删除字段使用 `deleted SMALLINT`。
6. 实现 `MarkdownStructureValidator`。
7. 实现 `SelfHealingLoop` 并接入 `PhaseHandler` decorator。
8. 实现 JDBC repository 保存 validation run 和 context slice。
9. 添加 Web 查询接口。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| 输出不是合法 JSON | validator 返回 error，进入 self-healing |
| self-healing 仍失败 | 进入 `WAITING_FOR_HUMAN`，附带 issues |
| Mermaid 语法不支持 | warning 或 error 取决于 phase 要求 |
| DDL 含危险语句 | error，不进入 confirmed snapshot |
| context 超预算 | reducer 截断低分 chunk，并记录 omitted summary |
| schemaRef 不存在 | phase 配置错误，任务进入 `FAILED_RECOVERABLE` |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `JsonSchemaOutputValidatorTests` | 必填字段缺失返回 error |
| `MermaidOutputValidatorTests` | 非法箭头语法失败 |
| `DdlOutputValidatorTests` | DROP/TRUNCATE 被拒绝 |
| `DdlOutputValidatorTests` | 非 `t_` 表名 warning/error |
| `DefaultContextReducerTests` | 超 token budget 时截断 |
| `SelfHealingLoopTests` | 第一次失败第二次修复成功 |
| `OutputGovernancePhaseDecoratorTests` | 校验失败进入 HITL |
| `JdbcOutputValidationRepositoryTests` | issues_json 正确保存读取 |

---

## 8. 性能指标与监控方案

| 指标 | 目标 |
|---|---|
| `agent.output.validation.latency.p95` | 小于 200ms，不含 LLM self-healing |
| `agent.output.schema.pass.first.rate` | 大于 80% |
| `agent.output.schema.pass.after_heal.rate` | 大于 95% |
| `agent.output.self_heal.retry.avg` | 小于 1 |
| `agent.context.estimated_tokens.p95` | 小于预算 |

监控：

- validation run 入库。
- self-healing 每次重试记录 trace node `OUTPUT_SELF_HEAL`。
- context reducer 记录 estimatedTokens、droppedChunks、sourceSnapshotIds。

