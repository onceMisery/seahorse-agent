# Seahorse Agent 代码规范专项 Review

> Review 日期：2026-05-21  
> Review 范围：仅从代码规范和设计原则角度分析，包括组合优于继承、魔法值、DRY、SRP、OCP、LSP、ISP、DIP、KISS、YAGNI、LOD。  
> 说明：本次基于最新提交重新扫描，采用“全量结构扫描 + memory 新增代码重点精读 + 代表性文件复核”的方式。结论不评价业务功能是否正确，也不替代安全、性能或架构专项评审。

## 总体结论

项目总体已经有六边形架构雏形：`kernel` 通过 port 表达依赖，adapter 承接 Web、JDBC、模型、搜索、向量库等外部能力。这是符合 DIP/OCP 的方向。最新一批提交明显增强了 memory 能力，但也把当前最主要的规范风险从旧的 metadata/backfill 热点进一步扩展到了 memory 子系统。

主要不符合规范的点集中在五类：

1. **新增 memory 编排类快速膨胀**：`DefaultMemoryEnginePort` 接近 1100 行，`DefaultMemoryRetrievalPipeline` 也承载了大量读取、去重、profile slot、反馈记录和向量召回细节。
2. **少数类承担过多职责**：JDBC 元数据治理适配器、OpenAI 兼容模型适配器、Metadata Backfill 服务、memory 自动配置、部分前端页面和 Zustand store 都过大。
3. **重复代码较多**：Web Controller 响应格式、服务不可用判断、memory profile slot 识别、分页加载、JSON/枚举/文本解析逻辑重复。
4. **接口能力边界过宽或过软**：部分 port 使用大量 `default` 空实现或返回空/false，memory 新增 outbox/profile/correction 相关 port 也继续沿用了这种模式，容易把“能力未实现”变成静默成功或静默空结果。
5. **魔法值和隐式协议散落**：HTTP 响应 `code=0/1`、SSE 事件、checkpoint key、memory track/outbox task/profile slot、分页大小、超时时间、GitHub API URL 等散落在多处。

建议按“先收口规范，再拆高风险类”的顺序推进，避免直接大规模重构。

## 优先级

| 优先级 | 问题 | 主要原则 | 建议处理 |
| --- | --- | --- | --- |
| P1 | Memory 引擎和检索 Pipeline 职责过大且重复 slot 规则 | SRP、DRY、KISS、LOD | 拆 ingestion/retrieval/profile/vector 协作者，集中 slot resolver |
| P1 | 大类/多职责类 | SRP、KISS、LOD | 按 use case 或能力拆分协作者 |
| P1 | Web 响应和服务可用判断重复 | DRY、魔法值、KISS | 统一 `ApiResponse` 与 `requiredPort` |
| P1 | 端口默认空实现过多 | ISP、LSP、OCP | 拆分能力接口，引入显式 unsupported/noop 语义 |
| P2 | Memory outbox task 协议硬编码且 relay 不可扩展 | OCP、魔法值、ISP | 引入 task type enum/handler registry/payload value object |
| P2 | Memory capture/profile/policy 规则散落 | DRY、魔法值、OCP | 提取规则表、`MemoryProfileSlotResolver`、`MemoryTrack` key |
| P2 | Memory 自动配置参数和 noop 装配过重 | SRP、KISS、DIP | 提取 `MemoryProperties` 与 assembler/factory |
| P2 | JDBC JSON/enum/text 解析重复 | DRY、SRP | 提取 `JdbcCommonSupport` 或复用已有 support |
| P2 | 前端页面/store 过大 | SRP、KISS | 拆 hook、service adapter、presentational components |
| P2 | 魔法值和隐式协议散落 | 魔法值、OCP | 提取常量、配置属性、协议对象 |
| P2 | Chat schema upgrader 承担 memory 表迁移 | SRP、LOD | 拆 `JdbcMemorySchemaUpgrade` 或迁移注册器 |
| P3 | 空标记接口和继承式内存端口 | 组合优于继承、ISP | 改为能力组合或明确 marker 语义 |

## 详细问题与建议

### 1. Web Controller 响应协议重复，且存在魔法值

**证据**

- `SeahorseIngestionPipelineController` 在类内重复定义 `KEY_CODE`、`KEY_DATA`、`SUCCESS_CODE`，并在多个方法中直接返回 `Map.of(...)`：  
  `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseIngestionPipelineController.java:44-90`
- 同类问题在 Web adapter 下至少 30 个 Controller 中出现，例如 `private static final String KEY_CODE = "code"`。
- `Service not available` 判断在多个 Controller 中重复出现，例如：  
  `SeahorseIntentTreeController.java:52,58,65,72,79,86,93`  
  `SeahorseIngestionPipelineController.java:59,67,73,81,88`  
  `SeahorseMemoryController.java:57,63,69,76,84,92,101,107,113`
- `SeahorseWebExceptionHandler` 已经有统一异常响应，但业务 Controller 仍直接返回错误 Map：  
  `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebExceptionHandler.java:35-65`

**违反原则**

- DRY：响应字段和错误响应重复。
- 魔法值：`"code"`、`"0"`、`"1"`、`"message"`、`"Service not available"` 散落。
- KISS：每个 Controller 都手写协议细节，增加修改成本。
- OCP：如果响应格式要增加 `traceId`、`requestId`、`timestamp`，需要改很多 Controller。

**建议**

1. 增加统一响应对象，例如：

   ```java
   public record ApiResponse<T>(String code, String message, T data) {
       public static <T> ApiResponse<T> ok(T data) {
           return new ApiResponse<>("0", null, data);
       }

       public static ApiResponse<Void> ok() {
           return new ApiResponse<>("0", null, null);
       }

       public static ApiResponse<Void> error(String message) {
           return new ApiResponse<>("1", message, null);
       }
   }
   ```

2. 增加 Web 层端口解析工具：

   ```java
   final class WebPorts {
       static <T> T required(ObjectProvider<T> provider, Class<T> type) {
           T port = provider.getIfAvailable();
           if (port == null) {
               throw new IllegalStateException(type.getSimpleName() + " is not configured");
           }
           return port;
       }
   }
   ```

3. Controller 只做参数转命令和调用入站端口：

   ```java
   IngestionPipelineInboundPort port = WebPorts.required(pipelinePortProvider, IngestionPipelineInboundPort.class);
   return ApiResponse.ok(port.create(toPayload(request, operator(userId))));
   ```

