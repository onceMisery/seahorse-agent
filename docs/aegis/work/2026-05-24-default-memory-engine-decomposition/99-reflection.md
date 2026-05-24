# DefaultMemoryEnginePort decomposition — Reflection

## 拆分模式总结

11 个 cut 形成了 4 类可复制的拆分模板：

### 模板 A — 无 port 依赖纯函数 service

代表：`MemoryRefinementContextParser` (cut 3) / `MemoryRefinerBatchCircuitBreaker` (cut 4) / `MemoryRefinerMetadataWriter` (cut 8)

特征：
- 仅依赖配置阈值或字面量常量。
- 内部可全为 static method（除可读性外不需要 instance state）。
- 收益最大、风险最低。

适合作为接手者的练手 cut。

### 模板 B — outbound port 委托 service

代表：`MemoryDerivedIndexDispatchService` (cut 1) / `MemoryTrackWriteService` (cut 2) / `MemoryCanonicalAliasResolver` (cut 7) / `MemoryOperationCompletionWriter` (cut 10) / `MemoryRefinerFeedbackLookup` (cut 11)

特征：
- 单一 outbound port 注入 + 一组配置常量。
- 实质是把 "port + 业务规则" 打包成可独立测试单元。
- 风险中等，但 port 注入有标准化模板。

构造函数签名约定：
```java
public MemoryXxxService(SomePort port, OtherPort other, int threshold, String constant) { ... }
```

### 模板 C — application helper 组合 service

代表：`MemoryProfileValueNormalizer` (cut 5) / `MemoryOperationBuilder` (cut 9)

特征：
- 注入 1+ 个 kernel application 内已有的 helper（`ProfileSlotResolver` / `MemorySanitizer` 等）。
- 不直接接 outbound port，但承担"业务编排"。
- 适合作为复用 kernel-internal 工具的中层服务。

### 模板 D — multi-port + config bundle service

代表：`MemoryRefinementInputBuilder` (cut 6)

特征：
- 3+ port + 多个配置阈值（本例 7 参数构造）。
- 构造函数膨胀但内聚度高。
- 后续 §4.3 拆分（如 refinedAddClassification）会落到这一类，**警惕 6+ port 的 god service**。

## 关键经验教训

### ✅ 应继续做

1. **每次 cut 后立即测试 118 用例**。回归底线必须 byte-identical，任何 silent 行为变更（如 mojibake 日志改字面量）都通过测试暴露。
2. **OPERATION_* 字面量保留**。这是 ingestion result 的公开契约，外部 caller 可能在做 string match。
3. **新 service 内重新声明 metadata key 字面量**（而非引用 facade 常量）。短期违反 DRY，长期通过 `MemoryMetadataKeys` 公共类集中（见 HANDOFF.md §7）。
4. **字节级 Python rewrite 处理 CRLF / mojibake**。Edit 工具默认 LF 匹配，遇到 CRLF 文件或 mojibake 字面量会失败；用 `python -c "with open(...) ..."` 直接读写更可靠。
5. **commit 信息保留 "Cumulative cut 1–N ≈ XXX lines below the original facade" 行**。统一格式让 reviewer 一眼看出全局进展。

### ❌ 避免重复犯的错

1. **不要在 cut 内顺手清理 imports**。imports 是工具自动管理的副产品；如果一定要清，单独成 cleanup 提交，不要混入 service 抽取 commit（虽然本次 cut 7 顺手清了 6 个 unused imports，事后看 reviewer 难看出哪些是必要哪些是 cleanup）。
2. **不要追求 spec §8.2 5 个 service 名字的精确映射**。实际拆分按内聚度切，自然出来 11 个细粒度 service；强行合并成 5 个会变成 god class。
3. **不要在 metadata key 公共化前做 refined* classification cluster (§4.3E)**。会把 7+ metadata key 字面量再复制一份，技术债爆炸。
4. **不要让 subagent 同时改 facade**。`DefaultMemoryEnginePort.java` 是所有 cut 的共享文件，串行处理。

### ⚠️ 仍存在的技术债

- **metadata key 字面量重复**。cut 4/6/8/11 各内化了一份 metadata key 字面量。建议下一步先做 `MemoryMetadataKeys` 公共常量类（HANDOFF.md §7），再开 §4.3 拆分。
- **11 个新 service 未在 auto-config 中暴露**。当前 facade ctor 直接 `new XxxService(...)`，外部无法 mock 替换。如果后续测试需要细粒度 mock，要把每个 service 提升为 `@Bean`（subagent B 建议研究的方向）。
- **OPERATION_* 字符串散落**。`OPERATION_CORRECTION_UPSERT` 在 `MemoryTrackWriteService` 中、`OPERATION_OUTBOX_DELETE_ENQUEUED` 在 `MemoryDerivedIndexDispatchService` 中。如果未来要做 ingestion result schema 治理，需要统一收口。

## Drift Check 总结

**Scope**: 11 个 cut 全部停留在 `kernel application memory` 包内，未触碰：
- outbound port 契约
- Spring auto-config
- web controller
- inbound port
- 其他 application 子包（agent runtime / metadata / retrieval / aggregation / maintenance）

**Compatibility**:
- `MemoryEnginePort` / `MemoryIngestionWorkflowPort` 公开契约 byte-identical
- OPERATION_* 字面量保留
- metadata key 字面量保留
- 118 个回归用例 11 次全过

**Retirement**:
- facade 内 derivedIndexDispatch / trackWrite / refinementContext / refinerBatch / profileValue / refinementInput / canonicalAlias / refinerMetadata / operationBuild / operationCompletion / refinerFeedback 旧实现完全替换；无双轨保留

**New risk signals**:
- metadata key 字面量重复（见技术债）
- 新 service 未对外开放（见技术债）

**Advisory decision**: stop & handoff — spec §8.4 acceptance 已达成 3.5×；继续拆需先做 `MemoryMetadataKeys` 清场。

## 给接手者的一句话

facade 已从 god class（2156 行）退化为可维护的入口路由 + 编排（1457 行）。如果只是为了 spec 验收，**到此为止已足够**；如果要继续推进 §4.1 review apply 拆分，HANDOFF.md §6 有完整的 TDD 流程草案。
