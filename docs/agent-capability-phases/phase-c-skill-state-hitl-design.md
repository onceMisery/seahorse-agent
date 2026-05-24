# Phase C 详细设计：Skill 注册中心、状态机、任务快照与 Human-in-the-Loop

> **2026-05-24 修订说明（术语漂移）：**
> 本文使用的 `AgentTask` / `SkillDefinition` / `PhaseHandler` / `RequirementAnalysisSkill` 等术语在当前代码中**没有实际 owner**。真实落地术语为：
> - `AgentTask` → `AgentRun`（`kernel/domain/agent/runtime/AgentRun.java`）
> - `SkillDefinition` → `AgentDefinition`（`kernel/domain/agent/definition/AgentDefinition.java`）
> - 单步交互 / phase → `AgentStep`（不要继续按 phase 命名扩展）
> - `PhaseHandler` runtime → 不存在；当前由 `KernelAgentLoop` + runtime services 承载
>
> 后续阅读本文时，请把以上术语整体替换。新增设计或代码必须使用真实术语；任何"重启 `AgentTask` 平行模型"的提案必须先经 ADR 评审，详见 `docs/aegis/specs/2026-05-24-design-alignment-next-development.md` §14 禁止事项与 §5.3 canonical 术语表。

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 前置依赖：Phase A Agent Loop、Phase B Agentic Search。  
> 本阶段目标：让 Agent 从一次性工具循环升级为可持久化、可恢复、可人工确认、可回滚的多阶段任务系统。

---

## 1. 范围与原则

### 1.1 范围

- 新增 Agent Task Runtime。
- 新增 Skill 注册中心和 `PhaseHandler`。
- 新增任务状态机、快照版本链、人工动作日志。
- 新增 Agent Task Web API 和 SSE 双轨事件。
- 实现首个内置 `RequirementAnalysisSkill`。

### 1.2 非目标

- 不引入外部工作流引擎。
- 不做完全可视化 DAG 编辑器。
- 不让状态机执行 LLM；LLM 执行仍由 `KernelAgentLoop` 负责。

---

## 2. 类设计与接口定义

### 2.1 领域对象

```java
public enum AgentTaskStatus {
    CREATED,
    RUNNING,
    WAITING_FOR_HUMAN,
    COMPLETED,
    FAILED_RECOVERABLE,
    FAILED,
    CANCELLED
}

public enum AgentTaskActionType {
    CONFIRM,
    REVISE,
    ROLLBACK,
    SKIP,
    RESUME,
    CANCEL
}

public enum AgentSnapshotStatus {
    DRAFT,
    CONFIRMED,
    OUTDATED,
    REJECTED
}
```

```java
public record SkillDefinition(
        String skillId,
        String skillVersion,
        String name,
        List<SkillPhase> phases,
        Map<String, Object> defaults) {
}

public record SkillPhase(
        String phaseId,
        String name,
        List<String> dependsOn,
        boolean requireHumanConfirmation,
        String outputSchemaRef,
        List<String> allowedToolIds) {
}
```

### 2.2 应用服务

| 类 | 职责 |
|---|---|
| `KernelAgentTaskService` | 创建任务、处理 action、调度 phase、管理状态 |
| `AgentStateMachine` | 纯状态迁移校验 |
| `AgentSnapshotService` | 创建、确认、失效、回滚 snapshot |
| `KernelSkillRegistry` | 注册内置 Skill，读取持久化 Skill |
| `RequirementAnalysisSkill` | 提供四个需求分析 phase handler |
| `DefaultAgentTaskEventPublisher` | 发布 SSE / 内部事件 |
| `AgentIntentRouter` | 判断用户修订是当前阶段还是跨阶段变更 |
| `SnapshotDiffAnalyzer` | 判断 soft patch / hard cascade |

### 2.3 端口定义