4. 由 `SeahorseWebExceptionHandler` 统一把异常转为响应，避免业务方法返回错误 Map。

**收益**

- 响应协议只有一个修改点。
- Controller 更贴近 adapter 职责。
- 减少 Controller 内 if/null 检查和重复 `getIfAvailable()`。

### 2. 新增 memory 引擎和检索 Pipeline 职责过大

**证据**

- `DefaultMemoryEnginePort` 接近 1100 行，同时实现 `MemoryEnginePort` 和 `MemoryIngestionWorkflowPort`：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java:91`
- 该类直接持有短期、长期、语义、profile、correction、router、operation log、vector、outbox、business document、lifecycle、retrieval pipeline 等大量协作者：  
  `DefaultMemoryEnginePort.java:101-120`
- 构造函数链非常长，并在构造器里创建 `DefaultMemoryRetrievalPipeline`、capture extractor、value assessor、sanitizer、prefilter、classifier、schema validator：  
  `DefaultMemoryEnginePort.java:122-281`
- `ingest` 主流程同时处理开关、请求校验、operation log、预过滤、语义分类、schema 校验、路由、写入、profile、correction、vector/outbox 等：  
  `DefaultMemoryEnginePort.java:294-321`  
  `DefaultMemoryEnginePort.java:520-607`
- `DefaultMemoryRetrievalPipeline` 新增后也达到 500 多行，负责 route plan、五类 track 加载、向量召回、business document 合并、profile slot 去重、读取反馈记录和 record 转换：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryRetrievalPipeline.java:59-80`  
  `DefaultMemoryRetrievalPipeline.java:106-166`  
  `DefaultMemoryRetrievalPipeline.java:331-370`
- 两个类都定义并实现了 `PROFILE_SLOT_KEYS`、`semanticSlot`、`metadataSlot`、`metadataContainsValue`、`containsAny` 等 profile slot 识别逻辑：  
  `DefaultMemoryEnginePort.java:95-99`  
  `DefaultMemoryEnginePort.java:1046-1120`  
  `DefaultMemoryRetrievalPipeline.java:62-66`  
  `DefaultMemoryRetrievalPipeline.java:457-533`

**违反原则**

- SRP：写入工作流、profile 事实写入、correction ledger、向量索引、outbox fallback、读取 pipeline、profile slot 规则、质量统计混在两个大类中。
- DRY：profile slot 推断和 metadata JSON 字符串匹配重复，后续改 slot 名或规则容易漏改。
- KISS：构造器和主流程过长，新增一个记忆 track 或治理动作需要理解整条链路。
- LOD：engine/pipeline 同时知道底层 port、metadata JSON 结构、profile slot 命名、outbox 载荷、lifecycle 反馈等细节。
- OCP：新增 memory track、profile slot 或 outbox action 时，很可能继续修改两个主类。

**建议**

1. 先提取稳定的小协作者，不立即改外部端口：

   - `MemoryIngestionService`：只负责 ingest 主流程和 operation 状态。
   - `MemoryProfileWriteService`：只负责 profile fact/correction/profile slot 更新。
   - `MemoryVectorIndexingService`：封装同步 upsert 与 outbox fallback。
   - `MemoryRetrievalPipeline` 保持纯读取，去掉写入侧遗留的 slot helper。
   - `MemoryProfileSlotResolver`：集中 `PROFILE_SLOT_KEYS`、metadata 解析、内容规则和去重语义。

2. profile slot 识别不要用字符串 contains 匹配 JSON，优先用 `ObjectMapper` 解析 metadata，再基于字段判断。

3. `DefaultMemoryEnginePort` 保留 façade 兼容时，应只组合和委托：

   ```java
   final class DefaultMemoryEnginePort implements MemoryEnginePort, MemoryIngestionWorkflowPort {
       private final MemoryIngestionWorkflowPort ingestionWorkflow;
       private final MemoryRetrievalPipelinePort retrievalPipeline;

       public MemoryContext loadMemory(MemoryLoadRequest request) {
           return retrievalPipeline.load(request);
       }

       public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
           return ingestionWorkflow.ingest(command);
       }
   }
   ```

4. 拆分前补 characterization tests，尤其覆盖：

   - explicit/profile/preference/fact 捕获。
   - profile slot 去重。
   - correction 与 profile 同时存在时的优先级。
   - vector upsert 失败后的 outbox fallback。
   - lifecycle/read feedback 调用。

**收益**

- memory 写入和读取链路边界清晰。
- 新增 profile slot、track、索引策略时改动点更少。
- 能降低当前 memory 主类继续膨胀的风险。

### 3. Memory capture/profile/policy 规则散落，魔法值和重复规则较多

**证据**

- `MemoryCaptureCandidateExtractor` 中硬编码了大量中文/英文前缀、signal、拒绝原因、长度阈值和类型字符串：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryCaptureCandidateExtractor.java:42-56`  
  `MemoryCaptureCandidateExtractor.java:67-76`  
  `MemoryCaptureCandidateExtractor.java:103`  
  `MemoryCaptureCandidateExtractor.java:151-208`
- `candidate.length() > 120` 是直接写在逻辑里的阈值：  
  `MemoryCaptureCandidateExtractor.java:69-70`
- `inferMemoryType` 返回 `"PROFILE"`、`"PREFERENCE"`、`"FACT"` 字符串，没有使用 enum 或值对象：  
  `MemoryCaptureCandidateExtractor.java:184-208`
- `MemoryPolicyConfig` 虽然把阈值提成常量，但 track key 仍是字符串 map：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryPolicyConfig.java:36-46`  
  `MemoryPolicyConfig.java:94`  
  `MemoryPolicyConfig.java:118-125`
- profile slot key 在 engine 和 retrieval pipeline 重复：  
  `DefaultMemoryEnginePort.java:95-99`  
  `DefaultMemoryRetrievalPipeline.java:62-66`

**违反原则**

- 魔法值：`"profile_statement"`、`"no_high_value_signal"`、`"sensitive_credential"`、`120`、`"PROFILE"` 等缺少统一语义定义。
- DRY：profile slot、记忆类型、track key 多点维护。
- OCP：新增语言规则、记忆类型、track 或 slot 时需要改多个类。
- KISS：业务规则和流程控制揉在一起，review 规则变更时很难看清影响面。

