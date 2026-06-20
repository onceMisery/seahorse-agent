# 设计文档:可观测性增强、Agent Loop 解耦与 AgentScope 能力局部接入

- 文档状态:草案 v1
- 日期:2026-06-19
- 作者:平台架构
- 关联记忆:[[project_autoconfig_fix]]

## 1. 背景与动机

当前 Seahorse Agent 是完全自研的六边形架构平台(2378 个 Java 文件,kernel 749 个 port 接口),
agent 推理由自研的 `KernelAgentLoop` 承担。近期产生三个明确诉求:

1. **引入 MCP / A2A 协议** —— MCP 已有较完整自研实现,真正缺失的是 **A2A(Agent-to-Agent)** 服务发现与互调。
2. **Studio 可视化调试 + OpenTelemetry 链路追踪** —— 当前**完全没有** OTel(代码零命中),仅有 `MicrometerObservationAdapter` 产指标。
3. **自研 loop 难维护** —— `KernelAgentLoop` 2102 行,是本设计要治本的核心对象。

> 关键判断:**不替换底层框架**。Seahorse 90% 代码是 SaaS 业务与适配器,AgentScope 只对应 `application/agent`
> 一小块。正确路径是"**先解耦治本,再把 AgentScope 当可插拔后端按能力点局部接入**"。

## 2. 现状分析

### 2.1 `KernelAgentLoop` 的技术债(难维护根因)

| 债务 | 证据 | 影响 |
|------|------|------|
| 构造函数爆炸 | `KernelAgentLoop.java:162~280` 有 8+ 个重载构造,参数 5~7 个 | 新增依赖即新增重载,装配脆弱 |
| God class / 职责混杂 | 真正的 ReAct 核心(`run`/`requestModelTurn`/`executeTool`)被淹没 | 单类 2102 行,改一处怕动全身 |
| markdown/mermaid 规范化内联 | `normalizeMermaidBody`/`splitCompressedFlowchartStatements` 等数百行 | 与推理逻辑无关,属输出层 |
| 流式事件发射内联 | 十余个 `emitStepStarted`/`emitToolCallFinished`... | 与 SSE 协议耦合,属传输层 |
| text-encoded tool call 解析内联 | `parseTextEncodedToolCalls` 等 | 属模型兼容层 |

**结论**:这些代码搬不到 AgentScope(是 Seahorse 自有的 SSE 协议与输出治理),换框架解决不了维护性,
必须先在本仓内做**职责拆分**。

### 2.2 现有契约(本设计据此抽象,保持向后兼容)

- 入参:`AgentLoopRequest`(`domain/agent`)—— 18 字段,含 `runId/agentId/versionId/rolloutId/tenantId/userId/agentIdentityId` 全维度。
- 出参:`AgentLoopResult`(record)—— `finalAnswer / steps / truncated / exitReason`。
- 退出原因:`AgentLoopExitReason` = `FINAL_ANSWER / TRUNCATED / WAITING_APPROVAL`。
- 流式:`StreamCallback`(`onContent/onThinking/onRunStarted/onEvent/onComplete/onError`)+ `StreamCancellationHandle.cancel()` + `StreamEventType` 全量事件枚举。
- 观测端口:`ObservationPort.start(ObservationCommand) -> ObservationScope (AutoCloseable)`,Micrometer adapter 已实现。
- 装配点:`SeahorseAgentKernelAgentAutoConfiguration`(autoconfigure 模块)。

## 3. 设计目标与非目标

**目标**
- G1:补齐 OpenTelemetry 分布式追踪,agent 执行全链路(step / tool-call / model-turn)可见。
- G2:拆解 `KernelAgentLoop`,消除构造函数爆炸,分离输出层 / 传输层 / 模型兼容层职责。
- G3:抽象 `ReActExecutorPort`,使"循环引擎"成为可插拔实现。
- G4:以可选适配器形式引入 AgentScope 的 A2A、Studio 能力,自研 loop 保留为默认且可回退。

**非目标**
- NG1:不替换 Spring Boot / 六边形架构 / JDK 21 主干。
- NG2:不改 `AgentLoopRequest/Result` / SSE 协议 / Controller 契约(对前端零感知)。
- NG3:不强制全量切到 AgentScope;它是可灰度的备选后端,非默认。
- NG4:不在本期改造 MCP(已有实现),仅在 A2A 处补缺。

## 4. 总体架构

