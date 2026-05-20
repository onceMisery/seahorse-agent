# Gemini Agent 记忆系统完整设计方案

## 1. 设计目标与核心原则

Gemini Agent 记忆系统的目标不是把全部对话永久保存，而是在保证安全、低延迟和可控成本的前提下，把“未来有复用价值的信息”沉淀为可检索、可更新、可忘却的结构化记忆。

系统遵循以下原则：

1. **流程引擎控制确定性边界**：触发时机、隐私过滤、Schema 校验、数据库写入、并发控制和生命周期管理由工作流引擎负责，不能交给概率模型自由发挥。
2. **LLM 只作为语义算子**：大模型负责理解自然语言、提取原子事实、判断冲突和生成结构化操作，不直接持有数据库写权限。
3. **读写链路分离**：写入管道异步运行，降低对话时延；读取管道同步低延迟运行，负责动态检索和 Prompt 注入。
4. **结构化事实优先于向量相似度**：用户身份、技术栈、纠错规则等强事实使用 KV/关系型/图结构保存；向量库只保存事件片段、历史讨论和文档 Chunk。
5. **高保真业务知识与高度提炼画像分轨存储**：个人画像可以高度总结，业务文档必须高保真、可溯源、可版本化。
6. **显式纠错拥有最高优先级**：用户明确纠正的事实写入 Correction Ledger，并优先注入模型上下文。
7. **记忆必须可更新、可废弃、可回收**：通过 MVCC/CAS、Tombstone、Generation ID、TTL、半衰期衰减和离线 GC 解决冲突与陈旧数据。

## 2. 整体架构设计

系统整体分为四个阶段：写入、存储、检索、生命周期管理。

```text
用户对话/业务文档
   │
   ├── 写入管道 Ingestion Pipeline（异步）
   │      ├── Trigger/Debounce
   │      ├── Sanitization 前置静态洗涤
   │      ├── Pre-Filter 信息过滤
   │      ├── Semantic Classifier 语义打标
   │      ├── LLM Refiner 结构化提炼
   │      ├── Schema Validation
   │      └── Conflict Resolution + Persistence
   │
   ├── 存储层 Storage
   │      ├── KV/关系型数据库：用户画像、纠错本、版本号、状态
   │      ├── 向量数据库：事件碎片、文档 Chunk、语义增强文本
   │      ├── BM25/倒排索引：API 名、字段名、规则编号、精确关键词
   │      └── 图数据库/轻量关系图：实体、关系、别名、依赖、因果链
   │
   ├── 读取管道 Retrieval Pipeline（同步）
   │      ├── Memory Router
   │      ├── Profile/KV Fetch
   │      ├── Vector ANN Search
   │      ├── BM25 Sparse Search
   │      ├── Graph Expansion / Parametric Graph Activation
   │      ├── Rerank + Token Budget
   │      └── Context Weaver Prompt 注入
   │
   └── 生命周期 Lifecycle Management
          ├── Temporal Overwrite
          ├── Tombstone / Generation Invalidation
          ├── Offline Compaction
          ├── Decay / TTL
          └── Async GC
```

### 2.1 数据流转

**写入方向**：

```text
对话结束/静默期/用户显式保存
   -> UserUtteranceSaved 事件
   -> MQ(Kafka/Pulsar)
   -> Worker 执行 MemoryIngestionWorkflow
   -> 清洗、过滤、分类、提炼
   -> 生成 ADD/UPDATE/DELETE/IGNORE 操作
   -> Schema 校验
   -> KV/Vector/Graph/BM25 持久化
   -> 触发压缩、衰减、GC 后台任务
```

**读取方向**：

```text
用户新输入
   -> Memory Router 判断激活哪条记忆轨道
   -> 并发读取 Profile KV、Correction Ledger、短期滑窗、向量库、业务索引
   -> 应用 Metadata Filter、Generation Filter、Rerank
   -> Context Weaver 控制 Token 预算并分区组装 Prompt
   -> 注入 LLM System Prompt
   -> LLM 结合当前输入生成回答
```

### 2.2 策略控制

策略控制由流程引擎集中实现，主要包含：

| 策略 | 实现位置 | 作用 |
| --- | --- | --- |
| 触发策略 | Trigger/Debounce | 避免每句话都写记忆，通常在会话结束、静默 5 分钟、点击 Clear、显式“记一下”时触发 |
| 隐私策略 | Sanitization | 密码、Token、银行卡、身份证等由死代码拦截或占位符替换 |
| 过滤策略 | Pre-Filter + Classifier | 过滤问候、闲聊、单次日志、临时 Debug，保留长期事实、项目、偏好、纠错 |
| 冲突策略 | Conflict Resolver | 新事实覆盖旧事实，旧事实标记 historical/deprecated/obsolete |
| 预算策略 | ContextSynthesizer | 单次记忆注入通常限制在固定 Token Budget，例如 512 Token |
| 一致性策略 | MVCC/CAS + Generation ID | KV 作为事实锚点，向量通过世代和元数据与 KV 对齐 |
| 权限策略 | Tenant/Auth Filter | 每次检索都携带用户、租户、权限 Token，隔离跨用户和跨业务域数据 |
| 生命周期策略 | Decay + Compaction + GC | 低价值

## 3. 记忆层级结构

系统采用类似计算机缓存金字塔的三层记忆层级，并扩展出双轨异构矩阵。

```text
+-------------------------------------------------------------+
| 1. 短期工作内存 Short-Term Window / L1 Cache                  |
|    原始 Token 滑动窗口，高保真、强时序、会话级有效            |
+-------------------------------------------------------------+
                              │ 异步提炼/定时触发
                              ▼
+-------------------------------------------------------------+
| 2. 长期事实记忆 Profile / Entity / Vector / L2 Storage        |
|    结构化 KV、实体图、向量碎片、业务文档 Chunk                 |
+-------------------------------------------------------------+
                              ▲
                              │ 显式修正/冲突覆盖
+-------------------------------------------------------------+
| 3. 用户纠错本 Correction Ledger / Ring 0                      |
|    最高优先级的强规则约束层                                   |
+-------------------------------------------------------------+
```