**建议**

1. 提取规则对象和枚举：

   - `MemoryCaptureSignal`
   - `MemoryRejectionReason`
   - `MemoryCaptureType`
   - `MemoryTrack` 作为 `MemoryPolicyConfig.enabledTracks` 的 key，而不是 `Map<String, Boolean>`。
   - `MemoryProfileSlot` 或 `MemoryProfileSlotResolver`。

2. 捕获规则改为表驱动：

   ```java
   record CaptureRule(MemoryCaptureSignal signal, Pattern pattern, MemoryCaptureType type) {}
   ```

3. 阈值进入配置或常量：

   - `MAX_CAPTURE_CANDIDATE_LENGTH`
   - `MIN_CAPTURE_CANDIDATE_LENGTH`
   - `DEFAULT_TENANT_ID`

4. 对外暴露的 rejection reason 要稳定，建议统一放入 enum，并由 enum 提供 code。

**收益**

- 规则变更可以集中 review。
- 多语言扩展和灰度策略更容易接入。
- 避免 slot/track 字符串拼写错误导致静默失效。

### 4. Memory outbox 协议硬编码，relay 对新增任务不开放

**证据**

- `MemoryOutboxRelayService` 只支持一个字符串任务类型 `"VECTOR_UPSERT"`，并用 if 判断未知类型：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryOutboxRelayService.java:29`  
  `MemoryOutboxRelayService.java:48-63`
- relay 直接从 `Map<String, Object>` 读取 `"memoryId"`、`"content"`、`"embeddingModel"`，并把 embedding model 默认成 `"default"`：  
  `MemoryOutboxRelayService.java:56-60`
- `MemoryOutboxPort` 对 `pollPending`、`markSucceeded`、`markFailed` 提供 default no-op/空列表，并在 `MemoryOutboxTask.vectorUpsert` 中再次硬编码 `"VECTOR_UPSERT"`、`"embeddingModel"`、`"default"`：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryOutboxPort.java:30-43`  
  `MemoryOutboxPort.java:67-81`
- `SeahorseMemoryOutboxRelayJob` 中锁名、租约、默认 batch size 和调度延迟也以内联常量/配置默认值存在：  
  `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseMemoryOutboxRelayJob.java:29-44`

**违反原则**

- OCP：新增 outbox task 需要修改 `MemoryOutboxRelayService` 的 if/else。
- 魔法值：任务类型、payload key、默认 tenant/embedding model 分散。
- ISP：`MemoryOutboxPort` 同时表达 enqueue、poll、mark success/failure；写入方可能并不应该承诺 relay 读取能力。
- LSP：default no-op 让 relay 在缺失真实实现时可能表现为“没有任务”或“标记成功”，而不是暴露配置错误。

**建议**

1. 引入任务类型和值对象：

   - `MemoryOutboxTaskType.VECTOR_UPSERT`
   - `VectorUpsertTaskPayload(memoryId, content, embeddingModel)`
   - `MemoryOutboxPayloadMapper`

2. relay 改为 handler registry：

   ```java
   interface MemoryOutboxTaskHandler {
       MemoryOutboxTaskType supports();
       void handle(MemoryOutboxTask task);
   }
   ```

3. 拆分 outbox 能力：

   - `MemoryOutboxCommandPort.enqueue`
   - `MemoryOutboxRelayPort.pollPending/markSucceeded/markFailed`

4. 对 no-op 做显式命名和装配隔离。需要 outbox relay 时，自动配置应要求真实 `MemoryOutboxRelayPort`，不要使用 `MemoryOutboxPort.noop()` 执行调度。

**收益**

- 新增 outbox action 不修改 relay 主类。
- payload 结构可测试、可版本化。
- 缺失实现能尽早暴露。

### 5. Memory 自动配置承担过多装配细节

**证据**

- `SeahorseAgentKernelMemoryAutoConfiguration` 同时配置 policy、engine、router、retrieval pipeline、context weaver、outbox relay/job、ingestion workflow、management service、governance service/job：  
  `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java:77-334`
