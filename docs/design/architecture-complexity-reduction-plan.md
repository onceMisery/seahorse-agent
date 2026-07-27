# 架构复杂度治理方案（Architecture Complexity Reduction Plan）

> 版本：Phase 0 基线冻结  
> 日期：2026-07-27  
> 负责人：Architecture Governance  
> 状态：Draft → Phase 0 已落地  

## 1 背景与问题

### 1.1 现状度量

通过静态扫描统计当前主干分支的复杂度基线：

| 指标 | 当前值 | 说明 | 期望趋势 |
|------|--------|------|----------|
| **Ports 接口数量** | **804** | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports` 下除 `NoopFallback` 外的 Java 文件数（代表 inbound/outbound port 定义膨胀） | 只许减不许增 |
| **>800 行大类** | **15** | 业务主路径下（排除 `spring-boot-autoconfigure` 自动装配类）行数超过 800 的类 | 只许减不许增，Phase1 拆分为 <400 行 |
| **AutoConfiguration imports** | **106** | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件行数，代表启动器装配复杂度 | 只许减不许增，Phase1 收敛至 <80 |

附加观测：

- `seahorse-agent-kernel` 模块中子域（`application/<domain>`）达 34 个，跨域直接引用经 import 扫描约 30+ 起，ArchUnit 深层依赖扫描约 **35** 处。
- `kernel` 仍存在对 `adapter`、`spring-web`、SDK 的风险依赖入口。
- Controller 层（`adapter-web`）存在直接依赖 `Kernel*ServiceImpl` 的隐患（绕过 Port）。
- Adapter 之间存在潜在的隐式互相依赖。

### 1.2 根因

1. **Clean Architecture 边界未强制化**：仅靠文档约束，缺乏 ArchUnit 自动化门禁，`kernel → adapter` 反向依赖易悄然引入。
2. **Port 膨胀**：每个用例均新增独立 Port，缺乏组合与分层，达到 804。
3. **大类**：`KernelChatInboundService`、`KernelSandboxRuntimeService`、`DefaultMemoryEnginePort` 等承担多重职责，测试成本高。
4. **AutoConfiguration 过载**：106 个自动配置类，启动链路隐式耦合，难以做增量裁剪与可观测。
5. **子域隔离缺失**：`application` 下 34 个子域互相 `import`，形成事务脚本。

---

## 2 目标与原则

### 2.1 北极星指标

- Phase 0：**冻结存量 + 自动化门禁 + 棘轮基线**
- Phase 1：Ports 从 804 → <600（合并 CQRS 读写、引入组合 Port）；>800 行类从 15 → 5；imports 106 → 80
- Phase 2：所有子域通过 Event / Port 通信，移除白名单；Adapter → SPI 注册

### 2.2 设计原则

1. **依赖方向强制化**：`kernel` 零依赖 adapter / `spring-web` / SDK；`domain` 零依赖 `application`。
2. **显式依赖优于隐式**：Controller 只允许依赖 `InboundPort`，禁止直接依赖 `Kernel*Service`。
3. **Adapter 隔离**：每个 `seahorse-agent-adapter-*` 仅依赖 `kernel` + `spring-boot` + 自身 SDK，互不依赖。
4. **子域隔离**：`application.<domain>` 之间默认隔离，跨域必须通过 `ports` 或领域事件，存量 35 处加入白名单逐步消化。
5. **棘轮（Ratchet）**：CI 中复杂度预算只许减不许增，任何 PR 若使指标上升则失败。

---

## 3 基线度量方法

### 3.1 Ports 计数

```bash
find seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports \
  -type f -name "*.java" ! -name "NoopFallback.java" | wc -l
# 当前 804
```

方法说明：统计 Port 定义目录下所有 Java 文件，排除非 Port 的 NoopFallback。未来若拆包，该脚本需同步更新，但趋势保持不变。

### 3.2 >800 行大类

```bash
find . -type f -path "*/src/main/java/*" -name "*.java" \
  ! -path "*/seahorse-agent-spring-boot-autoconfigure/*" \
  -exec wc -l {} \; | awk '$1>800' | wc -l