### 3.1 短期工作内存

短期工作内存就是当前 Transformer Context Window 中的会话上下文。它保存原始对话、代码片段、报错日志和多轮推理链。

特征：

- **高保真**：不总结、不压缩，尽量保持用户原始表达。
- **强时序**：适合当前会话中的多轮代码分析和 Debug。
- **窗口有限**：通常只保留最近 5-10 轮对话或当前任务所需片段。
- **寿命短**：随着会话窗口滑动、会话结束或归档而失效。
- **不默认持久化**：长日志、一次性报错会留在短期窗口帮助排查，但通常不会进入长期记忆。

### 3.2 长期事实记忆

长期事实记忆保存可以跨会话复用的结构化事实。

典型内容：

- 用户职业、角色、掌握技能等，如 技术栈、常用数据库、中间件、IDE。
- 进行中的长期工作或项目，如 `seahorse-agent`、`ApiHub`、PIP 编号。
- 明确偏好，如使用某类工具、架构审美、输出风格。
- 长期业务域，如 ERP、返利核销、财务对账。
- 历史事件片段，如“上周处理过 OceanBase OMS 多节点乱序问题”。

长期事实记忆内部再分为：

| 子层 | 存储 | 适合内容 |
| --- | --- | --- |
| Profile KV 树 | Redis/Bigtable/OceanBase/MySQL JSON | 强事实、低延迟读取、可覆盖更新 |
| Entity Graph | Neo4j/FalkorDB/关系型边表 | 实体关系、别名、依赖、因果链 |
| Episodic Vector Cache | Milvus/JVector/HNSW 向量扩展 | 事件碎片、语义回忆、历史方案 |
| Business Document Store | Vector DB + BM25 + RDBMS | 高保真业务文档 Chunk、规则、API、版本 |

### 3.3 用户纠错本

Correction Ledger 是最高优先级记忆层，专门保存用户显式纠错。

示例：

```json
{
  "entity": "Database",
  "incorrect_value": "SQL Server",
  "correct_value": "OceanBase",
  "reason": "用户显式指出生产环境使用的是 OceanBase 数据库，纠正了 AI 的语法生成假设",
  "priority": "HARD_RULE",
  "updated_at": 1775021200
}
```

该层的规则：

- 明确否定、纠正、替换语义直接进入纠错本。
- 注入 Prompt 时排在 Profile 与检索碎片之前。
- 与其他记忆冲突时优先级最高。
- 可用于检索期硬过滤，例如把旧 `SQL Server` 假设从上下文中剔除。

## 4. 动态检索与 Prompt 注入机制

系统收到用户输入后，不会直接调用 LLM，而是先进入 Pre-processing 网关和 Memory Router。

```text
[用户输入]
   │
   ▼
触发语义路由 Memory Router
   │
   ├── 显式检索：用户问“我是谁/我用什么技术栈”
   │       -> 拉取完整 User Profile
   │
   ├── 隐式检索：输入包含 OceanBase、Pulsar、返利等实体
   │       -> Lexical Match + Vector Search + BM25
   │
   ├── 纠错拦截：输入包含“不是/错了/改成/以后别”
   │       -> Correction Ledger 读写
   │
   └── 业务检索：输入询问规则、API、流程、金额阈值
           -> Business Doc Track 检索
```

### 4.1 Prompt 组装公式

基础公式：

$$
\text{Final Prompt}
= \text{System Prompt}
+ \text{Correction Ledger}
+ \text{Retrieved Profile}
+ \text{Retrieved Business Rules}
+ \text{Retrieved Episodic Shards}
+ \text{Short-Term Context}
+ \text{User Input}
$$

纠错本位于最前，是因为它代表用户明确修正后的硬事实。

### 4.2 上下文分区注入

当个人画像和业务文档同时激活时，使用 XML/分区标签隔离，避免模型注意力混淆。

```markdown
# Role
你是一个深度嵌入企业业务流的 AI 架构专家搭档。请结合当前用户信息和企业业务规范回答。

# ZONE 1: 当前用户信息
<user_profile>
- 核心角色: 高级 Java 开发工程师
- 技术栈资产: OceanBase, Apache Pulsar, Trae IDE
- 正在推进的项目: seahorse-agent, ApiHub
</user_profile>

# ZONE 2: 企业业务规范限制
<business_rules>
- 规则源: 《集团季度末返利核销管理规范 V2》
- 核心断言: 季度末返利核销对账中，若累计差异金额 > 5000 元，付款流转将自动进入冻结状态，必须由事业部财务一号位审批方可解冻。
</business_rules>

# ZONE 3: 当前对话上下文与用户输入
[Short-term Memory Window]
用户：这批数据尾差累计到 5200 块了，在系统里直接改报错了。

# 协同推理要求
1. 语言风格贴合用户画像。
2. 业务结论必须服从业务规则。
3. 行动建议结合用户技术栈。
```

### 4.3 Token 预算与重排

检索出来的记忆不能全部注入，必须先打分、排序、裁剪。

综合打分公式：

$$
\text{Score}
= \alpha \cdot \text{VectorSimilarity}
+ \beta \cdot e^{-\lambda(T_{\text{now}} - T_{\text{fact}})}
+ \gamma \cdot \text{InteractionFrequency}
$$

简化版读管道重排公式：

$$
\text{Score}
= w_1 \cdot \text{VectorSimilarity}
+ w_2 \cdot e^{-\lambda(T_{\text{now}} - T_{\text{fact}})}
$$

含义：

- `VectorSimilarity`：当前问题与记忆片段的向量相似度。
- `T_now - T_fact`：记忆年龄或距离上次唤醒的时间差。
- `InteractionFrequency`：该记忆过去被反复引用的频次。
- `lambda`：衰减因子，职业/核心技术栈趋近 0，临时行程或一次性报错较大。

裁剪策略：