```java
public interface AgentTaskInboundPort {
    AgentTaskView create(CreateAgentTaskCommand command);
    AgentTaskView get(String taskId, AgentTaskQuery query);
    AgentTaskActionResult act(AgentTaskActionCommand command);
    AgentTaskPage list(AgentTaskPageCommand command);
}

public interface AgentTaskRepositoryPort {
    void create(AgentTaskRecord record);
    Optional<AgentTaskRecord> findById(String taskId);
    AgentTaskPage page(AgentTaskPageRequest request);
    boolean updateStatus(String taskId, long expectedVersion, AgentTaskStatus status, String phaseId);
    boolean updateActiveSnapshot(String taskId, long expectedVersion, String snapshotId);
}

public interface AgentSnapshotRepositoryPort {
    void save(AgentSnapshotRecord record);
    Optional<AgentSnapshotRecord> findById(String snapshotId);
    List<AgentSnapshotRecord> listByTask(String taskId);
    void markOutdated(String taskId, List<String> phaseIds, String reason);
}

public interface AgentActionLogRepositoryPort {
    boolean exists(String actionId);
    void save(AgentActionLogRecord record);
}

public interface PhaseHandler {
    String phaseId();
    PhaseExecutionResult execute(PhaseExecutionContext context);
    PhaseExecutionResult resume(PhaseResumeContext context);
}
```

### 2.4 `RequirementAnalysisSkill`

| phaseId | handler |
|---|---|
| `extract_prototype` | `PrototypeExtractionPhaseHandler` |
| `business_logic` | `BusinessLogicPhaseHandler` |
| `technical_design` | `TechnicalDesignPhaseHandler` |
| `implementation_plan` | `ImplementationPlanPhaseHandler` |

每个 handler 只负责构造 `AgentLoopRequest`、解析输出、写 artifact；不直接操作 JDBC。

---

## 3. 数据库表结构设计

### 3.1 任务表

```sql
CREATE TABLE IF NOT EXISTS t_agent_task (
    id VARCHAR(64) PRIMARY KEY,
    skill_id VARCHAR(128) NOT NULL,
    skill_version VARCHAR(64) NOT NULL DEFAULT '1',
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    current_phase VARCHAR(128),
    active_snapshot_id VARCHAR(64),
    input_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    runtime_options JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_task_user_status
ON t_agent_task (user_id, status, deleted, update_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_task_tenant_status
ON t_agent_task (tenant_id, status, deleted, update_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_task_skill
ON t_agent_task (skill_id, create_time DESC);

COMMENT ON TABLE t_agent_task IS 'Agent 长期任务状态表';
```

### 3.2 快照表

```sql
CREATE TABLE IF NOT EXISTS t_agent_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    parent_snapshot_id VARCHAR(64),
    phase_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    summary TEXT,
    diff_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    invalidation_reason TEXT,
    created_by VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_snapshot_task_phase
ON t_agent_snapshot (task_id, phase_id, status, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_snapshot_parent
ON t_agent_snapshot (parent_snapshot_id);

COMMENT ON TABLE t_agent_snapshot IS 'Agent 阶段产物快照表';
```

### 3.3 人工动作日志

```sql
CREATE TABLE IF NOT EXISTS t_agent_action_log (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    base_snapshot_id VARCHAR(64),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_status VARCHAR(32) NOT NULL,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_action_id ON t_agent_action_log (id);
CREATE INDEX IF NOT EXISTS idx_agent_action_task ON t_agent_action_log (task_id, create_time DESC);

COMMENT ON TABLE t_agent_action_log IS 'Agent 人工动作与恢复幂等日志表';
```

### 3.4 Skill 定义表

```sql
CREATE TABLE IF NOT EXISTS t_skill_definition (
    id VARCHAR(128) PRIMARY KEY,
    skill_version VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    enabled SMALLINT NOT NULL DEFAULT 1,
    definition JSONB NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_definition_version
ON t_skill_definition (id, skill_version)
WHERE deleted = 0;

COMMENT ON TABLE t_skill_definition IS 'Agent Skill 定义表';
```

---

## 4. API 接口规范

### 4.1 创建任务

```http
POST /api/seahorse-agent/agent-tasks
Content-Type: application/json
```

```json
{
  "skillId": "requirement-analysis",
  "input": {
    "prompt": "根据原型图生成 PRD 和技术方案",
    "attachments": [{"type": "image", "objectKey": "upload/prototype.png"}]
  },
  "runtimeOptions": {
    "requireHumanConfirmation": true,
    "maxStepsPerPhase": 8
  }
}
```

响应：