- policy 的阈值、预算、灰度 key 等通过多个 `@Value` 参数平铺在 bean 方法里：  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:81-89`
- engine 和 retrieval pipeline 重复构造 `MemoryEngineOptions`，并重复使用 short/long/semantic/capture 配置：  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:120-143`  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:168-189`
- 多个 bean 方法大量使用 `getIfAvailable(...::noop)`：  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:136-143`  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:184-189`  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:267-270`  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:304-306`

**违反原则**

- SRP：自动配置类同时是配置读取器、默认值承载者、对象装配器、兼容 fallback 策略承载者。
- DRY：`MemoryEngineOptions` 和 noop 装配逻辑重复。
- KISS：bean 方法参数很长，读者难以判断哪些能力是必需、哪些是可选。
- DIP：业务默认策略被直接写入 Spring 配置层，不利于非 Spring 环境复用。

**建议**

1. 引入 `@ConfigurationProperties`：

   - `MemoryProperties`
   - `MemoryPolicyProperties`
   - `MemoryOutboxProperties`
   - `MemoryGovernanceProperties`

2. 提取装配工厂：

   - `MemoryEngineOptionsFactory`
   - `MemoryPolicyConfigFactory`
   - `MemoryPortsAssembler`

3. 对需要真实能力的 bean 使用 `@ConditionalOnBean` 精确约束；只有明确允许降级的场景才使用 noop。

4. 让 engine 和 retrieval pipeline 共享同一个 `MemoryEngineOptions` Bean，避免配置不一致。

**收益**

- Spring 装配层更薄。
- 配置默认值集中，便于文档化。
- 更容易发现缺失能力，而不是被 noop 掩盖。

### 6. `JdbcChatSchemaUpgrade` 开始承担 memory 表迁移，职责命名不一致

**证据**

- `JdbcChatSchemaUpgrade` 的类名和职责说明仍是 chat schema upgrade，但 `upgrade()` 首先调用 `ensureMemoryProfileTables()`：  
  `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcChatSchemaUpgrade.java:30-41`
- 同一个类中创建 `t_memory_operation_log`、`t_memory_outbox`、`t_user_profile_fact`、`t_memory_correction_ledger`，并处理 memory lifecycle 列升级：  
  `JdbcChatSchemaUpgrade.java:52-162`  
  `JdbcChatSchemaUpgrade.java:193-231`
- `JdbcProfileMemoryRepositoryAdapter` 中仍有 `status = 'ACTIVE'`、`deleted = 0`、tenant 默认 `"default"`、limit fallback `20` 等 JDBC 层魔法值：  
  `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcProfileMemoryRepositoryAdapter.java:50-77`  
  `JdbcProfileMemoryRepositoryAdapter.java:94-128`  
  `JdbcProfileMemoryRepositoryAdapter.java:179-185`

**违反原则**

- SRP：chat schema upgrader 同时管理 memory operation/outbox/profile/correction/lifecycle 表。
- LOD：升级类知道多个 memory 子域的表名、索引、状态字段和默认值。
- 魔法值：`'ACTIVE'`、`0`、`'default'`、`20` 多处出现。
- OCP：继续增加 memory 表时会进一步修改 chat upgrader。

**建议**

1. 拆出 `JdbcMemorySchemaUpgrade`，专门负责 memory 相关 DDL。
2. 建立简单的 schema migration registry，由 starter 注册多个 upgrader：

   - `JdbcChatSchemaUpgrade`
   - `JdbcMemorySchemaUpgrade`
   - 后续 `JdbcKnowledgeSchemaUpgrade`

3. 抽出 memory JDBC 常量：

   - `MemoryJdbcTables`
   - `MemoryJdbcColumns`
   - `MemoryJdbcStatus.ACTIVE`
   - `JdbcMemorySupport.defaultTenant`

4. `JdbcProfileMemoryRepositoryAdapter` 优先复用 `JdbcMemorySupport`，不要在每个 repository 中复制 tenant/status/limit 规则。

**收益**

- schema 迁移按领域分层。
- DDL 增长不会持续污染 chat 迁移类。
- JDBC 层默认值和状态语义更一致。

### 7. `JdbcMetadataGovernanceRepositoryAdapter` 实现端口过多，职责过大

**证据**

- 类长约 1129 行。
- 一个类同时实现 14 个 metadata 相关 port：  
  `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataGovernanceRepositoryAdapter.java:93-99`
- 构造函数中已经组合了多个 support，但主类仍保留大量 SQL、row mapper、统计、兼容门面和工具方法：  
  `JdbcMetadataGovernanceRepositoryAdapter.java:104-125`  
  `JdbcMetadataGovernanceRepositoryAdapter.java:127-220`  
  `JdbcMetadataGovernanceRepositoryAdapter.java:717-959`  
  `JdbcMetadataGovernanceRepositoryAdapter.java:961-1124`

**违反原则**

- SRP：schema registry、dictionary、extraction result、review、quarantine、backfill、quality report、usage report、index status 都在一个适配器门面中。
- ISP：调用方依赖某个小能力时，实际被迫面对一个巨型实现。
- KISS：类内 SQL 和映射逻辑跨度过大，定位问题成本高。
- LOD：主类知道太多表结构、字段名、JSON 结构、统计细节和协作者细节。

**建议**

1. 保持对外 Bean 注册兼容，但内部拆分为独立适配器：

   - `JdbcMetadataSchemaRepositoryAdapter`
   - `JdbcMetadataDictionaryRepositoryAdapter`
   - `JdbcMetadataExtractionResultRepositoryAdapter`
   - `JdbcMetadataReviewRepositoryAdapter`
   - `JdbcMetadataQuarantineRepositoryAdapter`
   - `JdbcMetadataBackfillJobRepositoryAdapter`
   - `JdbcMetadataQualityReportRepositoryAdapter`
   - `JdbcMetadataSchemaUsageRepositoryAdapter`

2. 如果 Spring 自动配置依赖单一 Bean，可以临时保留 façade，但 façade 只委托，不持有 SQL：

   ```java
   final class JdbcMetadataGovernanceRepositoryAdapter
           implements MetadataSchemaRegistryPort, MetadataDictionaryPort, ... {
       private final MetadataSchemaRegistryPort schema;
       private final MetadataDictionaryPort dictionary;
       // methods only delegate
   }
   ```

3. RowMapper 和 SQL 常量按聚合拆分，避免一个类知道所有表。

4. 分阶段执行：

   - 第一阶段：移动 private mapper/helper，不改 public 行为。
   - 第二阶段：让自动配置直接注册细粒度 port。
   - 第三阶段：删除兼容 façade 或标注 deprecated。

**收益**

- 单个类更容易测试和维护。
- 新增 metadata 能力不需要继续修改巨型类，符合 OCP。
- 可以降低 merge 冲突概率。

### 8. `OpenAiCompatibleModelAdapter` 同时承担多个模型端口

**证据**

- 类长约 568 行。
- 一个类同时实现 `ChatModelPort`、`StreamingChatModelPort`、`EmbeddingModelPort`、`RerankModelPort`、`ModelProviderPort`、`TokenCounterPort`、`ModelHealthPort`：  
  `seahorse-agent-adapter-ai-openai-compatible/src/main/java/com/miracle/ai/seahorse/agent/adapters/ai/openai/OpenAiCompatibleModelAdapter.java:66-67`
- 类内同时包含 chat payload、SSE 解析、tool call 聚合、embedding 解析、rerank 解析、HTTP 请求、token 估算、健康状态：  
  `OpenAiCompatibleModelAdapter.java:100-156`  
  `OpenAiCompatibleModelAdapter.java:168-203`  
  `OpenAiCompatibleModelAdapter.java:205-220`  
  `OpenAiCompatibleModelAdapter.java:291-383`  
  `OpenAiCompatibleModelAdapter.java:434-489`
- `recordSuccess` 和 `recordFailure` 是空实现：  
  `OpenAiCompatibleModelAdapter.java:197-203`

**违反原则**

- SRP：HTTP 客户端、Chat、Streaming、Embedding、Rerank、模型发现、健康状态混在一起。
- ISP：使用 embedding 的调用方不应该依赖 chat/stream/rerank 的实现。
- LSP：`ModelHealthPort` 的失败记录方法为空，调用方可能误以为健康状态会被更新。
- OCP：新增 provider 特性时容易继续扩大这个类。

**建议**

1. 提取共享底座：

   - `OpenAiHttpClient`
   - `OpenAiPayloadMapper`
   - `OpenAiSseParser`
   - `OpenAiToolCallMapper`

2. 每个端口独立适配器：

   - `OpenAiChatModelAdapter`
   - `OpenAiStreamingChatModelAdapter`
   - `OpenAiEmbeddingModelAdapter`
   - `OpenAiRerankModelAdapter`
   - `OpenAiModelCatalogAdapter`
   - `OpenAiModelHealthAdapter`

3. `TokenCounterPort` 不建议挂在 OpenAI HTTP adapter 上。近似 token 计数应注册为独立 `ApproximateTokenCounterAdapter`，真实 token 计数可另行实现。

4. 对 `recordSuccess/recordFailure`：

   - 如果暂不支持健康统计，返回 `UnsupportedOperationException` 或使用显式 `NoopModelHealthPort` Bean。
   - 如果要保留 no-op，类名或 Bean 名中必须表达 `Noop`，避免误解。

**收益**

- 每个模型能力可以独立测试和替换。
- 模型 adapter 更符合 ISP/DIP。
- 后续支持不同 OpenAI-compatible provider 的差异时，不需要堆 if/else。

### 9. `KernelMetadataBackfillService` 业务编排过重，checkpoint 协议散落

**证据**

- 类长约 845 行。
- 一个服务同时负责创建任务、运行批次、暂停/恢复/取消、重抽取、文档过滤、对象存储读取、pipeline 执行、隔离队列、观察事件、checkpoint 构造/解析：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataBackfillService.java:66-142`  
  `KernelMetadataBackfillService.java:300-407`  
  `KernelMetadataBackfillService.java:409-425`  
  `KernelMetadataBackfillService.java:550-663`