```
                        ┌────────────────────────────────────────┐
   Web/SSE Controller   │           kernel/application/agent       │
        │               │  ┌────────────────────────────────────┐ │
        ▼               │  │  ReActExecutorPort (新抽象/inbound)  │ │
   AgentRunService ─────┼─▶│   execute() / streamExecute()       │ │
                        │  └───────────┬──────────────┬─────────┘ │
                        │              │              │            │
                        │   ┌──────────▼───┐   ┌──────▼─────────┐ │
                        │   │ KernelAgent  │   │ AgentScope      │ │  ← @ConditionalOnProperty
                        │   │ LoopExecutor │   │ ReActExecutor   │ │     (默认 kernel,可灰度)
                        │   │ (默认/瘦身)  │   │ (新 adapter)    │ │
                        │   └──┬────┬───┬──┘   └─────────────────┘ │
                        └──────┼────┼───┼────────────────────────┘
                               │    │   │
              ┌────────────────▼┐ ┌─▼──────────┐ ┌▼───────────────┐
              │MarkdownNormalizer│ │AgentStream │ │ ToolCallParser │  ← 从 god class 抽出的协作者
              │  (输出层)        │ │Emitter(传输)│ │ (模型兼容层)   │
              └──────────────────┘ └────────────┘ └────────────────┘

   观测:所有 executor 经 ObservationPort → MicrometerObservationAdapter (指标)
                                         └→ 新增 OTel 桥 (micrometer-tracing-bridge-otel) → span
```

## 5. 工作流一(最高 ROI / 最低风险):OpenTelemetry 追踪

### 5.1 方案
不引入 AgentScope。复用现有 `ObservationPort` 埋点,在 Micrometer 之上桥接 OTel,
现有埋点自动产出 span,无需改业务代码。

### 5.2 依赖(`seahorse-agent-adapter-observation-micrometer/pom.xml` 新增)
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```
版本由 `spring-boot-dependencies` BOM 管理,不手写版本号(对齐项目规范)。

### 5.3 改造点
- 新增 `OtelObservationAdapter`(或在现有 adapter 内组合 `io.micrometer.tracing.Tracer`),
  令 `ObservationScope` 同时开启 Micrometer timer **和** tracing span。
- `ObservationCommand` 的 `tenantId / agentId / runId` 写入 span 的 baggage / attributes。
- 自动配置:`@ConditionalOnClass(Tracer.class)` + `@ConditionalOnProperty(seahorse.observability.tracing.enabled)`,
  缺失依赖时退回纯 Micrometer,保证现有行为不变。
- `application.yml` 暴露 OTLP endpoint、采样率、`service.name`。

### 5.4 验收
- 一次 agent run 在 Jaeger/Tempo 中呈现父子 span:`agent.run → step.N → tool_call.X / model.turn`。
- 租户/agent 维度可在 trace 上过滤。
- 关闭开关时无 OTel 依赖加载,旧指标不受影响。

### 5.5 工作量:**1~2 天**,独立可交付。

## 6. 工作流二(治本):拆解 `KernelAgentLoop` + 抽象 `ReActExecutorPort`

### 6.1 协作者拆分(纯重构,行为不变)

| 新类 | 迁出内容 | 所在层 |
|------|---------|--------|
| `MarkdownNormalizer` | `normalizeFinalMarkdown` / `normalizeMermaid*` / `splitCompressedFlowchartStatements` / 代码块围栏处理等 | 输出治理层 |
| `AgentStreamEmitter` | 全部 `emitXxx`(step/tool-call/skill/source/artifact/error)+ `StreamEventType` 映射 | 传输/SSE 层 |
| `ToolCallParser` | `parseTextEncodedToolCalls` / `decodeTextToolCallValue` / `nextTextToolCallId` | 模型兼容层 |
| `KernelAgentLoopExecutor` | 仅保留 `run`/`requestModelTurn`/`executeTool` 等 ReAct 核心 | 应用核心 |

每个协作者:单一职责、可独立单测、由瘦身后的 executor 组合调用。

### 6.2 消除构造函数爆炸

用参数对象替代 8+ 重载:
```java
// 一个不可变配置载体,替代所有重载构造
public record AgentLoopDependencies(
        StreamingChatModelPort modelPort,
        ToolRegistryPort toolRegistry,
        ToolGatewayPort toolGateway,
        ContextWeaverPort contextWeaver,
        OutputGovernanceService outputGovernance,
        MarkdownNormalizer markdownNormalizer,
        AgentStreamEmitter streamEmitter,
        ToolCallParser toolCallParser,
        ObservationPort observationPort) {
    // 紧凑构造里做 null 兜底 / 默认空实现(沿用现有 Objects.requireNonNullElse 习惯)
}