1. 给记忆注入分配硬 Token Budget，例如 512 Token。
2. 按 Score 降序排序。
3. 贪心选择最高分片段，直到 Token 预算耗尽。
4. 对冲突片段执行硬剔除。
5. 对业务规则和纠错本设置保底预算，不被普通向量碎片挤出。

## 5. 信息过滤器与语义打标分类器

两份文档强调：过滤器和分类器不能只靠一个巨大 Prompt 实现，否则会出现 Lost in the Middle、Token 成本过高和响应时延上升。合理架构是分布式、管道式混合过滤。

### 5.1 三层过滤架构

```text
[原始 Session 文本]
   │
   ▼
第一层：轻量级前置过滤器 Pre-Filter
   -> Regex、Trie、BPE Token 统计、依存句法、轻量 Embedding 阈值
   -> 过滤问候、表情、闲聊、长日志、一次性报错、敏感信息
   │
   ▼
第二层：语义打标分类器 Semantic Classifier
   -> RoBERTa/DeBERTa/BERT-style 微调模型或轻量 LLM
   -> 切 Chunk，打 Domain、Lifespan、Intent 标签
   -> 计算信息熵/新颖度，过滤低信息增量片段
   │
   ▼
第三层：LLM 记忆提炼器 Refiner
   -> JSON Mode / Structured Outputs
   -> 输出 Profile 更新、纠错本、业务规则增强字段
```

### 5.2 前置过滤器设计

前置过滤器目标是用低成本 CPU 算子拦截大部分无价值内容。

主要规则：

- 问候、确认、礼貌短句：`哈哈`、`谢谢`、`收到`、`好的`。
- 单次 Debug 噪音：长 Stacktrace、编译日志、一次性异常。
- 敏感数据：密码、API Key、银行卡、身份证、验证码、Session Key。
- 普适常识：`会用 Git`、`用 IDE 写代码`、`注重代码质量`。

安全洗涤链路：

```text
原始对话文本
   -> AC自动机/Trie树
   -> 高并发正则引擎
   -> Token 占位符替换
   -> 洁净文本
```

占位符替换优于直接删除。示例：

```text
输入：我的临时密钥是 sk-proj-123456abcdef
输出：我的临时密钥是 <REDACTED_API_KEY_SECRET>
```

这样既保护密钥，又保留“用户在讨论 API 密钥”的语义。

### 5.3 语义打标分类器

分类器将文本切成语义片段，并打上主领域标签和时效标签。

除标签外，分类器还可以计算片段的信息熵或新颖度，用来过滤“低信息增量”的日常闲聊。工程上可把它实现为规则分 + 模型分类分 + 熵/新颖度分的组合，而不是让 LLM 单独判断。

个人画像标签体系：

| 标签类型 | 标签 |
| --- | --- |
| Domain | `Professional_Skill`、`Active_Project`、`Infrastructure_Preference`、`Business_Domain`、`Workflow_Tool` |
| Lifespan | `Ephemeral`、`Iterative`、`Perennial` |

过滤决策：

```text
仅当 Chunk 满足：
  Lifespan in {Iterative, Perennial}
  AND Domain in 核心领域标签
  AND novelty_score >= threshold
  AND entropy_score >= threshold
才进入 LLM Refiner。
```

示意打分：

$$
\text{KeepScore}
= a \cdot \text{DomainConfidence}
+ b \cdot \text{LifespanConfidence}
+ c \cdot \text{Novelty}
+ d \cdot \text{InformationEntropy}
- e \cdot \text{SensitivityRisk}
$$

当 `KeepScore` 低于阈值时直接丢弃或只保留在短期工作内存中。

业务文档标签体系：

| 维度 | 示例 |
| --- | --- |
| Domain | 销售返利、供应链采购、财税逻辑 |
| Type | Rule/Policy、API_Spec、Glossary |
| State | Active、Deprecated、Draft |

业务文档必须使用 Metadata Filter，否则纯向量检索可能把旧版规则、相似业务域或草案误召回。

### 5.4 信息价值评级

长期存储优先级：

| 维度 | 存储标准 | 丢弃标准 |
| --- | --- | --- |
| 事实与属性 | 核心社会属性、角色、技术栈、高频软件/硬件 | 今日流水账、临时状态 |
| 持续性项目 | 有名称、版本、仓库、周期跨数天/数周 | 一次性 Demo 或单次 Debug |
| 业务/技术偏好 | 排他性选择、明确工具习惯、架构审美 | 大众共识、泛泛观点 |
| 纠错与断言 | 包含“不、错、不是、改为”等强否定纠正 | 普通文案润色要求 |

辅助触发信号：

- **高频词频与语义密度**：某实体跨多个 Session 反复出现，权重上升。
- **强情感锚点**：如“头疼”“线上大坑”“最近死磕”，通常指向核心瓶颈。
- **显式保存指令**：如“记一下”“保存配置”。
- **连续讨论触发**：连续 3 轮围绕非通用概念讨论，触发全量 Summary 提取。

## 6. 双轨异构记忆矩阵

系统需要同时支持“用户是谁”和“业务规则是什么”。两者不能混用同一套 Pipeline，因为目标完全不同。

```text
[用户输入 Prompt]
       │
       ▼
┌───────────────────────────────┐
│ Central Memory Router          │
└───────────────┬───────────────┘
                │
      ┌─────────┴─────────┐
      ▼                   ▼
┌──────────────┐    ┌────────────────┐
│ 用户画像记忆流 │    │ 业务文档知识流   │
│ User Profile │    │ Business Docs  │
├──────────────┤    ├────────────────┤
│ 高度提炼      │    │ 高保真          │
│ 高频动态更新  │    │ 可溯源          │
│ KV/图/向量    │    │ Vector+BM25+RDB │
└──────┬───────┘    └────────┬───────┘
       └──────────┬──────────┘
                  ▼
        Context Weaver
                  ▼
             LLM 推理生成
```

### 6.1 第一轨：个人画像记忆流

目标：理解用户身份、长期偏好、技术栈、活跃项目。

特征：