- checkpoint key 以字符串形式散落：  
  `KernelMetadataBackfillService.java:573-617`  
  例如 `"schemaVersion"`、`"extractorVersion"`、`"documentIds"`、`"forceRerun"`、`"overwriteApproved"`。

**违反原则**

- SRP：任务生命周期、批处理执行、checkpoint 协议、错误隔离、观察事件都在同一个服务。
- 魔法值：checkpoint key、失败阶段 `"FETCH"`/`"EXTRACT"`、跳过原因等散落。
- KISS：批处理主流程需要理解太多细节。
- OCP：新增 checkpoint 字段或 backfill 触发类型时，需要改多处 copy/parse 逻辑。

**建议**

1. 提取值对象 `BackfillCheckpoint`：

   ```java
   record BackfillCheckpoint(
       long currentPage,
       String lastDocumentId,
       int schemaVersion,
       String extractorVersion,
       boolean forceRerun,
       boolean overwriteApproved,
       Set<String> documentIds,
       Map<String, Object> extensions
   ) {}
   ```

2. 提取协作者：

   - `BackfillJobLifecycleService`：create/pause/resume/cancel。
   - `BackfillBatchRunner`：分页、循环、保存进度。
   - `BackfillDocumentProcessor`：单文档处理。
   - `BackfillFailureHandler`：失败记录、quarantine。
   - `BackfillObservationPublisher`：事件上报。

3. 对失败阶段使用 enum：

   ```java
   enum BackfillFailureStage { FETCH, EXTRACT, INDEX, UNKNOWN }
   ```

4. 先加 characterization tests，再拆类，避免破坏兼容行为。

**收益**

- Backfill 主流程会更接近业务语言。
- checkpoint 字段集中管理，不再靠字符串复制。
- 后续扩展 schema compensation、review re-extract 更稳。

### 10. Port 默认实现过多，容易破坏 LSP/ISP

**证据**

