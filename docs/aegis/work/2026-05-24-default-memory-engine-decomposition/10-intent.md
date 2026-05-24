# DefaultMemoryEnginePort decomposition — Intent

## TaskIntentDraft

- **Requested outcome**: 按 spec §8 把 `DefaultMemoryEnginePort` 拆成职责清晰的应用服务，确保 spec §8.4 acceptance 达成（facade 行数 ≥ 200 行减少 + 行为不变 + 新 service 无 Spring 注解）。
- **Goal**: facade 从 god class 退化为入口路由 + 编排，主业务逻辑分散到 SRP 服务中；同时保持 `MemoryEnginePort` / `MemoryIngestionWorkflowPort` 契约 byte-identical。
- **Success evidence**:
  - facade 行数减少 ≥ 200。
  - 每次 cut 后 `DefaultMemoryEnginePortTests` (62) + `SeahorseAgentKernelAutoConfigurationTests` (46) + `MemoryWorkflowRoutingTests` (10) 全部通过。
  - 新 service 仅依赖 outbound port，不含 Spring/Redis/Pulsar/Milvus/OpenAI 类型。
  - kernel module 编译通过。
  - 每次提交路径限定，未触碰 HANDOFF 列出的脏改。
- **Stop condition**: spec §8.4 acceptance 全部达成；或边际收益开始倒挂（继续拆需大规模 metadata key 公共化）则停手并交接。
- **Non-goals**:
  - 不引入新功能（不连 ContextReducer 等 dormant 组件）。
  - 不重写 inbound contract（`MemoryEnginePort` / `MemoryIngestionWorkflowPort` 不能改签名）。
  - 不触碰 HANDOFF 中列出的 dirty 文件（jdbc / LocalToolGatewayPort / CompactionService tests 等）。
  - 不引入 metadata key 公共常量类（建议作为 §4.3 拆分前的清场动作单独 PR）。
- **Scope**: kernel application 层 `DefaultMemoryEnginePort` 及其同 package 11 个新服务文件；不改 outbound port 契约，不改 auto-config。
- **Change kinds**:
  - refactor
- **Risk hints**:
  - Worktree 含并行脏改；提交必须路径限定。
  - facade 内含 mojibake 中文日志字面量（需字节级 read/edit 处理）。
  - metadata key 字面量在 cut 4/6/8/11 中重复出现，构成已知技术债。

## BaselineReadSetHint

- `docs/aegis/specs/2026-05-24-design-alignment-next-development.md`（§8 拆分目标与验收）
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryDerivedIndexDispatchService.java`（cut 1 模板）
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePortTests.java`

## ImpactStatementDraft

- **Compatibility boundary**: 保留 `MemoryEnginePort` / `MemoryIngestionWorkflowPort` 公开契约不变；保留 OPERATION_* 字符串字面量（`CORRECTION_UPSERT` / `PROFILE_UPSERT` / `OUTBOX_DELETE_ENQUEUED`）作为 ingestion result 一部分。
- **Affected layers**:
  - `seahorse-agent-kernel/src/main/java/.../application/memory/`（facade + 11 个新 service）
  - `seahorse-agent-tests/src/test/java/.../application/memory/`（仅作为回归验证）
- **Owners**:
  - 接手的 memory-system 开发
- **Invariants**:
  - 四层记忆模型不变。
  - facade 行为 byte-identical（118 个回归用例全过）。
  - kernel pure（无 Spring 注解、无 outbound 包外 import）。
- **Non-goals**:
  - 不动 outbound port / Spring auto-config / web controller / inbound port。

These records are Method Pack drafts / hints, not authoritative runtime decisions.