public KernelAgentLoopExecutor(AgentLoopDependencies deps) { ... } // 唯一构造
```
在 `SeahorseAgentKernelAgentAutoConfiguration` 里组装 `AgentLoopDependencies`,
各依赖仍用 `ObjectProvider<T>` 懒加载(对齐项目 Controller/装配规范)。

### 6.3 抽象 `ReActExecutorPort`(inbound port,这是工作流三的前提)

```java
package com.miracle.ai.seahorse.agent.kernel.application.agent;

/** Agent 推理循环引擎抽象。默认实现 KernelAgentLoopExecutor;可由 AgentScope 适配器替换。 */
public interface ReActExecutorPort {

    /** 同步执行,返回最终结果。 */
    AgentLoopResult execute(AgentLoopRequest request);

    /** 流式执行,事件经 StreamCallback 回调;返回取消句柄。 */
    StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback);

    /** 引擎标识,用于可观测与灰度路由。 */
    default String engineId() { return "kernel"; }
}
```
- 签名**完全复用现有** `AgentLoopRequest/Result/StreamCallback/StreamCancellationHandle`,
  因此 `AgentRunService` 等 80 处调用方零改动(把对 `KernelAgentLoop` 的直接依赖替换为 `ReActExecutorPort`)。
- `KernelAgentLoopExecutor implements ReActExecutorPort`,`engineId()="kernel"`,作为 `@Primary` 默认实现。

### 6.4 验收
- `KernelAgentLoopExecutor` 行数显著下降(目标 < 700 行),三个协作者各自带单测。
- 现有集成测试(`seahorse-agent-tests`)与 RAG smoke 全绿,SSE 事件序列逐字节一致。
- 构造函数收敛为 1 个。

### 6.5 工作量:**1~2 周**(纯重构 + 抽象,不引外部依赖,风险可控,需回归测试护航)。

## 7. 工作流三:局部引入 AgentScope(A2A + Studio,可灰度后端)

### 7.1 新模块
```
seahorse-agent-adapter-agent-agentscope/        # 新 L3 适配器
  └─ AgentScopeReActExecutor implements ReActExecutorPort   # engineId="agentscope"
  └─ AgentScopeReActAutoConfiguration                       # @ConditionalOnProperty 默认 false
  └─ TenantContextBridge / BlockingBridge                   # 跨模型适配
```
AgentScope 坐标(本地源码在 `D:\code\ai-pro\as`,产物 `io.agentscope:agentscope`),
通过 `agentscope-dependencies-bom` 管理版本。

### 7.2 两大适配难点与对策

| 冲突 | Seahorse | AgentScope | 对策 |
|------|----------|-----------|------|
| 并发模型 | 阻塞 + 命令式 | 响应式 `Mono/Flux` + `.block()` | 在 adapter 边界 `block()`;流式用 `Flux` 订阅回灌 `StreamCallback` |
| 租户上下文 | `TenantContext` ThreadLocal | Reactor 调度跨线程丢失 ThreadLocal | 用已依赖的 `transmittable-thread-local` + Reactor `Context` 透传 `tenantId` |
| JDK | 21 | 要求 17+ | 兼容,无需降级 |
| 事件模型 | `StreamEventType` 全量 SSE | AgentScope Hook/event | 在 adapter 内做 event→`StreamEventType` 映射,缺失语义降级为 `STEP_PROGRESS` |

### 7.3 映射要点(`AgentLoopRequest` → `ReActAgent`)
- `modelId` → AgentScope `ChatModel`(复用 Seahorse 的 openai-compatible 配置,避免双份模型配置)。
- `allowedToolIds` → AgentScope tool 注册表;MCP 工具仍走 Seahorse 现有 `NativeMcpToolRegistry`,
  以 AgentScope tool 包装暴露,**不重复实现 MCP**。
- `history / memoryContext` → AgentScope memory(仅会话级映射,长期记忆仍用 Seahorse kernel memory)。
- 审批 `WAITING_APPROVAL` / 配额 / 审计:**保留在 Seahorse 侧**,adapter 内不旁路,
  即 AgentScope 仅负责"推理循环",治理仍由 kernel 拦截(否则破坏 SaaS 契约)。

### 7.4 A2A
- 接 `agentscope-extensions-nacos`,把 Seahorse agent 注册为 A2A 可发现服务。
- 新增 `A2AAgentConnectorPort`(outbound),让现有 `application/agent/connector` 通过 A2A 调用远端 agent。
- 服务发现注册信息带 `tenantId`,避免跨租户互调(复用多租户隔离原则)。

### 7.5 Studio
- 接 `agentscope-extensions-studio`,仅在 `agentscope` 引擎启用时挂载。
- OTel span(工作流一)与 Studio trace 共用 traceId,实现"指标 + 链路 + 可视化"三位一体。

### 7.6 验收
- 配置 `seahorse.agent.executor.engine=agentscope` 后,同一组 RAG smoke 用例结果与 kernel 引擎语义等价。
- 切回 `kernel` 立即恢复,无残留。
- A2A:两个 agent 实例可经 Nacos 互相发现并完成一次跨 agent 调用,租户隔离生效。

### 7.7 工作量:**2~4 周**(含适配桥接 + A2A + Studio,可灰度,不阻塞主干)。

## 8. 自动配置集成(对齐 13 层规范)

| 改动 | 层 | 说明 |
|------|----|----|
| `OtelObservationAdapter` 装配 | 不新增层,并入现 observation adapter | `@ConditionalOnClass(Tracer.class)` 缺失即退回 |
| `ReActExecutorPort` / `KernelAgentLoopExecutor` | Layer 5/6(Kernel main / sub-config) | `@Primary`,`@AutoConfigureAfter` 声明 model/tool/context adapter |
| `AgentScopeReActExecutor` | 新增 L1 adapter + 在 Layer 6 注册 | `@ConditionalOnProperty(seahorse.agent.executor.engine=agentscope)` |
| A2A connector | Layer 1 adapter | `@ConditionalOnProperty` 开关,after Nacos client |

**关键规则遵守**:新 kernel 子配置必须在 `@AutoConfigureAfter` 声明所有产生
`@ConditionalOnBean` 依赖的 adapter 配置(见 CLAUDE.md / [[project_autoconfig_fix]])。

## 9. 配置项(新增)

```yaml
seahorse:
  observability:
    tracing:
      enabled: false          # 工作流一开关
      otlp-endpoint: http://localhost:4317
      sampling-rate: 0.1
  agent:
    executor:
      engine: kernel          # kernel(默认) | agentscope,工作流三灰度开关
  agentscope:
    a2a:
      enabled: false
      nacos-server: ${NACOS_ADDR:}
    studio:
      enabled: false