- `KnowledgeDocumentRepositoryPort` 对多个管理能力提供默认空/false 实现：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/knowledge/KnowledgeDocumentRepositoryPort.java:50-77`  
  `KnowledgeDocumentRepositoryPort.java:114-152`
- kernel ports 中存在多处 `default List.of()`、`Optional.empty()`、`false`、no-op，例如 `KeywordIndexPort`、`MetadataIndexCompensationPort`、`DocumentRefreshSchedulePort`、`RetrievalStrategyTemplateRepositoryPort` 等。
- `ObjectStoragePort.ensureBucket` 默认 no-op，注释说明是为了兼容旧行为。
- memory 新增 port 继续使用 default/noop：  
  `MemoryOutboxPort.pollPending/markSucceeded/markFailed` 默认空实现或空列表：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryOutboxPort.java:30-43`  
  `ProfileMemoryPort.recordRead` 默认 no-op，`noop()` 返回空/无动作实现：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/ProfileMemoryPort.java:35-53`
- memory 自动配置大量使用 `getIfAvailable(...::noop)`，把缺失能力变成静默降级：  
  `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java:136-143`  
  `SeahorseAgentKernelMemoryAutoConfiguration.java:267-270`

**违反原则**

- ISP：一个 port 同时包含基础写入、管理分页、分块日志、刷新替换、启停、删除等能力。
- LSP：调用方依赖 port 方法时，默认实现返回空/false 可能让业务以为“没有数据”或“操作失败”，而不是“能力未实现”。
- OCP：新增能力时倾向于继续往既有 port 加 default 方法，接口会越来越宽。
- YAGNI：为兼容预留过多默认方法，会让能力边界变模糊。

**建议**

1. 按能力拆接口，而不是在同一 port 增加 default 方法：

   - `KnowledgeDocumentCommandPort`
   - `KnowledgeDocumentQueryPort`
   - `KnowledgeDocumentAdminPort`
   - `KnowledgeDocumentRefreshPort`
   - `KnowledgeChunkQueryPort`

2. 对确实可选的能力，使用显式 capability 或 `UnsupportedOperationException`：

   ```java
   interface KnowledgeDocumentRefreshPort {
       boolean supportsRefresh();
       boolean replaceFileForRefresh(...);
   }
   ```

3. 对兼容期 default 方法加 `@Deprecated(forRemoval = false)` 和迁移说明，避免继续依赖。

4. 自动配置层按 Bean 存在与否装配功能，而不是让调用方调用空实现。

5. memory outbox/profile/correction 这类会影响数据一致性的能力，不建议默认 no-op。至少应区分：

   - `NoopMemoryOutboxCommandPort`：明确表示不落 outbox。
   - `UnsupportedMemoryOutboxRelayPort`：relay 被启用但没有真实实现时直接失败。
   - `ProfileReadFeedbackPort`：把 `recordRead` 从 `ProfileMemoryPort` 中拆出。

**收益**

- 调用方能准确知道能力是否存在。
- 避免“静默空结果”掩盖配置错误。
- 接口更稳定，符合 ISP 和 LSP。

### 11. 空标记接口继承不够表达差异，组合优于继承落实不足

**证据**

- `MemoryStorePort` 定义了所有基础记忆存储方法：  
  `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryStorePort.java:29-70`
- `ShortTermMemoryPort`、`LongTermMemoryPort`、`SemanticMemoryPort` 都是空接口，仅继承 `MemoryStorePort`：  
  `ShortTermMemoryPort.java:25`  
  `LongTermMemoryPort.java:25`  
  `SemanticMemoryPort.java:25`

**违反原则**

- 组合优于继承：当前通过继承表达“记忆类型”，但没有表达不同记忆类型的行为差异。
- ISP：如果某类记忆不支持 `listByConversation` 或 `listByUser`，仍必须承诺这些方法。
- LSP：空子接口没有强化契约，替换时只能靠命名理解语义。

**建议**

1. 如果三类记忆的行为完全一致，可以保留一个 `MemoryStorePort`，用 `MemoryType` 作为参数或 Bean qualifier 区分。
2. 如果行为不同，应拆成组合式能力：

   - `ConversationMemoryQueryPort`
   - `UserMemoryQueryPort`
   - `MemoryWritePort`
   - `MemoryDeletePort`
   - `SemanticMemoryUpsertPort`

3. 如果必须保留 marker 接口，应在接口注释中写清楚它们只是 Spring Bean qualifier，不承诺额外行为。

**收益**

- 类型层表达真实能力，而不只是名称。
- 减少未来新记忆类型被迫继承不需要的方法。

### 12. JDBC adapter 中通用解析逻辑重复

**证据**

- 多个 JDBC 类各自实现 `hasText`：  
  `JdbcConversationMemoryAdapter.java:202`  
  `JdbcConversationRepositoryAdapter.java:139`  
  `JdbcDocumentRefreshScheduleAdapter.java:227`  
  `JdbcKnowledgeBaseRepositoryAdapter.java:240`  
  `JdbcKnowledgeChunkRepositoryAdapter.java:412`  
  `JdbcIntentTreeRepositoryAdapter.java:303`  
  `JdbcKeywordIndexAdapter.java:129`  
  `JdbcMessageFeedbackRepositoryAdapter.java:164`
- 多个 metadata support/adapter 各自实现 `enumValue`：  
  `JdbcMetadataJsonSupport.java:143`  
  `JdbcMetadataGovernanceRepositoryAdapter.java:1182`  
  `JdbcMetadataQualityReportSupport.java:629`  
  `JdbcMetadataReviewSupport.java:350`  
  `JdbcMetadataBackfillSupport.java:245`
- 多个 memory repository adapter 各自实现 `number(Object value, double fallback)`：  
  `JdbcLongTermMemoryRepositoryAdapter.java:143`  
  `JdbcShortTermMemoryRepositoryAdapter.java:158`  
  `JdbcSemanticMemoryRepositoryAdapter.java:166`

**违反原则**

- DRY：相同解析逻辑复制在多个类中。
- SRP：业务 repository adapter 里混入通用转换细节。
- KISS：修复一个解析边界条件时容易漏改。

**建议**

1. 建立统一工具类，例如：

   - `JdbcTextSupport.hasText/trimToNull`
   - `JdbcEnumSupport.enumValue`
   - `JdbcNumberSupport.intValue/longValue/doubleValue`
   - 或统一为 `JdbcCommonSupport`

2. 对已有 `JdbcMemorySupport` 扩展 number/text 功能，避免每个 memory adapter 单独实现。

3. 对 metadata 的 JSON/enum 解析，优先收敛到 `JdbcMetadataJsonSupport`，不要在主 adapter/support 中重复私有实现。

4. 为公共 support 添加单元测试覆盖：

   - null/blank
   - 大小写 enum
   - 非法 enum fallback
   - Number/String 混合输入

**收益**

- 解析行为一致。
- 减少重复代码和未来维护成本。

### 13. 前端页面和 store 过大，混合 UI、数据、校验、转换和副作用

**证据**

- `frontend/src/pages/admin/ingestion/IngestionPage.tsx` 约 2230 行，包含页面状态、表单、加载、校验、节点 JSON/form 双模式转换、上传、任务详情等：  
  `IngestionPage.tsx:50-79`  
  `IngestionPage.tsx:245-290`  
  `IngestionPage.tsx:919-1035`  
  `IngestionPage.tsx:1920-1970`
- `frontend/src/stores/chatStore.ts` 约 484 行，单个 store 同时负责 session 管理、消息映射、SSE 调用、stream 事件处理、取消、反馈、toast、副作用：  
  `chatStore.ts:37-171`  
  `chatStore.ts:172-407`  
  `chatStore.ts:432-490`
- 其他大页面包括：  
  `KnowledgeDocumentsPage.tsx` 约 1526 行  
  `DashboardPage.tsx` 约 1346 行  
  `IntentTreePage.tsx` 约 942 行  
  `AdminLayout.tsx` 约 789 行

**违反原则**

- SRP：页面既是容器、表单控制器、数据加载器、业务规则承载者，又是展示组件。
- KISS：文件过长导致局部修改成本高。
- DRY：分页加载、搜索、错误 toast、JSON 字段校验在多个页面重复。
- LOD：UI 组件直接知道 API 参数、数据结构、错误文案和导航规则。

**建议**

1. 对 `IngestionPage` 拆分：

   - `useIngestionPipelines`
   - `useIngestionTasks`
   - `PipelineEditorDialog`
   - `TaskCreateDialog`
   - `TaskUploadDialog`
   - `PipelineNodeForm`
   - `pipelineNodeMapper.ts`

2. 对 `chatStore` 拆 slice：

   - `sessionSlice`
   - `messageSlice`
   - `streamSlice`
   - `feedbackSlice`

   或至少把 stream event reducer 提取为纯函数，便于测试。

3. 抽通用 hook：

   - `usePagedQuery`
   - `useDebouncedSearch`
   - `useJsonField`
   - `useConfirmAction`

4. 页面中只保留“组合 UI + 调用 hook”，把转换和校验移出。

**收益**

- 页面更容易维护。
- 复杂转换逻辑可单测。
- 降低改一个 dialog 影响整个页面的风险。

### 14. 前端直接访问外部 GitHub API，违反 DIP/LOD

**证据**

- `AdminLayout` 直接 `fetch("https://api.github.com/repos/onceMisery/seahorse-agent")`：  
  `frontend/src/pages/admin/AdminLayout.tsx:202-219`
- `Header` 中也存在类似直接访问 GitHub API 的逻辑。

**违反原则**

- DIP：UI 组件直接依赖 GitHub API，而不是依赖应用自己的服务抽象。
- LOD：布局组件知道外部 API URL 和响应字段 `stargazers_count`。
- OCP：如果后续改 repo、缓存策略、代理策略，需要改 UI 组件。

**建议**

1. 提取 `repositoryStatsService.ts`：

   ```ts
   export async function getRepositoryStars(): Promise<number | null> {
     // GitHub URL 和字段解析集中在这里
   }
   ```

2. 更推荐后端提供 `/admin/repository/stats` 或 `/system/about`，前端只依赖本系统 API。

3. 外部 URL 移到配置：

   - `VITE_REPOSITORY_STATS_URL`
   - 或后端配置项

**收益**

- UI 不直接依赖第三方协议。
- 可以做缓存、降级和限流。

### 15. 魔法值与隐式协议需要集中建模

**证据**

- Web 响应：`"code"`、`"0"`、`"1"`、`"message"`。
- Chat Controller：  
  `sseTimeoutMs` 默认 `300000`，rate limit 默认 `60` 和 `60000`：  
  `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseChatController.java:78-89`
- Chat Controller：conversationId 截断 `64`，taskId 长度 `20`：  
  `SeahorseChatController.java:135-144`
- OpenAI adapter：协议 path、SSE 标记、tool 类型、JSON 字段散落：  
  `OpenAiCompatibleModelAdapter.java:69-77`  
  `OpenAiCompatibleModelAdapter.java:205-220`  
  `OpenAiCompatibleModelAdapter.java:262-288`
- 前端 API timeout `60000`：  
  `frontend/src/services/api.ts:9-12`
- 前端分页大小多处硬编码 `10`、`200`、`6`，例如：  
  `frontend/src/pages/admin/ingestion/IngestionPage.tsx:50-51`  
  `IngestionPage.tsx:245-270`  
  `AdminLayout.tsx:232-237`
- Memory capture：拒绝原因、signal、类型字符串、长度阈值 `120`：  
  `MemoryCaptureCandidateExtractor.java:42-76`  
  `MemoryCaptureCandidateExtractor.java:184-208`
- Memory outbox：`"VECTOR_UPSERT"`、payload key、`"default"` embedding model：  
  `MemoryOutboxPort.java:67-81`  
  `MemoryOutboxRelayService.java:29-60`
- Memory policy：`"correction"`、`"profile"`、`"episodic"`、`"business_doc"`、`"short_window"`：  
  `MemoryPolicyConfig.java:118-125`
- Memory JDBC：`'ACTIVE'`、`deleted = 0`、tenant 默认 `"default"`：  
  `JdbcProfileMemoryRepositoryAdapter.java:50-77`  
  `JdbcChatSchemaUpgrade.java:52-162`

**违反原则**

- 魔法值：读者无法从数字本身理解约束来源。
- OCP：协议变更时需要多点修改。
- KISS：隐式协议靠约定散落，理解成本高。

**建议**

1. 后端增加常量/配置：

   - `ApiResponseCodes.SUCCESS/ERROR`
   - `WebHeaderNames.USER_ID`
   - `SseEventNames.ERROR/DONE`
   - `ChatWebProperties`
   - `OpenAiProtocol`

2. 前端增加常量：

   - `API_TIMEOUT_MS`
   - `DEFAULT_PAGE_SIZE`
   - `SEARCH_RESULT_LIMIT`
   - `SCROLL_SETTLE_DELAY_MS`

3. 对业务协议字段使用 enum 或值对象：

   - `BackfillCheckpointKeys` 只是过渡方案；更推荐 `BackfillCheckpoint` 值对象。
   - `BackfillFailureStage` enum 替代 `"FETCH"`/`"EXTRACT"`。
   - `MemoryOutboxTaskType` enum 替代 `"VECTOR_UPSERT"`。
   - `MemoryCaptureRejectionReason` enum 替代 `"too_long"`、`"question"` 等字符串。
   - `MemoryTrack` enum 替代 policy map 中的字符串 key。

**收益**

- 约束更可读。
- 改协议时影响范围更小。

### 16. YAGNI 风险：兼容门面和预留能力偏多

**证据**

- `JdbcMetadataGovernanceRepositoryAdapter` 中有多处“主适配器仅保留兼容门面”的注释和委托。
- 多个 port 为兼容旧适配器保留 default 方法。
- `OpenAiCompatibleModelAdapter` 把多个模型能力集中在一个类中，即使某些部署只需要其中一种能力。
- memory 自动配置里大量可选能力使用 noop 注入，`DefaultMemoryEnginePort` 也通过多级构造器兼容旧入参，兼容面已经开始挤压主路径。

**违反原则**

- YAGNI：为了兼容或未来能力，在当前类型中保留太多预留面。
- KISS：兼容层和真实能力混在一起，读者难以判断主路径。

**建议**

1. 对兼容层建立明确生命周期：

   - 标注 `@Deprecated`
   - 写迁移目标
   - 在文档或 ADR 中说明计划删除条件

2. 新能力优先新增小 port 和小 adapter，不继续扩展巨型 port/adapter。

3. 对没有真实实现的能力，使用显式 noop bean，而不是塞进真实 adapter。

**收益**

- 兼容代码不会无限期留在主路径。
- 新功能的扩展点更清晰。

## 按原则归类的检查结果

### 组合优于继承

- 内存端口使用空接口继承表达类型，行为差异不足。
- memory engine 现在通过巨型 façade 组合过多能力，但组合没有进一步沉淀为小协作者，导致“组合”变成依赖堆叠。
- 建议改为能力组合或明确 marker 仅用于 Bean qualifier。

### 不能使用魔法值

- `code=0/1`、SSE 事件、checkpoint key、memory track/outbox task/profile slot/rejection reason、分页大小、timeout、URL 等散落。
- 建议集中到响应对象、配置属性、协议常量和值对象。

### DRY

- Web 响应重复。
- Memory profile slot 识别在 engine 和 retrieval pipeline 中重复。
- Memory capture/profile/policy 中类型、signal、track key 多点维护。
- JDBC 通用解析重复。
- 前端分页加载和 JSON 校验重复。
- 建议抽 `ApiResponse`、`WebPorts`、`MemoryProfileSlotResolver`、`MemoryOutboxTaskType`、`JdbcCommonSupport`、`usePagedQuery`、`useJsonField`。

### SRP

- `DefaultMemoryEnginePort`、`DefaultMemoryRetrievalPipeline`、`SeahorseAgentKernelMemoryAutoConfiguration`、`JdbcChatSchemaUpgrade`、`JdbcMetadataGovernanceRepositoryAdapter`、`OpenAiCompatibleModelAdapter`、`KernelMetadataBackfillService`、`IngestionPage`、`chatStore` 职责过多。
- 建议按 use case、端口能力、UI 容器/展示/数据 hook 拆分。

### OCP

- 响应协议、checkpoint key、memory outbox task、profile slot、模型 provider 能力、新 metadata 能力都需要修改现有大类。
- 建议用小接口、小 adapter、值对象和注册式扩展减少修改面。

### LSP

- port default 空实现和 no-op 可能让调用方误判能力存在，memory outbox/profile 的静默空实现尤其容易掩盖数据一致性问题。
- 建议能力未实现时显式失败或通过 capability 暴露。

### ISP

- `KnowledgeDocumentRepositoryPort`、`MemoryOutboxPort`、`ProfileMemoryPort`、模型 adapter、metadata governance adapter 都存在接口过宽问题。
- 建议拆成查询、命令、管理、刷新、统计等细粒度端口。

### DIP

- 后端总体方向较好，kernel 依赖 port。
- 但 memory 自动配置把大量默认策略和 noop fallback 写在 Spring 层，前端布局组件直接依赖 GitHub API，OpenAI adapter 内部把多种协议细节直接耦合到一个类。
- 建议通过 service 抽象或后端 API 隔离外部依赖。

### KISS

- 超长类、超长页面和超长自动配置降低可理解性，memory 子系统当前是新的主要复杂度来源。
- 建议先提取纯函数/协作者，再做模块拆分。

### YAGNI

- 兼容门面、default 空实现、预留能力较多，memory 相关多级构造器和 noop 装配已经开始扩大兼容面。
- 建议为兼容层设置退出条件，避免继续膨胀。

### LOD

- Controller/页面知道过多响应协议、服务可用判断、外部 API、数据转换细节；memory engine/pipeline 知道过多 metadata JSON、slot、track、outbox payload 和 lifecycle 细节。
- 建议封装到 helper、service、hook、adapter mapper、resolver 和 payload mapper。

## 建议落地路线

### 第 1 阶段：低风险规范收口

1. 新增 `ApiResponse`、`ApiResponseCodes`、`WebPorts`。
2. 选 2-3 个 Controller 试点替换 `Map.of` 和 `Service not available`。
3. 新增 `MemoryProfileSlotResolver`，先消除 `DefaultMemoryEnginePort` 和 `DefaultMemoryRetrievalPipeline` 的 slot 规则重复。
4. 新增 `MemoryOutboxTaskType`、`MemoryCaptureRejectionReason`、`MemoryCaptureType`，把 outbox/capture 字符串收口为 enum/code。
5. 新增 `JdbcCommonSupport`，迁移 `hasText`、`enumValue`、`number`。
6. 前端新增 `constants.ts`，收口 timeout/page size/search limit。

### 第 2 阶段：拆分高风险大类

1. 拆 `DefaultMemoryEnginePort`：先提取 `MemoryIngestionService`、`MemoryProfileWriteService`、`MemoryVectorIndexingService`，保留 façade 兼容。
2. 保持 `DefaultMemoryRetrievalPipeline` 只负责读取编排，把 profile/correction 转换、read feedback、slot 去重拆为协作者。
3. 拆 `OpenAiCompatibleModelAdapter`，先提取共享 HTTP/SSE/payload mapper。
4. 拆 `KernelMetadataBackfillService` 的 checkpoint 值对象和 document processor。
5. 拆 `JdbcMetadataGovernanceRepositoryAdapter` 的 schema/dictionary/extraction/review/quarantine/backfill 适配器。

### 第 3 阶段：端口治理

1. 梳理所有 `default` 空实现，分类为：
   - 真正合理的 noop
   - 兼容期 default
   - 应该拆接口的能力
2. 重点处理 `MemoryOutboxPort`、`ProfileMemoryPort`、`KnowledgeDocumentRepositoryPort` 的宽接口和 default/noop。
3. 对兼容期 default 加迁移注释。
4. 自动配置改为按能力 Bean 注册，不依赖大 port。
5. 引入 `MemoryProperties`/`MemoryPolicyProperties`，让 engine、retrieval、governance、outbox 共用配置对象。

### 第 4 阶段：JDBC schema 与 outbox 协议治理

1. 拆 `JdbcMemorySchemaUpgrade`，从 `JdbcChatSchemaUpgrade` 移出 memory DDL。
2. 建立 schema upgrade registry，按领域注册 migration。
3. outbox relay 改为 task handler registry，新增 task 不再修改 relay 主类。
4. 抽 `MemoryOutboxPayloadMapper`，避免 relay 直接读写 `Map<String, Object>`。
5. 收口 memory JDBC 常量：tenant、status、deleted、默认 limit、表名和列名。

### 第 5 阶段：前端职责拆分

1. 拆 `IngestionPage` 的 pipeline/task hook 和 dialog。
2. 拆 `chatStore` 的 stream/session/message/feedback slice。
3. 抽通用分页和 JSON 字段校验 hook。
4. 把 GitHub stars 访问移动到 service 或后端 API。

## 验收建议

每次重构建议满足：

1. 行为不变：先补 characterization tests，再移动代码。
2. 单个类职责可一句话描述清楚。
3. 新增功能优先新增小协作者，不继续扩展大类。
4. Web 响应协议和错误处理只有一个入口。
5. 可选能力必须显式表达“支持/不支持”，避免静默空结果。
