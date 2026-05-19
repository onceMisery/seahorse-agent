# Phase A 详细设计：基础 Agent Loop 与工具调用契约固化

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 当前状态：基础 `KernelAgentLoop`、`ToolPort`、`ToolRegistryPort`、OpenAI-compatible `streamChatWithTools`、`chatMode=agent` 已落地。  
> 本阶段目标：固化现有契约、补齐边界策略、配置、回归测试和监控口径，为 Phase B-F 提供稳定运行底座。

---

## 1. 范围与原则

### 1.1 范围

- 保持 `chatMode=rag` 默认路径不变。
- 继续以 `KernelAgentLoop` 作为唯一 LLM-driven tool loop owner。
- 固化工具注册、工具暴露、工具执行、取消、trace、截断、超时和协议错误处理。
- 明确 Phase B-F 扩展点：Tool adapter、Task Runtime、Skill、Validator、Memory Tool、Governance。

### 1.2 非目标

- 不实现持久化状态机。
- 不新增 Skill 注册中心。
- 不新增 Agentic Search 工具。
- 不调整已有 RAG pipeline 语义。

---

## 2. 类设计与接口定义

### 2.1 现有核心类

| 类/接口 | 所属模块 | 职责 |
|---|---|---|
| `KernelAgentLoop` | `seahorse-agent-kernel` | ReAct-style tool loop；请求模型、执行工具、回填 observation、输出最终回答 |
| `KernelAgentLoopOptions` | `seahorse-agent-kernel` | `maxSteps`、`perToolTimeout`、`maxParallelTools` |
| `ToolRegistryPort` | `ports/outbound/agent` | 枚举和查找工具；支持动态注册 |
| `ToolPort` | `ports/outbound/agent` | 单个工具执行入口；异常必须转为 `ToolInvocationResult.failed` |
| `ToolDescriptor` | `ports/outbound/agent` | OpenAI-compatible function metadata |
| `StreamingChatModelPort` | `ports/outbound/model` | `streamChatWithTools` 协议入口 |
| `KernelChatInboundService` | `kernel/application/chat` | 根据 `chatMode` 路由 RAG 或 Agent |
| `SeahorseAgentKernelAgentAutoConfiguration` | `spring-boot-starter` | Agent loop 自动装配，默认关闭 |

### 2.2 建议新增/固化的支持类

```java
package com.miracle.ai.seahorse.agent.kernel.application.agent;

public record AgentLoopExecutionPolicy(
        int maxSteps,
        int maxToolCalls,
        int maxToolObservationChars,
        Duration perToolTimeout,
        int maxParallelTools,
        boolean allowUnknownToolObservation) {
}
```

用途：

- 将当前散落在 `KernelAgentLoop` 常量和 `KernelAgentLoopOptions` 中的运行边界收敛成统一 policy。
- Phase B-F 只能扩展 policy，不直接改 `KernelAgentLoop` 常量。

```java
public interface AgentToolInvocationGuard {
    ToolGuardDecision beforeInvoke(AgentToolCall call, ToolDescriptor descriptor, AgentLoopRequest request);
}

public record ToolGuardDecision(boolean allowed, String reason) {
    public static ToolGuardDecision allow() { ... }
    public static ToolGuardDecision deny(String reason) { ... }
}
```

用途：

- Phase A 只提供 noop 实现。
- Phase F 接入权限、预算、审计时复用该扩展点。

### 2.3 自动装配配置项

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `seahorse-agent.chat.agent-mode-enabled` | `false` | 显式开启 Agent 模式 |
| `seahorse-agent.chat.agent.max-steps` | `6` | 单次 loop 最大模型轮次 |
| `seahorse-agent.chat.agent.per-tool-timeout` | `30s` | 单工具超时 |
| `seahorse-agent.chat.agent.max-parallel-tools` | `1` | 并发工具数 |
| `seahorse-agent.chat.agent.max-tool-observation-chars` | `8192` | 单工具 observation 最大字符数 |
| `seahorse-agent.chat.agent.tools.mcp.include` | 空 | MCP allowlist |

---

## 3. 数据库表结构设计

Phase A 不新增业务表。原因：

- Agent Loop 是一次性流式执行能力，不负责长期任务状态。
- Trace 已复用现有 `t_rag_trace_run` / `t_rag_trace_node`。
- 持久化任务、快照和审计分别在 Phase C / Phase F 落地。

### 3.1 复用表

| 表 | 用途 |
|---|---|
| `t_rag_trace_run` | 保存 Agent/RAG 运行 trace run |
| `t_rag_trace_node` | 保存 `AGENT_STEP`、`AGENT_TOOL` 节点 |