```

## 10. 分期里程碑与依赖

| 阶段 | 内容 | 工作量 | 依赖 | 可独立交付 |
|------|------|--------|------|-----------|
| M1 | 工作流一:OTel 追踪 | 1~2 天 | 无 | ✅ |
| M2 | 工作流二:拆解 loop + 抽 `ReActExecutorPort` | 1~2 周 | 无(建议在 M1 后) | ✅ |
| M3 | 工作流三:AgentScope 适配器 + A2A + Studio | 2~4 周 | **必须 M2 完成**(依赖 `ReActExecutorPort`) | ✅(灰度) |

推进顺序 **M1 → M2 → M3**;M3 强依赖 M2 产出的端口抽象。

## 11. 风险与回退

| 风险 | 等级 | 缓解 |
|------|------|------|
| 重构 loop 引入行为偏差 | 中 | 改造前补齐 SSE 事件序列快照测试;`seahorse-agent-tests` 全回归 |
| AgentScope 响应式与阻塞模型阻抗失配 | 中 | 适配器边界 `block()`;TTL 透传租户;失败可一键切回 `kernel` |
| AgentScope 旁路了 SaaS 治理(配额/审批/审计) | 高 | 治理拦截保留在 kernel 侧,adapter 只做推理,不接管治理钩子 |
| OTel 依赖与现有 micrometer 冲突 | 低 | BOM 统一版本;`@ConditionalOnClass` 缺失即退回 |
| 跨租户 A2A 互调 | 高 | 注册信息携带 `tenantId`,发现层强制同租户校验 |

所有三个工作流均**带开关、可回退**,默认行为与现状一致(engine=kernel、tracing 关、a2a/studio 关)。

## 12. 测试策略

- **M1**:OTLP 导出到本地 collector,断言 span 树结构与 attributes;关开关回归现有指标测试。
- **M2**:三协作者单测 + loop 核心单测;SSE 事件逐项比对(golden file);RAG smoke 必须命中文本证据(沿用现有 smoke 约束)。
- **M3**:`engine=agentscope` 与 `engine=kernel` 跑同一用例集做语义等价对比;A2A 双实例集成测试 + 租户隔离用例。

## 13. 待确认问题(Open Questions)

1. OTLP 后端选型:Jaeger / Tempo / 厂商 APM?(影响 exporter 配置,不影响代码结构)
2. AgentScope 版本锁定:用本地 `D:\code\ai-pro\as` 源码构建,还是 Maven Central `1.0.x`?
3. A2A 服务注册中心:复用现有基础设施还是新引入 Nacos?
4. M3 是否纳入本季度,或先交付 M1+M2 观察收益后再决策?