# 当前 15
```

排除 `spring-boot-autoconfigure`，因为该模块包含大量自动装配类，其大类属于配置聚合，单独治理。

当前 15 个清单（>800 行）：

- `ContainerSandboxRuntimeAdapter` 3849
- `KernelSandboxRuntimeService` 2040
- `DefaultMemoryEnginePort` 1757
- `KernelChatInboundService` 1681
- `HybridMemoryRecallPipeline` 1338
- `LocalToolGatewayPort` 1205
- `KernelRunExperimentService` 1198
- `JdbcChatSchemaUpgrade` 1177
- `SandboxBrowserToolPortAdapter` 1044
- `DefaultSandboxArtifactScannerPort` 1037
- `KernelIngestionTaskService` 973
- `KernelMetadataBackfillService` 932
- `AgentScopeReActExecutor` 886
- `JdbcMemoryLifecycleRepositoryAdapter` 880
- `KernelMemoryManagementService` 872

### 3.3 AutoConfiguration imports

```bash
wc -l < seahorse-agent-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# 当前 106
```

---

## 4 架构门禁规则（ArchUnit） - 4.1 节

新增 Maven 模块 `seahorse-agent-architecture-tests`，引入 `com.tngtech.archunit:archunit:1.2.1`，聚合 `kernel`, `adapter-web` 等已编译类进行扫描。

### 4.1.1 R1: Kernel 不依赖 adapter / Spring Web / SDK

**意图**：Kernel 必须框架无关，仅依赖 `java`, `slf4j`, `jackson`。

ArchUnit 实现：

```java
noClasses().that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
  .should().dependOnClassesThat().resideInAnyPackage(
    "com.miracle.ai.seahorse.agent.adapters..",
    "org.springframework.web..",
    "org.springframework.boot.autoconfigure..",
    "io.milvus..", "org.apache.tika..", "software.amazon.awssdk..",
    "org.redisson..", "org.apache.pulsar..", "org.apache.lucene..",
    "org.elasticsearch..", "io.agentscope.."
  )
```

特殊情况：`adapters.spring` 是装配层，允许依赖 kernel，非反向。测试扫描范围以 `seahorse-agent-kernel` 制品为输入。

**处置**：违规需通过 Port 抽象移动到 adapter。

### 4.1.2 R2: Domain 不依赖 Application

```java
noClasses().that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel.domain..")
  .should().dependOnClassesThat().resideInAPackage("com.miracle.ai.seahorse.agent.kernel.application..")
```

Domain 仅可依赖自身子包、`ports.common`、`java`。该规则防止领域模型被用例污染。

### 4.1.3 R3: Application 子域间隔离（白名单 35 处）

- 将 `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application` 下一级子文件夹视为子域。
- 默认：`application.<domainA>..` 不依赖 `application.<domainB>..` (A≠B)。
- 现存违规通过白名单容忍。

实现策略：

1. 扫描阶段收集所有 `JavaClass.getDirectDependenciesFromSelf()`。
2. 若 sourcePackage = `..application.<X>..` 且 targetPackage = `..application.<Y>..` 且 X≠Y，则判定跨域。
3. 生成 key：`sourceClass -> targetClass` 或 `sourceClass -> targetPackage`。
4. 白名单文件 `src/main/resources/archunit/cross-domain-whitelist.txt` 包含当前 35 条，格式：

```
# 格式：sourceDomain -> targetDomain | sourceClass | targetClass
chat -> agent | com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService | com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopDependencies
...
```

测试逻辑：若依赖在白名单中则忽略，否则失败。Phase1 目标：将 35 条逐步清零，任何新增跨域需要 ADR。

产出物：`scripts/scan-cross-domain.sh` 扫描脚本已确认当前 imports 级别 30 处，ArchUnit 依赖级别 35 处，取 35 作为冻结值。

### 4.1.4 R4: Adapter 互不依赖

每个 adapter 模块独立发布。规则：

```java
// 获取所有 adapter 包前缀：com.miracle.ai.seahorse.agent.adapters.<adapter>
Map<String, Set<JavaClass>> adapterMap...
for each adapterA != adapterB:
  noClasses in adapterA should depend on classes in adapterB
```

允许共同依赖：`kernel`, `org.springframework`, `java`, SDK。

实现中通过 Maven 依赖导入所有 `seahorse-agent-adapter-*` 的 jar，扫描 `com.miracle.ai.seahorse.agent.adapters..`。

例外：`adapter-agent-agentscope` 与 `adapter-agent-agentscope-core` 为父子关系，允许 core → 无，agentscope 可依赖 core，记作白名单。

### 4.1.5 R5: Controller 不直接引用 Kernel*Service 实现类

- Controller 定义：`@RestController` 或类名以 `Controller` 结尾且在 `adapters.web`。
- 禁止依赖 `com.miracle.ai.seahorse.agent.kernel.application..Kernel*Service` 或 `*ServiceImpl`（若有）。

```java
noClasses().that().resideInAPackage("..adapters.web..")
  .and().haveSimpleNameEndingWith("Controller")
  .should().dependOnClassesThat().haveSimpleNameStartingWith("Kernel")
  .and().haveSimpleNameEndingWith("Service")
  .and().resideInAPackage("..kernel.application..")