```json
{
  "code": "0",
  "data": {
    "taskId": "agt_xxx",
    "status": "RUNNING",
    "currentPhase": "extract_prototype",
    "eventStreamUrl": "/api/seahorse-agent/agent-tasks/agt_xxx/events"
  }
}
```

### 4.2 查询任务

```http
GET /api/seahorse-agent/agent-tasks/{taskId}?includeSnapshots=true
```

### 4.3 任务动作

```http
POST /api/seahorse-agent/agent-tasks/{taskId}/actions
Content-Type: application/json
```

```json
{
  "actionId": "act_001",
  "actionType": "CONFIRM",
  "baseSnapshotId": "snap_001",
  "payload": {}
}
```

`actionType`：

- `CONFIRM`：确认当前 snapshot。
- `REVISE`：基于用户指令修订当前或上游阶段。
- `ROLLBACK`：回滚到指定 snapshot。
- `SKIP`：跳过可选 phase。
- `RESUME`：恢复可恢复失败任务。
- `CANCEL`：取消任务。

### 4.4 SSE

```http
GET /api/seahorse-agent/agent-tasks/{taskId}/events
Accept: text/event-stream
```

事件：`task_meta`、`phase_started`、`tool_call`、`artifact_delta`、`confirmation_required`、`task_finished`、`task_failed`。

---

## 5. 实现步骤

1. 新增 domain task/snapshot/skill/action DTO。
2. 新增 inbound/outbound ports。
3. 实现 `AgentStateMachine` 单元测试。
4. 实现 `AgentSnapshotService` 和 `SnapshotDiffAnalyzer`。
5. 实现 `KernelSkillRegistry`，先注册内置 `RequirementAnalysisSkill`。
6. 实现 `KernelAgentTaskService.create/get/act/list`。
7. 新增 JDBC repository 和 SQL 脚本 `agent-runtime-postgresql.sql`。
8. 新增 `SeahorseAgentTaskController`、`SeahorseSkillController`。
9. 新增 starter auto configuration。
10. 前端新增 Agent 工作台页面。
11. 做端到端 RequirementAnalysisSkill 测试。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| skillId 不存在 | 返回业务错误 `AGENT_SKILL_NOT_FOUND` |
| 非法状态迁移 | 返回 `AGENT_STATE_CONFLICT` |
| actionId 重复 | 返回第一次处理结果，保证幂等 |
| baseSnapshotId 过期 | 返回 `SNAPSHOT_VERSION_CONFLICT` |
| 用户跨阶段修改 | Intent Router 判断目标阶段，Diff Analyzer 决定 soft patch / hard cascade |
| 后续阶段失效 | 标记 snapshot `OUTDATED`，不删除历史 |
| 服务重启 | 从 `t_agent_task` 和 active snapshot 恢复 |
| SSE 断开 | 逻辑继续，前端可重新订阅 |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `AgentStateMachineTests` | 所有合法/非法迁移 |
| `AgentSnapshotServiceTests` | 创建、确认、回滚、标记失效 |
| `KernelSkillRegistryTests` | 内置 skill 注册、phase DAG 校验 |
| `KernelAgentTaskServiceTests` | 创建任务后进入首个 phase |
| `KernelAgentTaskServiceTests` | `CONFIRM` 后推进下一 phase |
| `KernelAgentTaskServiceTests` | `REVISE` 生成新 snapshot |
| `KernelAgentTaskServiceTests` | `ROLLBACK` 不覆盖旧 snapshot |
| `JdbcAgentTaskRepositoryAdapterTests` | 乐观锁更新失败 |
| `SeahorseAgentTaskControllerTests` | API 响应 `{code,data}` |

---

## 8. 性能指标与监控方案

| 指标 | 目标 |
|---|---|
| `agent.task.create.latency.p95` | 小于 300ms，不含异步 LLM |
| `agent.task.action.latency.p95` | 小于 500ms，不含 LLM |
| `agent.task.recoverable.waiting.count` | 可观测 |
| `agent.snapshot.create.latency.p95` | 小于 100ms |
| `agent.action.conflict.rate` | 小于 1% |
| `agent.hitl.wait.duration.avg` | 按产品监控 |

监控：

- 每个 task 关联 traceId。
- 状态迁移记录 info 日志。
- action conflict 和 failed recovery 记录 warn。

