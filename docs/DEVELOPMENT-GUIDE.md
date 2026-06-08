# Seahorse Agent 入门与开发指导

> 面向新手的完整学习指南，帮助你从零开始理解和开发 Seahorse Agent 项目。

---

## 目录

1. [项目整体架构概述](#1-项目整体架构概述)
2. [开发环境搭建](#2-开发环境搭建)
3. [项目启动流程](#3-项目启动流程)
4. [核心功能模块分析](#4-核心功能模块分析)
5. [代码结构导航](#5-代码结构导航)
6. [开发流程指南](#6-开发流程指南)
7. [调试和测试方法](#7-调试和测试方法)
8. [常见问题排查](#8-常见问题排查)
9. [贡献指南](#9-贡献指南)

---

## 1. 项目整体架构概述

### 1.1 项目定位

Seahorse Agent 是一个面向企业知识问答与智能体应用的 **Agent 工程平台**，核心能力是 **RAG（检索增强生成）闭环**。它不是一个简单的 ChatBot，而是一个完整的工程化 AI 应用平台。

### 1.2 设计哲学：微内核 + 端口适配器

项目采用 **六边形架构（Hexagonal Architecture）**，核心思想是：

```
┌─────────────────────────────────────────────────────────┐
│                    外部世界（HTTP/前端/CLI）                  │
└───────────────────────┬─────────────────────────────────┘
                        │ 入站
        ┌───────────────▼───────────────┐
        │     Web 适配器（Controller）     │  ← 协议转换层
        └───────────────┬───────────────┘
                        │ 入站端口
    ┌───────────────────▼───────────────────────┐
    │            seahorse-agent-kernel          │  ← 微内核
    │  ┌─────────┐ ┌────────┐ ┌──────────────┐  │
    │  │领域模型  │ │应用服务 │ │ 出站端口接口  │  │
    │  │Domain   │ │Service │ │ Port(接口)   │  │
    │  └─────────┘ └────────┘ └──────┬───────┘  │
    └────────────────────────────────┼──────────┘
                                     │ 出站端口
        ┌────────────────────────────▼────────────────────┐
        │              可插拔适配器（Adapter）                │
        │  AI模型 │ 向量库 │ 缓存 │ MQ │ 存储 │ 解析 │ ...   │
        └─────────────────────────────────────────────────┘
```

**关键原则：**
- **内核（kernel）** 只包含稳定的业务逻辑，不依赖任何外部实现
- **端口（Port）** 是接口契约，定义在内核中
- **适配器（Adapter）** 是端口的具体实现，可以热插拔替换

### 1.3 Maven 多模块结构

```
seahorse-agent/                          # 根 POM（聚合 + 依赖管理）
├── seahorse-agent-kernel/               # 🔴 微内核（L2）：领域模型、端口、应用服务
├── seahorse-agent-adapter-web/          # 🟢 Web 入站适配器（REST/SSE/鉴权）
├── seahorse-agent-adapter-ai-openai-compatible/  # AI 模型适配器
├── seahorse-agent-adapter-vector-milvus/         # Milvus 向量库
├── seahorse-agent-adapter-vector-pgvector/       # pgvector 向量库
├── seahorse-agent-adapter-vector-noop/           # 空向量实现（开发用）
├── seahorse-agent-adapter-parser-tika/           # Tika 文档解析
├── seahorse-agent-adapter-repository-jdbc/       # JDBC 数据持久化
├── seahorse-agent-adapter-cache-local/           # 本地缓存
├── seahorse-agent-adapter-cache-redis/           # Redis 缓存
├── seahorse-agent-adapter-mq-direct/             # 进程内消息队列
├── seahorse-agent-adapter-mq-pulsar/             # Pulsar 消息队列
├── seahorse-agent-adapter-storage-local/         # 本地文件存储
├── seahorse-agent-adapter-storage-s3/            # S3 对象存储
├── seahorse-agent-adapter-search-elasticsearch/  # Elasticsearch 搜索
├── seahorse-agent-adapter-search-lucene/         # Lucene 嵌入式搜索
├── seahorse-agent-adapter-observation-noop/      # 空可观测实现
├── seahorse-agent-adapter-observation-micrometer/# Micrometer 可观测
├── seahorse-agent-adapter-mcp-http/              # MCP HTTP 工具
├── seahorse-agent-adapter-openapi/               # OpenAPI 连接器
├── seahorse-agent-adapter-source-feishu/         # 飞书数据源
├── seahorse-agent-mcp-server/                    # MCP Server
├── seahorse-agent-spring-boot-autoconfigure/     # Spring Boot 自动配置实现
├── seahorse-agent-spring-boot-starter-core/      # 核心轻量 starter 坐标
├── seahorse-agent-spring-boot-starter/           # 旧 starter 兼容别名
├── seahorse-agent-spring-boot-starter-all/       # 自动装配（全量适配器）
├── seahorse-agent-bootstrap/                     # 🔵 Spring Boot 启动入口
├── seahorse-agent-tests/                         # 集成测试
└── frontend/                                     # React 前端
```

**模块依赖关系：**
```
bootstrap → starter-core → autoconfigure → kernel
starter → starter-core → autoconfigure → kernel
starter-all → starter-core → autoconfigure → kernel
starter-all → 各重型 adapter 模块
adapter-web → kernel（入站适配器直接依赖内核端口）
```

当前 `starter-core` 已依赖内部 `seahorse-agent-spring-boot-autoconfigure` 模块，旧 `starter` 只作为指向 `starter-core` 的兼容别名。依赖边界已有回归检查：`starter-core` 和 `bootstrap` 使用 Maven Enforcer 防止误引入全量 starter/重型 adapter，`starter-core` 使用 `SeahorseAgentStarterCoreContextTests` 验证 local/direct/noop/JDBC 默认能力，`starter-all` 使用 `SeahorseAgentStarterAllSmokeTests` 验证官方重型 adapter 类和相关自动配置候选可被全量 starter 发现。

### 1.4 前后端分离架构

| 层 | 技术栈 | 端口 |
|---|--------|------|
| 前端 | React 18 + TypeScript + Vite + TailwindCSS | 5173（开发）/ 80（生产） |
| 后端 | Spring Boot 3.5.7 + Java 17 | 9090 |
| 数据库 | PostgreSQL 16 + pgvector | 5432 |

前端通过 Vite 的 proxy 配置将 `/api` 请求代理到后端 `http://localhost:9090`，开发时无需处理跨域问题。

---

## 2. 开发环境搭建

### 2.1 必备工具

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| **JDK** | 17+ | 后端编译运行 |
| **Maven** | 3.8+ | 后端构建（项目自带 mvnw） |
| **Node.js** | 18+ | 前端构建 |
| **npm** | 9+ | 前端包管理 |
| **PostgreSQL** | 14+（推荐 16 + pgvector） | 数据库 |
| **Git** | 2.x | 版本控制 |
| **Docker + Docker Compose** | 最新版 | 可选，用于运行中间件 |

### 2.2 JDK 17 安装

**Windows：**
```powershell
# 推荐用 Scoop 安装
scoop install java/temurin17-jdk

# 或者手动下载安装 Eclipse Temurin 17
# https://adoptium.net/temurin/releases/?version=17
```

验证：
```powershell
java -version
# openjdk version "17.x.x"
```

### 2.3 Maven 配置

项目自带 Maven Wrapper（`mvnw.cmd`），无需单独安装 Maven。首次运行会自动下载指定版本。

如果需要单独安装：
```powershell
scoop install maven
```

**国内用户建议配置镜像**：在 `~/.m2/settings.xml` 中添加阿里云镜像：
```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### 2.4 Node.js 安装

```powershell
# 推荐用 nvm-windows 管理多版本
# https://github.com/coreybutler/nvm-windows/releases

nvm install 18
nvm use 18
```

验证：
```powershell
node -v   # v18.x.x
npm -v    # 9.x.x
```

### 2.5 数据库准备

**方式一：Docker 启动（推荐）**
```powershell
docker run -d --name seahorse-postgres `
  -p 5432:5432 `
  -e POSTGRES_DB=seahorse `
  -e POSTGRES_USER=seahorse `
  -e POSTGRES_PASSWORD=seahorse `
  pgvector/pgvector:pg16
```

**方式二：本地安装 PostgreSQL**
安装 PostgreSQL 14+，并确保安装 pgvector 扩展。创建数据库：
```sql
CREATE DATABASE seahorse;
CREATE USER seahorse WITH PASSWORD 'seahorse';
GRANT ALL PRIVILEGES ON DATABASE seahorse TO seahorse;
```

数据库初始化脚本位于：`resources/database/seahorse_init.sql`，Docker 方式会自动执行。

### 2.6 AI 模型服务准备

项目需要一个 **OpenAI 兼容** 的模型服务。可以选择：

| 选项 | 说明 |
|------|------|
| OpenAI API | 直接使用 OpenAI 官方 API |
| Ollama（本地） | 本地运行开源模型，完全免费 |
| vLLM | 高性能本地推理 |

**Ollama 本地部署示例：**
```powershell
# 安装 Ollama: https://ollama.com/download
ollama pull qwen2.5:7b          # 对话模型
ollama pull nomic-embed-text     # Embedding 模型
```

### 2.7 环境变量配置

复制 `.env.example` 为 `.env`，填入你的 AI 模型配置：
```properties
# AI 模型配置（必填）
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=http://localhost:11434/v1   # Ollama 地址
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=ollama                       # Ollama 不需要真实 key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=qwen2.5:7b
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=nomic-embed-text
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=                         # 可选
```

---

## 3. 项目启动流程

### 3.1 后端启动

#### 第一步：编译项目
```powershell
cd d:\code\seahorse-agent

# 完整编译（首次较慢，需下载依赖）
.\mvnw.cmd clean install -DskipTests -Dspotless.check.skip=true

# 或者只编译 bootstrap 模块及其依赖（更快）
.\mvnw.cmd -pl seahorse-agent-bootstrap -am clean package -DskipTests -Dspotless.check.skip=true
```

#### 第二步：配置运行参数

**IDEA 方式（推荐开发使用）：**

1. 打开 IDEA → File → Open → 选择项目根目录
2. 等待 Maven 依赖导入完成
3. 找到启动类：`seahorse-agent-bootstrap/src/main/java/com/miracle/ai/seahorse/agent/SeahorseAgentApplication.java`
4. 右键 → Run 或配置 Run Configuration：

在 VM Options 或 Environment Variables 中添加：
```
SERVER_PORT=9090
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/seahorse
SPRING_DATASOURCE_USERNAME=seahorse
SPRING_DATASOURCE_PASSWORD=seahorse
SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=noop
SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=local
SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct
SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=noop
SEAHORSE_AGENT_ADAPTERS_REPOSITORY_TYPE=jdbc
SEAHORSE_AGENT_ADAPTERS_AI_TYPE=openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=http://localhost:11434/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=ollama
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=qwen2.5:7b
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=nomic-embed-text
```

**命令行方式：**
```powershell
$env:SERVER_PORT="9090"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/seahorse"
$env:SPRING_DATASOURCE_USERNAME="seahorse"
$env:SPRING_DATASOURCE_PASSWORD="seahorse"
$env:SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE="noop"
$env:SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE="local"
$env:SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE="local"
$env:SEAHORSE_AGENT_ADAPTERS_MQ_TYPE="direct"
$env:SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE="noop"
$env:SEAHORSE_AGENT_ADAPTERS_REPOSITORY_TYPE="jdbc"
$env:SEAHORSE_AGENT_ADAPTERS_AI_TYPE="openai-compatible"
$env:SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL="http://localhost:11434/v1"
$env:SEAHORSE_AGENT_ADAPTERS_AI_API_KEY="ollama"
$env:SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL="qwen2.5:7b"
$env:SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL="nomic-embed-text"

.\mvnw.cmd -pl seahorse-agent-bootstrap -am spring-boot:run -Dspotless.check.skip=true
```

启动成功后会看到：
```
Started SeahorseAgentApplication in X.XXX seconds
```

后端默认运行在 `http://localhost:9090`。

### 3.2 前端启动

```powershell
cd d:\code\seahorse-agent\frontend

# 安装依赖（首次需要）
npm install

# 启动开发服务器
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`，Vite 会自动将 `/api` 请求代理到后端。

### 3.3 Docker Compose 一键启动（全栈）

如果不想手动搭建环境，可以用 Docker Compose：

```powershell
# 先配置 .env 文件（AI 模型 API 信息）
cp .env.example .env
# 编辑 .env 填入你的 API Key

# 启动基础服务（PostgreSQL + 后端 + 前端）
docker compose up -d --build

# 或者启动全量服务（包含 Redis、Milvus、Pulsar 等）
docker compose -f docker-compose.full.yml up -d --build
```

### 3.4 适配器选择策略（开发环境推荐）

| 适配器 | 开发环境推荐 | 说明 |
|--------|-------------|------|
| 向量库 | `noop` | 无需安装 Milvus，适合先跑通流程 |
| 缓存 | `local` | 进程内缓存，无需 Redis |
| 存储 | `local` | 本地文件系统 |
| 消息队列 | `direct` | 进程内队列 |
| 可观测 | `noop` | 无开销 |
| 数据库 | `jdbc` | 连接 PostgreSQL |

当你需要测试向量检索功能时，可以切换到 `pgvector`（复用已有的 PostgreSQL）。

---

## 4. 核心功能模块分析

### 4.1 内核模块（seahorse-agent-kernel）

内核是项目的"大脑"，包含所有业务逻辑。内部结构：

```
kernel/
├── application/          # 应用服务层（编排业务流程）
│   ├── chat/            # 对话服务（KernelChatPipeline）
│   ├── agent/           # Agent Loop（KernelAgentLoop）
│   ├── ingestion/       # 文档入库引擎（KernelIngestionEngine）
│   ├── retrieval/       # 检索引擎（KernelMultiChannelRetrievalEngine）
│   ├── knowledge/       # 知识库管理服务
│   ├── memory/          # 记忆系统（四层记忆架构）
│   ├── conversation/    # 会话管理
│   ├── intent/          # 意图解析
│   ├── metadata/        # 元数据治理
│   ├── trace/           # RAG Trace 追踪
│   └── ...
├── domain/              # 领域模型层（业务实体和规则）
│   ├── chat/            # 对话领域对象
│   ├── agent/           # Agent 领域对象
│   ├── ingestion/       # 入库领域对象
│   ├── retrieval/       # 检索领域对象
│   ├── memory/          # 记忆领域对象
│   └── ...
├── feature/             # 扩展点注册（Feature 机制）
├── plugin/              # 插件机制（PortWrapper 等）
├── model/               # 通用模型
├── config/              # 内核配置
└── support/             # 辅助工具
```

**核心服务说明：**

| 服务 | 类 | 职责 |
|------|-----|------|
| 对话管线 | `KernelChatPipeline` | RAG 主链路编排：记忆→改写→意图→检索→Prompt→流式回答 |
| Agent 循环 | `KernelAgentLoop` | function-calling 多轮工具调用 |
| 入库引擎 | `KernelIngestionEngine` | 文档解析→分块→Embedding→索引 |
| 检索引擎 | `KernelMultiChannelRetrievalEngine` | 多路检索→融合→重排 |
| 记忆引擎 | `DefaultMemoryEnginePort` | 短期/长期/语义三层记忆 |

### 4.2 端口（Ports）

端口是内核与外部世界的"契约"。分为两类：

**入站端口（Inbound Ports）** —— 外部调用内核的入口：
```
ports/inbound/
├── ChatInboundPort          # 对话入站
├── IngestionTaskInboundPort # 入库任务入站
├── KnowledgeBaseInboundPort # 知识库管理入站
└── ...                      # 共 18 个入站端口
```

**出站端口（Outbound Ports）** —— 内核调用外部的接口：
```
ports/outbound/
├── ChatModelPort            # AI 对话模型
├── StreamingChatModelPort   # 流式 AI 对话
├── EmbeddingModelPort       # Embedding 模型
├── VectorSearchPort         # 向量检索
├── DocumentParserPort       # 文档解析
├── ObjectStoragePort        # 对象存储
├── MessageQueuePort         # 消息队列
├── ObservationPort          # 可观测
├── CachePort                # 缓存
├── KnowledgeChunkRepositoryPort  # 知识分块持久化
└── ...                      # 共 31 个出站端口
```

### 4.3 Web 适配器（seahorse-agent-adapter-web）

Web 适配器是 HTTP 请求的入口，包含：

```
adapters/web/              # Web 控制器（122 个文件）
├── SeahorseChatController          # 对话接口（/rag/v3/chat）
├── KnowledgeBaseController         # 知识库管理
├── IngestionTaskController         # 入库任务
├── IngestionPipelineController     # 入库流水线
├── ConversationController          # 会话管理
├── IntentTreeController            # 意图树
├── RagTraceController              # RAG 追踪
├── UserController                  # 用户管理
├── AuthController                  # 认证登录
└── ...

adapters/local/            # 本地认证适配器等
```

**重要设计模式**：所有 Controller 使用 `ObjectProvider<T>` 懒加载端口依赖，避免启动时循环依赖。

### 4.4 其他适配器

每个适配器模块都实现一个或多个出站端口：

| 适配器模块 | 实现的端口 | 说明 |
|-----------|-----------|------|
| `adapter-ai-openai-compatible` | ChatModelPort, StreamingChatModelPort, EmbeddingModelPort, RerankModelPort | 通过 HTTP 调用 OpenAI 兼容 API |
| `adapter-vector-milvus` | VectorSearchPort, VectorIndexPort | Milvus HNSW 向量索引 |
| `adapter-vector-pgvector` | VectorSearchPort, VectorIndexPort | PostgreSQL pgvector |
| `adapter-parser-tika` | DocumentParserPort | Apache Tika 解析 PDF/Office/HTML |
| `adapter-repository-jdbc` | 各种 RepositoryPort | MyBatis Plus + JDBC |
| `adapter-cache-local` | CachePort | ConcurrentHashMap 本地缓存 |
| `adapter-cache-redis` | CachePort | Redisson + Redis |
| `adapter-mq-direct` | MessageQueuePort | 进程内队列（开发用） |
| `adapter-mq-pulsar` | MessageQueuePort | Apache Pulsar |
| `adapter-storage-local` | ObjectStoragePort | 本地文件系统 |
| `adapter-storage-s3` | ObjectStoragePort | AWS S3 / MinIO |

### 4.5 自动装配体系

项目通过 Spring Boot 的 `AutoConfiguration` 机制实现适配器的自动注册。装配分为 6 层（见 CLAUDE.md），确保依赖顺序正确。

**关键文件位置：**
- 自动配置类：`seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/`
- 自动配置注册：`seahorse-agent-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 默认配置属性：`seahorse-agent-spring-boot-autoconfigure/src/main/resources/application.properties`

---

## 5. 代码结构导航

### 5.1 后端关键文件速查

| 功能 | 文件路径 |
|------|---------|
| **启动类** | `seahorse-agent-bootstrap/.../SeahorseAgentApplication.java` |
| **启动配置** | `seahorse-agent-bootstrap/src/main/resources/application.properties` |
| **内核配置** | `seahorse-agent-spring-boot-autoconfigure/src/main/resources/application.properties` |
| **对话控制器** | `seahorse-agent-adapter-web/.../web/SeahorseChatController.java` |
| **对话管线** | `seahorse-agent-kernel/.../kernel/application/chat/KernelChatPipeline.java` |
| **Agent 循环** | `seahorse-agent-kernel/.../kernel/application/agent/KernelAgentLoop.java` |
| **入库引擎** | `seahorse-agent-kernel/.../kernel/application/ingestion/KernelIngestionEngine.java` |
| **检索引擎** | `seahorse-agent-kernel/.../kernel/application/retrieval/KernelMultiChannelRetrievalEngine.java` |
| **记忆系统** | `seahorse-agent-kernel/.../kernel/application/memory/` （55 个文件） |
| **OpenAI 适配器** | `seahorse-agent-adapter-ai-openai-compatible/.../OpenAiCompatibleModelAdapter.java` |
| **Milvus 适配器** | `seahorse-agent-adapter-vector-milvus/.../MilvusVectorAdapter.java` |
| **Tika 解析器** | `seahorse-agent-adapter-parser-tika/.../TikaDocumentParserAdapter.java` |
| **数据库初始化** | `resources/database/seahorse_init.sql` |

### 5.2 前端关键文件速查

```
frontend/src/
├── main.tsx              # 入口文件
├── App.tsx               # 根组件
├── router.tsx            # 路由配置
├── pages/
│   ├── ChatPage.tsx      # 对话页面
│   ├── LoginPage.tsx     # 登录页面
│   ├── MemoryCenterPage.tsx  # 记忆中心
│   └── admin/            # 管理后台页面（25 个页面）
│       ├── KnowledgeBasePage.tsx    # 知识库管理
│       ├── IngestionPipelinePage.tsx # 入库流水线
│       ├── RagTracePage.tsx         # RAG 追踪
│       ├── IntentTreePage.tsx       # 意图树
│       ├── UserManagementPage.tsx   # 用户管理
│       └── ...
├── components/           # 通用组件
├── services/             # API 服务层（36 个文件）
├── stores/               # Zustand 状态管理（10 个 store）
├── hooks/                # 自定义 Hooks
├── lib/                  # 工具库
├── types/                # TypeScript 类型定义
└── utils/                # 工具函数
```

### 5.3 请求全链路追踪

以一次对话请求为例，代码执行路径：

```
1. 前端：ChatPage.tsx → useStreamResponse hook → fetch SSE
2. Vite Proxy：/api → http://localhost:9090
3. Web 层：SeahorseChatController./rag/v3/chat
4. 入站端口：ChatInboundPort.streamChat()
5. 内核编排：KernelChatPipeline.execute()
   ├── loadMemory → ConversationMemoryPort
   ├── activateMemory → MemoryEnginePort
   ├── optimizeQuery → QueryOptimizerPort
   ├── rewriteQuery → QueryRewritePort
   ├── resolveIntents → IntentResolvePort
   ├── retrieve → KernelMultiChannelRetrievalEngine
   │   └── VectorSearchPort → Milvus/pgvector Adapter
   └── streamChat → StreamingChatModelPort → OpenAI Compatible Adapter
6. SSE 事件流回前端：meta → message → finish → done
```

---

## 6. 开发流程指南

### 6.1 标准开发流程

```
1. 需求分析 → 理解要修改的模块和端口
2. 创建分支 → git checkout -b feature/xxx
3. 修改端口 → 如果需要新的外部能力，先在 kernel 中定义端口接口
4. 实现适配器 → 在对应 adapter 模块中实现端口
5. 编写内核逻辑 → 在 kernel 的应用服务中使用端口
6. 添加控制器 → 在 adapter-web 中暴露 API（如需要）
7. 前端开发 → 在 frontend 中实现页面功能
8. 测试验证 → 单元测试 + 集成测试 + 手动联调
9. 代码格式化 → spotless 会自动在编译时格式化
10. 提交代码 → git commit（有意义的 commit message）
```

### 6.2 添加新适配器的步骤

假设你要添加一个新的缓存适配器（比如 Memcached）：

**第 1 步：理解端口契约**
```java
// 查看内核中的缓存端口定义
// seahorse-agent-kernel/.../ports/outbound/CachePort.java
public interface CachePort {
    void put(String key, Object value, Duration ttl);
    <T> T get(String key, Class<T> type);
    boolean evict(String key);
    // ...
}
```

**第 2 步：创建适配器模块**
```
seahorse-agent-adapter-cache-memcached/
├── pom.xml
└── src/main/java/com/miracle/ai/seahorse/agent/adapters/cache/memcached/
    ├── MemcachedCacheAdapter.java           # 实现 CachePort
    └── MemcachedCacheAutoConfiguration.java # Spring Boot 自动配置
```

**第 3 步：编写 pom.xml**
参考现有适配器（如 `adapter-cache-redis`）的 pom.xml，依赖 kernel 端口接口。

**第 4 步：注册自动配置**
在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册配置类。

**第 5 步：添加到 starter-all**（可选）
在 `seahorse-agent-spring-boot-starter-all/pom.xml` 中添加依赖。若新增的是官方重型 adapter，还需要同步更新 `SeahorseAgentStarterAllSmokeTests`，确保全量 starter 的 classpath 和自动配置候选检查继续覆盖它。

### 6.3 修改内核逻辑的注意事项

- **永远不要在内核中直接依赖适配器模块**。只能通过端口接口（Port）访问外部能力。
- 新增端口时，同时提供 noop 实现，保证其他开发者可以用最小配置运行。
- 修改已有端口接口时，需要检查所有实现该端口的适配器。

### 6.4 前端开发流程

```powershell
cd frontend

# 开发模式（热重载）
npm run dev

# 代码检查
npm run lint

# 代码格式化
npm run format

# 构建生产包
npm run build
```

前端 API 服务层在 `src/services/` 目录，每个后端模块对应一个 service 文件。

---

## 7. 调试和测试方法

### 7.1 后端单元测试

项目使用 **JUnit 5 + Mockito** 进行单元测试。

```powershell
# 运行所有单元测试（排除 integration 组）
.\mvnw.cmd test

# 运行特定模块的测试
.\mvnw.cmd -pl seahorse-agent-kernel test

# 跳过测试
.\mvnw.cmd package -DskipTests
```

**测试配置文件：**
- `maven-surefire-plugin` 配置在根 `pom.xml` 中
- 默认排除 `integration` 分组的测试
- Mockito 作为 Java Agent 加载

**编写单元测试示例：**
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private VectorSearchPort vectorSearchPort;

    @InjectMocks
    private MyService myService;

    @Test
    void shouldReturnResults() {
        when(vectorSearchPort.search(any())).thenReturn(List.of(...));
        var result = myService.doSomething();
        assertNotNull(result);
    }
}
```

### 7.2 集成测试

集成测试位于 `seahorse-agent-tests` 模块，使用 `@Tag("integration")` 标记。

```powershell
# 运行集成测试（需要数据库等外部依赖）
.\mvnw.cmd -pl seahorse-agent-tests verify -Dgroups=integration
```

### 7.3 前端测试

```powershell
cd frontend

# 运行测试
npm run test

# 监视模式
npm run test:watch
```

前端使用 **Vitest + Testing Library** 进行测试。

### 7.4 前后端联调

1. 确保后端运行在 `localhost:9090`
2. 确保前端运行在 `localhost:5173`
3. Vite 自动代理 `/api` → `http://localhost:9090`

**调试技巧：**
- 后端：在 IDEA 中设置断点，使用 Debug 模式启动
- 前端：使用 Chrome DevTools → Network 面板查看请求
- SSE 流式对话：Chrome DevTools → Network → EventStream 标签

### 7.5 日志调试

在 `application.properties` 中调整日志级别：
```properties
# 查看内核编排日志
logging.level.com.miracle.ai.seahorse.agent.kernel=DEBUG

# 查看 SQL 日志
logging.level.org.springframework.jdbc=DEBUG

# 查看特定适配器日志
logging.level.com.miracle.ai.seahorse.agent.adapters=DEBUG
```

---

## 8. 常见问题排查

### 8.1 编译问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `Could not resolve dependencies` | Maven 仓库网络问题 | 配置阿里云镜像或使用 VPN |
| `java: error: release version 17 not supported` | JDK 版本不对 | 确保 `JAVA_HOME` 指向 JDK 17 |
| Spotless 格式化报错 | 代码不符合格式规范 | 运行 `.\mvnw.cmd spotless:apply` |
| `${maven.multiModuleProjectDirectory}` 爆红 | IDEA 正常现象 | 忽略，不影响编译运行 |

### 8.2 启动问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `Connection refused: postgres` | 数据库未启动 | 先启动 PostgreSQL（Docker 或本地） |
| `SEAHORSE_AGENT_ADAPTERS_AI_API_KEY` 为空 | 未配置 AI 模型 | 在环境变量或 `.env` 中配置 API Key |
| `Port 9090 already in use` | 端口被占用 | 修改 `SERVER_PORT` 或关闭占用端口的进程 |
| Bean 创建失败 / 循环依赖 | 配置顺序问题 | 检查自动配置类的 `@AutoConfigureAfter` 注解 |
| Redisson 相关错误 | 不需要 Redis 时未排除 | 启动类已排除 `RedissonAutoConfigurationV2`，确保使用 `local` 缓存 |

### 8.3 运行时问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 对话返回空 | 向量库为空/noop | 先上传文档到知识库，或切换到 pgvector |
| Embedding 失败 | 模型不支持 | 确认 Embedding 模型名称正确 |
| 前端请求 404 | 代理未生效 | 确保前端通过 `localhost:5173` 访问，而非直接访问 9090 |
| SSE 连接中断 | 超时或网络问题 | 前端有指数退避重试机制，检查后端日志 |

### 8.4 前端问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `npm install` 失败 | Node.js 版本或网络问题 | 使用 Node 18+，配置 npm 镜像 |
| TypeScript 类型错误 | 类型定义不匹配 | 运行 `npm run lint` 检查 |
| 页面空白 | 后端未启动 | 确保后端在 9090 端口运行 |

---

## 9. 贡献指南

### 9.1 代码规范

- **Java 代码格式化**：项目使用 [Spotless](https://github.com/diffplug/spotless) 自动格式化，编译时自动执行
- **License Header**：所有 Java 文件必须包含 Apache 2.0 License 头（Spotless 自动添加）
- **命名规范**：遵循 Java 标准命名规范，端口以 `Port` 结尾，适配器以 `Adapter` 结尾
- **Lombok**：项目使用 Lombok 减少样板代码

### 9.2 架构原则

1. **内核纯净性**：`seahorse-agent-kernel` 不依赖任何 adapter 模块
2. **端口优先**：所有外部能力通过端口接口访问
3. **Controller 懒加载**：使用 `ObjectProvider<T>` 注入端口
4. **适配器独立性**：每个适配器模块可以独立引入/移除
5. **Feature 扩展**：检索通道、入库节点等通过 Feature 机制扩展

### 9.3 提交规范

建议遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat: 添加 Memcached 缓存适配器
fix: 修复对话管线中记忆加载顺序问题
refactor: 重构入库引擎节点注册机制
docs: 更新部署文档
test: 添加向量检索适配器单元测试
chore: 升级 Spring Boot 到 3.5.8
```

### 9.4 Pull Request 流程

1. Fork 项目到自己的 GitHub
2. 创建功能分支：`git checkout -b feature/my-feature`
3. 编写代码并添加测试
4. 确保所有测试通过：`.\mvnw.cmd test`
5. 确保代码格式化：`.\mvnw.cmd spotless:apply`
6. 提交代码并推送到 Fork
7. 创建 Pull Request，描述修改内容和动机

### 9.5 开发检查清单

在提交代码前，确认：

- [ ] 代码编译通过（`mvn clean compile`）
- [ ] 单元测试通过（`mvn test`）
- [ ] Spotless 格式化通过（`mvn spotless:check`）
- [ ] 没有引入内核到适配器的反向依赖
- [ ] 新增端口提供了 noop 实现
- [ ] 新增了必要的测试用例
- [ ] Commit message 清晰有意义

---

## 附录：快速命令参考

```powershell
# === 后端 ===
.\mvnw.cmd clean install -DskipTests -Dspotless.check.skip=true  # 完整编译
.\mvnw.cmd -pl seahorse-agent-bootstrap -am spring-boot:run       # 启动后端
.\mvnw.cmd test                                                    # 运行测试
.\mvnw.cmd spotless:apply                                          # 格式化代码

# === 前端 ===
cd frontend
npm install           # 安装依赖
npm run dev           # 启动开发服务器
npm run build         # 构建生产包
npm run lint          # 代码检查
npm run test          # 运行测试

# === Docker ===
docker compose up -d --build                                      # 基础服务
docker compose -f docker-compose.full.yml up -d --build            # 全量服务
docker compose down                                                # 停止服务
docker compose down -v                                             # 停止并清除数据
```