```

推荐：Controller 只能构造注入 `*InboundPort`。

---

## 5 复杂度预算与棘轮

### 5.1 棘轮文件

`complexity-baseline.txt`（仓库根）：

```
ports=804
large_classes_gt_800=15
autoconfig_imports=106
cross_domain_whitelist_size=35
```

该文件由 `scripts/complexity-report.sh` 生成并对比。

### 5.2 脚本逻辑 `scripts/complexity-report.sh`

1. 计算当前三个指标（同 3 节方法）。
2. 读取基线文件。
3. 若当前 > 基线：报错 `::error::` 并退出 1，且打印对比表。
4. 若当前 < 基线：提示可更新基线（但 CI 不自动更新，需人工 `make baseline-update`）。
5. 输出 Markdown 报告，供 CI summary。

CI 步骤：

```yaml
- name: Complexity budget check (ratchet)
  run: bash scripts/complexity-report.sh
```

只许减不许增的设计保证技术债可控收缩。

### 5.3 基线更新仪式

- 每完成一次重构：运行 `scripts/complexity-report.sh --update-baseline`（若支持）手动更新 `complexity-baseline.txt`。
- PR 必须附带架构影响说明。

---

## 6 CI 接入

### 6.1 现有 CI

`.github/workflows/ci.yml` 中 `backend` job 已执行 `./mvnw verify`。

### 6.2 本方案新增

在 `backend` job 中新增两步（位于 Build & unit test 之后）：

```yaml
- name: Architecture tests (ArchUnit R1-R5)
  run: ./mvnw -B -ntp -pl seahorse-agent-architecture-tests -am test -Dtest=ArchitectureRulesTest

- name: Complexity budget check (ratchet)
  run: bash scripts/complexity-report.sh
```

同时，`.github/workflows-proposed/ci.yml` 为提案路径，实际激活时需 `mv` 至 `.github/workflows/ci.yml`。本 PR 同时更新两处文件以保持一致。

### 6.3 本地验证命令

```bash
./mvnw -B -ntp -pl seahorse-agent-architecture-tests -am test
bash scripts/complexity-report.sh
./mvnw -B -ntp verify -Dtest='!SeahorseE2E*'
```

---

## 7 模块设计：seahorse-agent-architecture-tests

`pom.xml` 关键依赖：

- `com.tngtech.archunit:archunit:1.2.1:test`
- `com.miracle.ai:seahorse-agent-kernel:0.0.1-SNAPSHOT`
- `com.miracle.ai:seahorse-agent-adapter-web:0.0.1-SNAPSHOT`
- 其他 adapters（可选，以 `provided` 引入用于扫描）

目录结构：

```
seahorse-agent-architecture-tests/
  pom.xml
  src/main/resources/archunit/
    cross-domain-whitelist.txt  # 35 条
  src/test/java/com/miracle/ai/seahorse/agent/arch/
    ArchitectureRulesTest.java
    R1KernelIsolationTest.java
    R2DomainIsolationTest.java
    R3SubdomainIsolationTest.java
    R4AdapterIsolationTest.java
    R5ControllerDependencyTest.java
    Whitelist.java
```

测试聚合类 `ArchitectureRulesTest` 直接运行所有规则，确保单入口。

---

## 8 后续路线（Phase 1/2 预告）

### Phase 1（重构）

- Port 合并：引入 `ChatInboundPort` 组合 `Memory + Retrieval` 依赖，减少重复 Port。
- 大类拆分：`KernelChatInboundService` 拆为 `ChatPreparationFacade` + `ChatExecutionFacade`，每类 <400 行。
- AutoConfig 收敛：105→80，抽取 `KernelModuleRegistrar` SPI，按 Feature Gate 条件装配。
- 子域事件化：`chat -> agent` 改为 `AgentInvocationPort.publish()`，移除直接类依赖。

### Phase 2（平台化）

- Adapter SPI 注册中心，启动时按 Profile 动态加载。
- 引入 `seahorse-agent-kernel-api` 纯接口包，`kernel` 实现包分离。
- 复杂度预算演进为 `jdepend Cycle + Cognitive Complexity` 混合指标。

---

## 9 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| ArchUnit 扫描范围过大导致误报（Spring） | 中 | 构建失败阻塞 | 细化 forbidden package，引入 allow-list |
| 白名单 35 处无法短期消化 | 高 | 债务残留 | PR 模板要求新增跨域需 ADR + 否决默认 |
| Ports 计数方法争议 | 低 | 指标波动 | 明确脚本口径为 `ports/.../*.java - NoopFallback` |
| CI 成本增加 | 低 | +2 min | 架构测试仅扫描已编译类，缓存 Maven |

---

## 10 附录

### 10.1 本地扫描跨域脚本（已执行）

```bash
python3 scripts/scan-cross-domain.py
# 输出 30 imports, 35 ArchUnit级，取 35 为冻结值
```

### 10.2 基线文件内容

参见根目录 `complexity-baseline.txt`。

### 10.3 关联文档

- `docs/architecture/current-code-architecture.md`
- `seahorse-architecture.md`
- `.github/workflows-proposed/ci.yml`（新版）

---

**签署**：本方案 Phase 0 以「冻结 + 门禁 + 棘轮」为核心，先止血再重构。所有新增模块均需同时通过 ArchUnit 与复杂度预算检查。
