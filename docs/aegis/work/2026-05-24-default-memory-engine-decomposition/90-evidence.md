# DefaultMemoryEnginePort decomposition — Evidence

## 测试命令

每次 cut 后执行的回归命令：

```bash
./mvnw -B -pl seahorse-agent-tests test \
    -Dspotless.check.skip=true \
    -Dtest='DefaultMemoryEnginePortTests,SeahorseAgentKernelAutoConfigurationTests,MemoryWorkflowRoutingTests' \
    -Dsurefire.failIfNoSpecifiedTests=false
```

输出（11 次 cut 完全一致）：

```
[INFO] Running com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelAutoConfigurationTests
[INFO] Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryEnginePortTests
[INFO] Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryWorkflowRoutingTests
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 118, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 编译命令

```bash
./mvnw -B -pl seahorse-agent-kernel compile -Dspotless.check.skip=true
```

每次 cut 后均 `BUILD SUCCESS`（858–860 source files）。

## Commit 序列

```
9df495cc refactor(kernel): Slice 3 续 — extract MemoryRefinerFeedbackLookup (cut 11)
4237405a refactor(kernel): Slice 3 续 — extract MemoryOperationCompletionWriter (cut 10)
62c85900 refactor(kernel): Slice 3 续 — extract MemoryOperationBuilder (cut 9)
b7b31c10 refactor(kernel): Slice 3 续 — extract MemoryRefinerMetadataWriter (cut 8)
3cd28a31 refactor(kernel): Slice 3 续 — extract MemoryCanonicalAliasResolver (cut 7)
a5f0b522 refactor(kernel): Slice 3 续 — extract MemoryRefinementInputBuilder (cut 6)
dcc0a0c6 refactor(kernel): Slice 3 续 — extract MemoryProfileValueNormalizer (cut 5 of 5)
663076de refactor(kernel): Slice 3 续 — extract MemoryRefinerBatchCircuitBreaker (cut 4 of 5)
bc0fbed1 refactor(kernel): Slice 3 续 — extract MemoryRefinementContextParser (cut 3 of 5)
bb3c2671 refactor(kernel): Slice 3 续 — extract MemoryTrackWriteService (cut 2 of 5)
e6570c42 refactor(kernel): Slice 3 — extract MemoryDerivedIndexDispatchService (cut 1, prior session)
```

## 文件清单

11 个新 service 文件：

```
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/
├── MemoryCanonicalAliasResolver.java         (120 lines)
├── MemoryDerivedIndexDispatchService.java    (131 lines)
├── MemoryOperationBuilder.java               (169 lines)
├── MemoryOperationCompletionWriter.java      (101 lines)
├── MemoryProfileValueNormalizer.java         (122 lines)
├── MemoryRefinementContextParser.java        (237 lines)
├── MemoryRefinementInputBuilder.java         (189 lines)
├── MemoryRefinerBatchCircuitBreaker.java     (172 lines)
├── MemoryRefinerFeedbackLookup.java          (144 lines)
├── MemoryRefinerMetadataWriter.java          (124 lines)
└── MemoryTrackWriteService.java              (187 lines)
─────────────────────────────────────────────────────
Total new service code:                       1696 lines
```

facade 行数变化：`2156 → 1457 (−699)`

## 静态验证证据

### kernel 纯净（grep 验证无 Spring/外部包导入）

```bash
grep -lE '@(Service|Component|Autowired|Configuration|Bean)' \
    seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/Memory*.java
# (no hits)

grep -lE 'org\.springframework|redis\.clients|org\.apache\.pulsar|io\.milvus|com\.openai' \
    seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/Memory*.java
# (no hits)
```

### Service 体量分布

```bash
wc -l seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/Memory*.java | sort -n
```

```
   100 MemoryCaptureRules.java                       (pre-existing)
   101 MemoryOperationCompletionWriter.java          (cut 10)
   108 MemorySchemaValidator.java                    (pre-existing)
   117 MemoryValueAssessor.java                      (pre-existing)
   120 MemoryCanonicalAliasResolver.java             (cut 7)
   122 MemoryProfileValueNormalizer.java             (cut 5)
   124 MemoryRefinerMetadataWriter.java              (cut 8)
   131 MemoryDerivedIndexDispatchService.java        (cut 1)
   141 MemoryManagementServicePorts.java             (pre-existing)
   144 MemoryRefinerFeedbackLookup.java              (cut 11)
   161 MemoryEngineOptions.java                      (pre-existing)
   169 MemoryOperationBuilder.java                   (cut 9)
   172 MemoryRefinerBatchCircuitBreaker.java         (cut 4)
   187 MemoryTrackWriteService.java                  (cut 2)
   189 MemoryRefinementInputBuilder.java             (cut 6)
   199 MemoryOutboxRelayService.java                 (pre-existing)
   237 MemoryRefinementContextParser.java            (cut 3)
   264 MemoryCaptureCandidateExtractor.java          (pre-existing)
```

所有新 service 均 ≤ 240 行；最大值（MemoryRefinementContextParser）属于纯字符串解析，无 port 依赖。

## 工作区状态（交接时刻）

```
$ git status --short --branch
## main...origin/main [ahead 17]
 M .gitignore
 M seahorse-agent-adapter-cache-redis/src/main/java/.../RedisMemoryAggregationBufferPort.java
 M seahorse-agent-adapter-observation-micrometer/src/test/.../MicrometerObservationAdapterTests.java
 M seahorse-agent-tests/src/test/java/.../RedisMemoryAggregationBufferPortTests.java
 M seahorse-agent-tests/src/test/java/.../DefaultContextWeaverObservationTests.java
 M seahorse-agent-tests/src/test/java/.../MemoryWorkflowRoutingTests.java
?? .claude/
?? .mvn/seahorse-agent-adapter-web/
?? .playwright-cli/
?? CLAUDE.md
?? "docs/Seahorse Agent记忆系统现状与Gemini文档差距对照.md"
?? docs/code-standard-review.md
?? docs/default-memory-engine-port-dependency-review.md
?? frontend/.playwright-cli/
```

所有脏改与未跟踪文件均**未**进入本次 11 个 commit；本次 commit 文件分布严格遵守 path-scoped 规则。
