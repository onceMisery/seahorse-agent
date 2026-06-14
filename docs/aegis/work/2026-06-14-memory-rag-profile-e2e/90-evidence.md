# Evidence

## Scope

- Goal: verify and repair the local Docker memory, RAG, and user-profile closed loop, including the expired-login symptom on chat.
- Environment: local full Docker deployment through `http://localhost` and backend container `seahorse-backend`.
- Data policy: preserved existing Postgres, Milvus, and Redis volumes.

## Fix Summary

- Frontend chat SSE now sends `Authorization: Bearer <token>` for the manual fetch stream.
- Memory capture now runs before the frontend finish event and explicit memory turns flush immediately.
- Profile slot resolution scans metadata and content slots; profile normalization splits multi-slot Chinese facts and strips instruction text.
- Memory routing recognizes Chinese long-term memory/profile recall questions, so stored profile facts are loaded back into the answer path.
- RAG retrieval options carry the configured embedding model through context creation and multi-channel execution.
- JDBC knowledge-base discovery filters to searchable, non-deleted, chunk-enabled knowledge bases matching the current embedding model.
- Vector global search embeds once, limits global collection scan, skips failed collections, and reports search metadata.
- Retrieval trace nodes now persist `SearchChannelResult.metadata` into `extra_data`.

## Automated Verification

- Focused trace metadata TDD: `KernelRetrievalEngineTests#shouldRecordChannelResultMetadataInTraceExtraData` passed after the production metadata pass-through change.
- Backend affected suite:
  - Command: `.\\mvnw.cmd -B -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=MemoryTurnCaptureStageTests,MemoryAggregationServiceTests,ProfileSlotResolverTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,VectorGlobalSearchFeatureTests,KernelRetrievalEngineTests,JdbcKnowledgeBaseQueryAdapterTests,SeahorseAgentKernelRetrievalAutoConfigurationTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: 111 tests, 0 failures, build success.
- Frontend SSE auth suite:
  - Command: `npm.cmd test -- src/stores/chatStore.test.ts`
  - Result: 9 tests, 0 failures.

## Docker Refresh

- Package command: `.\\mvnw.cmd -B -pl seahorse-agent-bootstrap -am "-DskipTests" package`
- Image command: `docker compose -f docker-compose.full.yml build backend`
- Restart command: `docker compose -f docker-compose.full.yml up -d --no-deps backend`
- Health command: `docker inspect --format='{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' seahorse-backend`
- Health result: `running healthy`.

## E2E Evidence

### Auth And SSE

- Login endpoint: `POST http://localhost/api/auth/login` with `admin/admin123`.
- SSE endpoint: `GET http://localhost/api/rag/v3/chat`.
- Probe question: `请只回复 ok`.
- Result: HTTP 200, content type `text/event-stream;charset=UTF-8`, events `meta,message,finish,done`, answer `ok`.
- Expired-login check: no `登录已过期`, `未登录`, `Unauthorized`, or `401` text in the stream.

### Memory And Profile

- Suffix: `20260614071920`.
- Write prompt facts:
  - `identity.occupation=闭环验证平台可靠性工程师-20260614071920`
  - `preferences.response_style=三句内中文回答-20260614071920`
- Write answer: `已记住`.
- Recall answer: `identity.occupation=闭环验证平台可靠性工程师-20260614071920;preferences.response_style=三句内中文回答-20260614071920`.
- Postgres evidence:
  - `92668751|identity.occupation|闭环验证平台可靠性工程师-20260614071920|ACTIVE|access_count=1`
  - `92668751|preferences.response_style|三句内中文回答-20260614071920|ACTIVE|access_count=1`

### RAG

- Question: `根据知识库内容，Seahorse Agent 使用哪个向量库和哪个向量化模型？请只回答向量库和模型名。`
- Answer: `Milvus, nomic-embed-text`.
- Trace id: `324448193208250368`.
- Search node: `search-channel:VectorGlobalSearch`.
- Hit collection: `rag_final_v2`.
- Hit preview: `Seahorse Agent 是基于六边形架构的 RAG 智能体平台，使用 Milvus 向量库和 nomic-embed-text 向量化模型。`
- Trace metadata now present:
  - `embeddingModel=nomic-embed-text`
  - `collectionCount=2`
  - `searchableCollectionCount=2`
  - `failedCollectionCount=1`

## Residual Risk

- Full repository tests were not run; verification targeted the memory/RAG/auth surface touched by this task.
- The local database still has an old missing Milvus collection reference. This no longer breaks RAG because failed collections are skipped and counted in trace metadata.