- 高度总结，丢弃大量细节。
- 强一致更新，KV/关系型数据库作为事实锚点。
- 高频读取，每次对话可能都需要注入核心画像。
- 纠错本和显式覆盖优先级高。

典型存储：

```json
{
  "user_id": "usr_9527",
  "meta_version": 42,
  "profile": {
    "occupation": "Java Developer",
    "technical_stack": ["Java", "Go", "OceanBase", "Istio"],
    "open_source_projects": ["apache/dubbo", "apache/pulsar"],
    "github_handle": "onceMisery"
  }
}
```

### 6.2 第二轨：业务文档知识流

目标：高保真记住业务规则、API 规范、PRD、财务核销流程等。

特征：

- 不追求高度总结，而追求可追溯、可版本化。
- 不能丢失表格、参数、阈值、规则编号。
- 同时使用 Dense Vector、Sparse BM25、Metadata Filter。
- 版本字段必须参与检索过滤。

业务文档 Chunk 元数据建议：

```json
{
  "chunk_id": "chk_001",
  "doc_id": "rebate_policy",
  "version": "v2",
  "is_active": true,
  "publish_date": "2026-05-01",
  "domain": "Rebate",
  "type": "Rule/Policy",
  "state": "Active",
  "chapter_path": "第三章 -> 销售返利核销 -> 违约责任",
  "source_span": {
    "page": 12,
    "paragraph": 4
  }
}
```

### 6.3 业务文档摄取

业务文档的过滤器不是“丢弃文本”，而是结构解析器。

流程：

```text
PDF/Markdown/HTML/Word
   -> Layout-Aware Parser
   -> 识别 H1/H2/H3、表格、代码块、API 参数表
   -> 去除页眉页脚、目录、审批人、修订日志
   -> 生成 Semantic Tree
   -> Chunking
   -> 三维标签 Domain/Type/State
   -> LLM Semantic Enhancement
   -> Vector + BM25 + RDBMS 持久化
```

表格必须保留为 Markdown Table 或 JSON，不能拉平成无序文本，否则会丢失空间对齐关系。

业务文档增强 Prompt 输出：

```json
{
  "business_rules": [],
  "entities_mentioned": [],
  "hypothetical_queries": [],
  "contextualized_text": ""
}
```

其中：

- `business_rules` 保存显式 if-then-else 规则。
- `entities_mentioned` 保存客户、供应商、返利比例等实体。
- `hypothetical_queries` 提升向量召回率。
- `contextualized_text` 将章节路径融入 Chunk，使片段自包含。

### 6.4 双轨并行读取

Java 风格伪代码：

```java
CompletableFuture<UserProfile> profileFuture =
    CompletableFuture.supplyAsync(() -> profileRepository.getById(userId));

CompletableFuture<List<BusinessRule>> rulesFuture =
    CompletableFuture.supplyAsync(() -> vectorSearchService.search(query, metadataFilter));

CompletableFuture.allOf(profileFuture, rulesFuture).join();

UserProfile profile = profileFuture.get();
List<BusinessRule> rules = rulesFuture.get();
```

双轨同时激活的场景：

- 用户提出“这笔返利对账又卡住了”。
- Profile Track 告诉系统用户是 Java 开发，使用 OceanBase 和 ERP。
- Business Doc Track 召回“累计差异超过 5000 元触发付款冻结”。
- Context Weaver 组装后，模型给出技术与业务结合的解释。

## 7. 底层流程引擎设计

底层流程引擎是状态机 + 事件驱动 DAG。LLM 被包装为一个节点，节点之间的条件分支、重试、熔断和数据库事务由代码控制。

### 7.1 写管道状态机

```text
UserDialogueFinishedEvent
   -> QUEUED
   -> SANITIZED
   -> PREFILTERED
   -> CLASSIFIED
   -> EXTRACTED
   -> VALIDATED
   -> CONFLICT_RESOLVED
   -> PERSISTED
   -> COMPACTED / GC_PENDING
```

四大确定性/语义算子：

```text
[用户话术]
   │
   ▼
1. 前置洗涤算子 Trie/Regex
   -> 物理截断、符号占位、隔绝高危隐私
   │
   ▼
2. LLM 提取算子 JSON Mode
   -> 状态感知、语义降维、输出结构化意图
   │
   ▼
3. 后置校验算子 Schema Match
   -> 强类型卡点、语法自愈、死信队列熔断
   │
   ▼
4. 冲突持久化算子 MVCC/CAS
   -> 乐观锁控制、关系映射、向量标量联合过滤
   │
   ▼
[进入记忆数据库]
```

### 7.2 MemoryIngestionWorkflow 伪代码

```python
class MemoryIngestionWorkflow:
    def __init__(self, mq_client, db_client, llm_client):
        self.mq = mq_client
        self.db = db_client
        self.llm = llm_client

    def execute(self, event: UserDialogueFinishedEvent):
        clean_turns = []
        for turn in event.dialogue_turns:
            safe_text = REGEX_COMPONENTS.filter_sensitive_data(turn.text)
            clean_turns.append(safe_text)

        llm_response = self.llm.call_with_json_schema(
            prompt=MEMORY_EXTRACTOR_SYSTEM_PROMPT,
            inputs={
                "dialogue": clean_turns,
                "existing_context": self.db.kv_db.get_profile_snapshot(event.user_id)
            },
            schema=MemoryOperationSchema
        )

        try:
            operations = json.loads(llm_response)
            self.validate_schema(operations)
        except ValidationError:
            self.mq.move_to_dead_letter_queue(event)
            return

        with self.db.start_transaction() as tx:
            for op in operations:
                if op.action == "IGNORE":
                    continue

                if op.action == "ADD":
                    if op.target == "profile":
                        tx.kv_db.json_path_update(event.user_id, op.path, op.value)
                    elif op.target == "episodic":
                        tx.vector_db.upsert(
                            id=op.id,
                            vector=get_embedding(op.value),
                            text=op.value,
                            metadata=op.metadata
                        )

                elif op.action == "UPDATE":
                    tx.kv_db.cas_update(
                        user_id=event.user_id,
                        path=op.path,
                        value=op.value,
                        expected_version=op.expected_version
                    )
                    tx.vector_db.metadata_update(
                        filter=op.deprecate_filter,
                        patch={"is_obsolete": True}
                    )

                elif op.action == "DELETE":
                    tx.vector_db.metadata_update(
                        filter={"id": op.id},
                        patch={"is_obsolete": True}
                    )

            tx.commit()
```