### 3.2 Trace 节点约定

| node_type | node_name | extra_data |
|---|---|---|
| `AGENT_STEP` | `agent-step-{n}` | `{"step":1,"toolCallCount":2}` |
| `AGENT_TOOL` | toolId | `{"toolCallId":"...","success":true,"latencyMs":123}` |

如果后续需要独立 Agent Trace 命名，可在 Phase F 做迁移；Phase A 不新增表，避免双 trace owner。

---

## 4. API 接口规范

### 4.1 流式聊天接口

```http
GET /rag/v3/chat?question=...&conversationId=...&userId=...&deepThinking=false&chatMode=agent
Accept: text/event-stream
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `question` | 是 | 用户输入 |
| `conversationId` | 否 | 不传时 Web 层生成 |
| `userId` | 否 | 默认 `default` |
| `deepThinking` | 否 | 采样参数开关 |
| `chatMode` | 否 | `rag` / `agent`，非法值回退 `rag` |

Agent 模式 SSE 事件沿用现有 `StreamCallback`：

| event | 说明 |
|---|---|
| `message` | 最终回答或模型内容增量 |
| `thinking` | tool call / observation 摘要 |
| `finish` | 完成 |
| `done` | 结束标记 |
| `error` | 异常 |

### 4.2 停止任务

```http
POST /rag/v3/stop?taskId={taskId}
```

语义：

- 调用 `StreamTaskPort.cancel(taskId)`。
- Agent 模式下取消当前模型流和工具执行。
- 已完成任务重复 stop 返回成功，保持幂等。

---

## 5. 实现步骤

1. 固化 `KernelAgentLoopOptions` 默认值和边界校验。
2. 为 `KernelAgentLoop` 补充缺失测试：
   - tool collector 未调用。
   - collector 重复调用。
   - tool observation 截断。
   - unknown tool observation。
   - cancel during model call。
   - cancel during tool call。
3. 在 `SeahorseAgentKernelAgentAutoConfiguration` 增加 `max-tool-observation-chars` 配置。
4. 在 trace node extra_data 中补充 step、toolCallId、latency、success。
5. 明确 `ToolPort` 实现不得抛异常，必须返回 failed observation。
6. 更新 README / baseline 中 Phase A 的边界描述。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| `chatMode=agent` 但未开启配置 | `KernelChatInboundService` 回退 RAG 并记录 warn |
| 无可用工具 | 模型收到空 tools 列表，可直接回答 |
| 模型未调用 collector | 抛 `AgentLoopException`，SSE `error` |
| 工具未注册 | `ToolPort.notFound` 返回 failed observation |
| 工具执行超时 | 取消 Future，返回 failed observation，loop 可继续 |
| observation 超长 | 截断到配置上限并追加 `[truncated]` |
| 达到 maxSteps | 返回“任务步骤已达上限” |
| 客户端断开 | `SseEmitter` 完成，后端取消 handle |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `KernelAgentLoopTests` | 多轮 tool call 后最终回答 |
| `KernelAgentLoopTests` | tool failed observation 回填后模型继续决策 |
| `KernelAgentLoopTests` | maxSteps 截断 |
| `KernelAgentLoopTests` | allowedToolIds 过滤 |
| `KernelAgentLoopTests` | unknown tool 不抛异常 |
| `KernelAgentLoopOptionsTests` | 配置边界归一化 |
| `KernelChatInboundServiceAgentModeTests` | agent mode 路由到 `KernelAgentLoop` |
| `SeahorseAgentKernelAgentAutoConfigurationTests` | 配置关闭时不创建 AgentLoop，开启时创建 |

回归命令：

```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am "-DfailIfNoTests=false" test
```

---

## 8. 性能指标与监控方案

| 指标 | 目标 | 采集方式 |
|---|---|---|
| agent.loop.duration | P95 小于 30s，视模型而定 | `ObservationPort` / trace |
| agent.loop.steps | 平均小于 4 | trace extra_data |
| agent.tool.latency | 单工具 P95 小于配置超时 80% | `AGENT_TOOL` node |
| agent.tool.failure.rate | 小于 5% | failed observation 计数 |
| agent.loop.cancel.success | 取消成功率大于 99% | `StreamTaskPort` 指标 |
| agent.loop.truncated.rate | 小于 2% | result.truncated |

监控落点：

- `KernelRagTraceRecorder`：记录 run 和 node。
- Micrometer adapter：新增 counter/timer 时命名使用 `seahorse.agent.*`。
- 日志：协议错误使用 error，工具失败 observation 使用 warn/debug，避免刷屏。