### 7.3 Structured Extraction Schema

LLM 提取算子必须输出强约束 JSON。

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "array",
  "items": {
    "type": "object",
    "properties": {
      "op": { "type": "string", "enum": ["ADD", "UPDATE", "DELETE", "IGNORE"] },
      "entity": { "type": "string" },
      "path": { "type": "string" },
      "value": { "type": "string" },
      "reasoning": { "type": "string" }
    },
    "required": ["op", "entity", "value"]
  }
}
```

状态感知提取要求：

- 输入不仅包含 Current Turn，还包含 Existing Context Snapshots。
- 如果旧事实是 `$.profile.database = MySQL`，当前文本是“换成 OceanBase 了”，LLM 应输出 `UPDATE`。
- 如果是“把变量名改一下”，不应写入长期记忆。

### 7.4 后置校验与熔断

校验链路：

```text
LLM 输出 JSON
   -> 强类型反射/Schema 校验器
   -> 成功：进入事务写入
   -> 失败：本地修复/小模型重写一次
   -> 仍失败：Circuit Breaker
   -> Dead Letter Queue + Prometheus 指标
```

规则：

- 解析失败或枚举越界，禁止触碰数据库。
- 修复只允许一次，避免无限消耗 Token。
- DLQ 用于后续人工审计和模型/Prompt 回归分析。

## 8. 数据存储模型

### 8.1 KV/关系型数据库

用途：

- 保存强事实、用户画像、纠错本、版本号、Active/Historical 状态。
- 提供毫秒级读取和精确覆盖。
- 作为冲突解决中的 Single Source of Truth。

Profile 表建议：

```sql
CREATE TABLE user_profile (
  user_id VARCHAR(128) PRIMARY KEY,
  profile_json JSON NOT NULL,
  version BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

CAS 更新：

```sql
UPDATE user_profile
SET profile_json = JSON_SET(profile_json, '$.technical_stack', 'Go'),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE user_id = '9527'
  AND version = 42;
```

如果影响行数为 0，说明版本冲突，触发指数退避重试并重新读取快照。

### 8.2 向量数据库

用途：

- 保存非结构化记忆碎片和业务文档语义增强文本。
- HNSW/ANN 近似检索，复杂度从全表扫描的 $O(N)$ 降为近似 $O(\log N)$。
- 在海量业务文档或多租户大规模场景中，可引入 IVF-PQ（倒排文件 + 乘积量化）降低内存占用，以召回精度换取更低成本。
- 与标量元数据联合过滤，保证读时对齐。

Episodic Memory 示例：

```json
{
  "fact_id": "fact_001",
  "vector_id": "v_88321",
  "content": "用户发起了 Pulsar Improvement Proposal PIP-459，并负责邮件组讨论。",
  "category": "project_progress",
  "timestamp": 1774944000,
  "weight": 0.85,
  "last_referenced": 1775021200,
  "is_obsolete": false,
  "generation_id": "tech_stack_gen_2"
}
```

检索期硬过滤：

```sql
SEARCH Vector FROM user_memory
WHERE user_id = '9527'
  AND tenant_id = 'tenant_a'
  AND permission_scope CONTAINS 'memory:read'
  AND is_obsolete == false
  AND generation_id == CURRENT_KV_GEN;
```

### 8.3 BM25/倒排索引

用途：

- API 名、字段名、规则编号、参数类型、大小写敏感符号。
- 业务文档中的专有名词和精确条款。
- 与向量召回结果做混合排序。

API 文档不适合纯向量存储，例如 `getUserInfo(String userId, int type)` 对大小写、参数类型和下划线极其敏感，必须使用 BM25 或结构化关系来保证精确性。

### 8.4 图数据库或轻量关系图

图数据库适合表示非线性关系：

节点：

- `Entity`：稳定概念、工具、技术栈，例如 `OceanBase`、`OMS`。
- `MemoryShard`：动态对话陈述或具体事件，例如“修改增量同步逻辑时遭遇多节点写入乱序”。

边：

- `(:MemoryShard)-[:DISCUSS]->(:Entity)`
- `(:Entity)-[:DEPENDS_ON]->(:Entity)`
- `(:MemoryShard)-[:CAUSED_BY]->(:MemoryShard)`
- `(:MemoryShard)-[:SEMANTIC_LINK {score: 0.65}]->(:MemoryShard)`

文档同时指出，在大规模 ToC 场景中，每个用户独立运行重型图数据库成本过高。因此可采用折中方案：

```text
轻量 KV 槽位
   + 稠密语义向量
   + 模型内生参数图
```

也就是用外部确定性数据激活模型预训练参数中的隐式语义网络。

## 9. 写入管道工作机制

### 9.1 触发条件

写入管道异步触发，避免阻塞用户响应。

触发条件：

- 对话流结束。
- 用户连续一段时间无新消息，例如 5 分钟 Debounce。
- 用户点击新对话/Clear。
- 用户显式说“记一下”“保存配置”。
- 连续 3 轮讨论同一个非通用概念。
- 强纠错语义出现，如“不对”“不是 SQL Server，是 OceanBase”。

### 9.2 个人画像写入流程

```text
Session 文本
   -> Debounce
   -> MQ
   -> Sanitization
   -> Pre-Filter
   -> Semantic Classifier
   -> LLM Refiner
   -> Schema Validation
   -> Conflict Resolver
   -> KV/Profile Upsert
   -> Vector Shard Upsert
   -> Correction Ledger Upsert
```

LLM Refiner 输出示例：

```json
{
  "profile_updates": {
    "core_role": null,
    "infrastructure_stack": ["OceanBase"],
    "active_projects": [
      {
        "project_name": "pulsar-agent",
        "tech_keywords": ["内存管理"],
        "business_domain": "智能体/基础架构组件",
        "lifecycle": "ACTIVE"
      }
    ],
    "explicit_preferences": []
  },
  "correction_ledger": [
    {
      "entity": "Database",
      "incorrect_value": "SQL Server",
      "correct_value": "OceanBase",
      "reason": "用户显式指出生产环境使用的是 OceanBase 数据库，纠正了 AI 的语法生成假设"
    }
  ]
}
```

### 9.3 业务文档写入流程

```text
文档上传/同步
   -> Layout-Aware Parser
   -> Semantic Tree
   -> Chunking
   -> Domain/Type/State 打标
   -> LLM Semantic Enhancement
   -> Embedding
   -> Vector DB Upsert
   -> BM25 Index
   -> RDBMS 保存元数据、版本、来源、结构化断言
```

关键要求：

- 表格转 Markdown Table 或 JSON。
- 保留章节路径和来源位置。
- 每个 Chunk 必带 `doc_id`、`version`、`is_active`、`publish_date`。
- 新版本发布后旧版本 `is_active=false`，或检索时强制 `WHERE is_active = true`。

## 10. 读取管道工作机制

### 10.1 两阶段检索

读取管道需要在几十毫秒内完成上下文注入。

阶段一：多路并发召回。

```text
Route 1: KV/Cache
   -> 读取 User Profile、Correction Ledger，目标 < 2ms

Route 2: Vector ANN Search
   -> 当前问题 Embedding
   -> HNSW Top-K 召回，目标 5-15ms

Route 3: Time Sliding Window
   -> 最近 3 次对话短期上下文

Route 4: BM25 Sparse Search
   -> API 字段、规则编号、专有名词

Route 5: Graph Expansion
   -> 从命中实体扩展 1-2 跳关系
```

所有路由都必须携带 `user_id`、`tenant_id`、权限 Token 和当前 `Generation_ID`。这些字段不是应用层“建议过滤”，而应下推到存储层成为硬谓词，避免跨租户召回、权限越界和旧世代记忆穿透。

阶段二：重排、过滤、裁剪。

```text
Candidate Memories
   -> Metadata Filter
   -> Generation Filter
   -> Conflict Eviction
   -> Score Rerank / Cross-Encoder Rerank
   -> Token Budget Trim
   -> Context Weaver
```

当候选集较小但质量要求高时，可在规则打分和向量相似度之后增加 Cross-Encoder 重排。Cross-Encoder 直接读取“用户问题 + 候选记忆”成对输入，能比纯向量相似度更准确地判断候选是否真正回答当前问题，但成本更高，因此通常只作用于 Top-N 候选。

### 10.2 HNSW 检索机制

HNSW 类似多层跳表：

```text
[第 2 层: 稀疏地标]   大跨度跳跃
[第 1 层: 次级稀疏]   中等跨度逼近
[第 0 层: 全量向量]   近邻微调
```

流程：

1. 从顶层地标节点入场，快速跳过无关空间。
2. 向下一层进入更局部邻居网络。
3. 在第 0 层对少量候选做精确余弦相似度。
4. 对 `is_obsolete=true` 或世代不符的节点直接跳过。

### 10.3 Hybrid Retrieval + RRF

业务文档读取使用混合检索：

```text
用户业务问题
   -> 意图解析，提取 metadata filter，例如 domain="返利", state="Active"
   -> Dense Vector 检索 contextualized_text
   -> Sparse BM25 检索规则编号/API 字段/专有名词
   -> RRF 或加权融合
   -> Metadata Filter
   -> Rerank 模型重排
   -> Context Weaver 注入 LLM
```

RRF 可以采用常见形式：

$$
\text{RRF}(d)=\sum_{r \in R}\frac{1}{k+\text{rank}_r(d)}
$$

其中 `R` 是不同检索器结果列表集合，`k` 是平滑常数。RRF 能把 Dense 和 Sparse 的排序融合，降低单一检索器误召回风险。

### 10.4 图增强检索

Graph-RAG 用于解决纯向量无法理解非线性关系的问题。

示例：

```text
[用户问题] --向量检索--> [Pulsar PIP-459 碎片]
                                  │
                                  ▼ 两跳语义边
                         [分布式一致性]
                                  │
                                  ▼
                         [OceanBase GTS / 增量同步逻辑]
```

图算法：

**两跳路径隐式关联**：

- 新记忆 `M_new` 连接 `OceanBase GTS`。
- 本体发现 `OceanBase GTS -> 分布式一致性`。
- 旧记忆 `M_old` 也连接 `分布式一致性`。
- 自动建立弱边 `SEMANTIC_LINK {score: 0.65}`。

**PageRank/度中心性权重演进**：

$$
\text{Score}(Entity) = (1-d) + d \sum_{i \in \text{In-Neighbors}} \frac{\text{Score}(Neighbor_i)}{\text{Out-Degree}(Neighbor_i)}
$$

高入度实体如 OceanBase、Istio、Java 会成为 Anchor，衰减因子降低，GC 时被优先保留。

**Leiden/Louvain 社区发现**：

- 对 MemoryShard 按边权聚类。
- 对高密度社团调用 LLM 生成 Super Node。
- 将原外向边桥接到 Super Node。
- 剪掉弱边并回收孤儿碎片。

### 10.5 模型内生参数图

在大规模 ToC 场景中，如果不部署重型图数据库，可用模型内生参数图替代部分图遍历。

Attention 公式：

$$
\text{Attention}(Q,K,V)=\text{softmax}\left(\frac{QK^T}{\sqrt{d_k}}\right)V
$$

解释：

- `Q` 来自用户当前问题，例如“多节点写入乱序”。
- `K/V` 包含外部注入的 KV 画像和向量碎片，例如 “OceanBase”“OMS 增量逻辑”。
- Self-Attention 通过矩阵乘法激活模型内部对 OceanBase、GTS、Paxos、分布式一致性的预训练知识。

工程取舍：

- 外部图数据库：关系明确，但高并发图事务、拓扑修改和多跳遍历成本高。
- KV + Vector + Parametric Graph：低延迟、低成本，通过 Prompt 注入激活模型内部语义网络。

## 11. 冲突解决机制

冲突解决的核心难点是 KV 和 Vector 的物理特性不同：

- KV 强结构，覆盖更新便宜。
- 向量库基于 HNSW，物理删除和重建索引成本高。

系统使用：

```text
KV 原子覆盖
   + Tombstone 软删除
   + Generation ID 世代失效
   + Filter-during-search
   + ContextSynthesizer 熔断重排
   + Async GC
```

### 11.1 冲突检测

当用户说“不再用 Java，换成 Go”时，LLM 提取算子输出：

```json
{
  "op": "UPDATE",
  "target": "tech_stack",
  "old_value": "Java",
  "new_value": "Go",
  "conflict_detected": true
}
```

### 11.2 KV 锚点覆盖

KV 是事实源。流程引擎使用 CAS 更新：

```sql
UPDATE user_profile
SET tech_stack = 'Go',
    version = version + 1
WHERE user_id = '9527'
  AND version = 42;
```

成功后，新画像立即生效。旧技术栈可保留为 historical，也可从 active slot 移除。

### 11.3 向量软擦除

不直接删除 HNSW 节点，而是更新元数据：

```json
{
  "is_obsolete": true,
  "obsolete_reason": "tech_stack updated from Java to Go",
  "generation_id": "tech_stack_gen_1"
}
```

新 Go 记忆写入 `tech_stack_gen_2`。

### 11.4 读时强过滤

检索必须带上：

```sql
SEARCH Vector FROM user_memory
WHERE user_id = '9527'
  AND is_obsolete == false
  AND generation_id == CURRENT_KV_GEN;
```

这样旧 Java 向量物理上存在，但对读取管道不可见。

### 11.5 ContextSynthesizer 熔断重排

如果向量检索漏出旧片段，ContextSynthesizer 使用最新 KV 再做最后扫描：

- 当前 KV：`tech_stack = Go`。
- 候选片段：大量讨论 Java Spring Cloud。
- 判断为不可调和冲突。
- 执行 Eviction，禁止注入 Prompt。

## 12. 记忆生命周期管理

生命周期包含产生、活跃、压缩、衰减、废弃、回收。

```text
NEW
 -> ACTIVE
 -> REFERENCED / WEIGHT_UP
 -> COMPACTED
 -> HISTORICAL / DEPRECATED
 -> OBSOLETE
 -> COLD_STORAGE / PHYSICAL_DELETE
```

### 12.1 时序优先与显式覆盖

当新事实与旧事实冲突：

- 用户显式纠正优先于模型推断。
- 新时间戳优先于旧时间戳。
- 旧事实不一定立刻删除，可以标记 `historical`。
- 新事实标记 `active`。

例如：

```json
{
  "project": "A",
  "status": "historical",
  "valid_until": "2026-05-20"
}
```

```json
{
  "project": "seahorse-agent",
  "status": "active",
  "valid_from": "2026-05-20"
}
```

### 12.2 LSM 式离线压缩整理

离线压缩在低峰期运行，将零散碎片合并为系统事实。

示例：

```text
碎屑 1：我们核心库换成 OceanBase 了。
碎屑 2：在改 OceanBase 的增量同步逻辑。
碎屑 3：OMS 抽增量日志遇到多节点乱序问题。

压缩后：
{
  "fact": "正在基于 OceanBase 解决多节点写入乱序的增量同步逻辑开发（涉及 OMS 架构）",
  "confidence": 0.95
}
```

压缩后动作：

- 写入新的 Compacted Fact。
- 旧碎屑标记 deprecated。
- 根据策略物理删除或转冷存储。

### 12.3 实体对齐与别名归一

同一实体可能有多个叫法：

```text
"OceanBase"
"OB 数据库"
"阿里的分布式数据库"
```

离线引擎通过图聚类/Embedding 聚类归一为标准实体：

```json
{
  "canonical_entity_id": "entity_oceanbase",
  "canonical_name": "OceanBase",
  "aliases": ["OB", "阿里的分布式数据库"]
}
```

### 12.4 半衰期衰减

记忆权重随时间衰减：

$$
W(t)=W_0 \cdot e^{-\lambda \Delta t}
$$

含义：

- `W0`：初始重要性，由提取模型根据业务核心度评估。
- `Delta t`：距离产生或上次唤醒的时间差。
- `lambda`：衰减因子。

不同记忆不同衰减：

| 记忆类型 | lambda |
| --- | --- |
| 职业、核心技术栈、长期偏好 | 接近 0 |
| 活跃项目 | 中等，随互动频率调整 |
| 一次性行程、临时任务 | 较大 |
| Debug 日志、短期报错 | 极大或不入长期库 |

### 12.5 垃圾回收（GC）触发

当 $W(t) < 0.1$ 或达到 TTL：

1. 从热向量池移除。
2. 转冷存储或物理销毁。
3. 清理 HNSW 索引空间。
4. 清理图中的弱边、孤儿节点。
5. 对业务文档按版本状态清理 inactive chunk。

异步 GC：

```text
常驻 GC 进程
   -> 扫描 is_obsolete == true / is_deprecated == true / expired TTL
   -> 提取 vector_id
   -> 批量移出 HNSW 拓扑
   -> 触发局部索引重建
   -> 更新审计日志
```

## 13. 持久化一致性与并发控制

### 13.1 MVCC

每个用户 Profile 带 `version` 字段，所有写入基于最新快照。

并发场景：

- Worker A 和 Worker B 同时提取到技术栈变化。
- A 先 CAS 成功，version 从 42 到 43。
- B 的 `WHERE version = 42` 失败。
- B 重新读取 version 43，重新做语义合并或放弃。

### 13.2 幂等写入

每个记忆操作带 `operation_id` 或事件 ID。

```sql
CREATE TABLE memory_operation_log (
  operation_id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128),
  op_type VARCHAR(32),
  status VARCHAR(32),
  created_at TIMESTAMP
);
```

Worker 重试时先查 operation log，避免重复写入。

### 13.3 双写一致性

KV、向量库、BM25、图数据库很难做强分布式事务。推荐策略：

- KV Profile 是事实源。
- 向量和索引是派生视图，可最终一致。
- 写操作先落 KV 版本，再异步更新派生索引。
- 派生索引用 `profile_version` 或 `generation_id` 对齐 KV。
- 读时必须检查版本或世代，防止读到旧派生数据。

### 13.4 Outbox 模式

KV 写成功后写 Outbox，后台消费者更新向量/BM25/图。

```sql
CREATE TABLE memory_outbox (
  event_id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128),
  event_type VARCHAR(64),
  payload JSON,
  profile_version BIGINT,
  status VARCHAR(32),
  created_at TIMESTAMP
);
```

## 14. 安全与隐私边界

安全边界由确定性流程控制，不依赖模型自觉。

必须强过滤或拒绝主动记录：

- 密码、验证码、API Secret、Session Key。
- 银行卡号、身份证明号码。
- 高敏健康隐私。
- 未经授权的第三方隐私。

安全策略：

- 进入 LLM 前先脱敏。
- 存储前做 Schema 和敏感字段二次扫描。
- 用户级沙箱隔离，`user_id` 必须是所有存储查询的硬过滤字段。
- 业务文档按租户、权限、版本隔离。
- 检索时必须带租户/用户/权限谓词。

## 15. 端到端示例

### 15.1 用户纠错与技术栈更新

输入：

```text
别用 Python 方案了，我最近换成用 Go 写后端了。
```

写入过程：

```text
Sanitization：无敏感信息
Classifier：Professional_Skill + Perennial
LLM Extractor：
  UPDATE $.profile.technical_stack Python -> Go
Conflict Resolver：
  KV CAS 覆盖
  generation_id 从 tech_stack_gen_1 -> tech_stack_gen_2
  Java/Python 相关旧片段按范围 Tombstone
Persistence：
  KV active=Go
  Vector old shards is_obsolete=true
```

读取过程：

```text
用户问：高并发模块怎么写？
-> KV 读取 tech_stack=Go
-> Vector Search 带 generation_id=CURRENT_KV_GEN
-> 旧 Python/Java 片段不可见
-> ContextSynthesizer 再剔除冲突片段
-> Prompt 注入 Go 后端上下文
```

### 15.2 业务文档规则召回

业务规则：

```text
季度末返利核销对账中，若累计差异金额 > 5000 元，付款流转将自动进入冻结状态，必须由事业部财务一号位审批方可解冻。
```

用户输入：

```text
这批数据尾差累计到 5200 块了，在系统里直接改报错了，帮我看看。
```

检索：

```text
Router：业务问题 + 用户技术上下文
Profile Track：Java, OceanBase, ERP
Business Doc Track：
  metadata filter domain=Rebate, state=Active
  vector 召回“尾差/冻结”
  BM25 命中“5000”
Context Weaver：
  分区注入用户画像和业务规则
LLM：
  解释 5200 > 5000 触发冻结，并建议检查审批流状态与相关服务节点
```

## 16. 推荐落地模块划分

```text
memory-core
   ├── router
   │   └── MemoryRouter
   ├── ingestion
   │   ├── MemoryIngestionWorkflow
   │   ├── Sanitizer
   │   ├── PreFilter
   │   ├── SemanticClassifier
   │   ├── LlmRefiner
   │   └── SchemaValidator
   ├── retrieval
   │   ├── RetrievalPipeline
   │   ├── ProfileRetriever
   │   ├── VectorRetriever
   │   ├── SparseRetriever
   │   ├── GraphExpander
   │   ├── Reranker
   │   └── ContextWeaver
   ├── storage
   │   ├── ProfileRepository
   │   ├── CorrectionLedgerRepository
   │   ├── VectorMemoryRepository
   │   ├── BusinessDocRepository
   │   └── GraphRepository
   ├── lifecycle
   │   ├── ConflictResolver
   │   ├── CompactionJob
   │   ├── DecayScorer
   │   └── GarbageCollector
   └── observability
       ├── Metrics
       ├── AuditLog
       └── DeadLetterInspector
```

## 17. 关键验收标准

一个可用的 Gemini Agent 记忆系统应满足：

1. **低延迟**：读取管道中的 Profile 读取约 1-2ms，ANN 检索约 5-15ms，整体记忆注入控制在几十毫秒级。
2. **高准确**：强事实不靠向量 Top-K 决定，使用 KV/纠错本/元数据过滤兜底。
3. **可解释**：每条长期记忆有来源、时间戳、置信度、生命周期状态。
4. **可更新**：用户纠错能立即覆盖旧事实，并在读时不可见旧冲突片段。
5. **可回收**：低价值记忆按半衰期衰减，离线压缩和 GC 控制存储体量。
6. **可溯源**：业务文档回答必须能追溯到 doc_id、version、章节和原文片段。
7. **可隔离**：用户、租户、权限、敏感信息必须由确定性代码隔离。
8. **可观测**：DLQ、Schema 失败率、冲突更新率、召回命中率、Prompt Token 消耗、GC 数量都应有指标。

## 18. 总结

这套记忆系统的本质是“确定性工程管道 + 概率语义模型”的协作：

- 流程引擎负责稳定性、安全性、并发、事务和生命周期。
- LLM 负责语义理解、事实抽取、文档增强和冲突意图识别。
- KV 负责强事实骨架。
- 向量库负责事件碎片和语义召回。
- BM25 负责精确术语、API 和规则编号。
- 图数据库或模型内生参数图负责非线性关系联想。
- Correction Ledger 负责用户显式纠错的最高优先级约束。

最终形成的不是一个“无限保存聊天记录”的系统，而是一个会筛选、会提炼、会联想、会覆盖、会遗忘的工程化 Agent 记忆体。
