# Checkpoint

## TodoCheckpointDraft

- Current todo: summarize evidence and hand off final status.
- Completed todos: fixed frontend SSE auth, memory/profile capture and recall, RAG embedding-model filtering, vector global search fallback, and trace metadata observability.
- Active slice: final verification evidence is complete.
- Next step: preserve this evidence for review and decide whether to commit.

## Evidence

- `docker compose -f docker-compose.full.yml ps`: backend and dependencies are running, backend is healthy.
- `.\\mvnw.cmd -v`: Maven wrapper currently resolves Maven 3.9.11 successfully.
- `rg -n "Authorization|Bearer|chat\\?" frontend/src/stores/chatStore.ts frontend`: chat stream sends `Authorization: Bearer <token>`.
- `.\\mvnw.cmd -B -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=MemoryTurnCaptureStageTests,MemoryAggregationServiceTests,ProfileSlotResolverTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,VectorGlobalSearchFeatureTests,KernelRetrievalEngineTests,JdbcKnowledgeBaseQueryAdapterTests,SeahorseAgentKernelRetrievalAutoConfigurationTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`: 111 tests, 0 failures.
- `npm.cmd test -- src/stores/chatStore.test.ts`: 9 tests, 0 failures.
- `.\\mvnw.cmd -B -pl seahorse-agent-bootstrap -am "-DskipTests" package`: build success.
- `docker compose -f docker-compose.full.yml build backend` and `docker compose -f docker-compose.full.yml up -d --no-deps backend`: backend image rebuilt and backend container recreated.
- `docker inspect --format='{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' seahorse-backend`: `running healthy`.
- Auth SSE E2E through `http://localhost/api/rag/v3/chat`: HTTP 200, `text/event-stream;charset=UTF-8`, events `meta,message,finish,done`, answer `ok`, no expired-login text.
- Memory/profile E2E suffix `20260614071920`: write answer `已记住`; strict recall answer `identity.occupation=闭环验证平台可靠性工程师-20260614071920;preferences.response_style=三句内中文回答-20260614071920`.
- Postgres `t_user_profile_fact`: active rows for `identity.occupation` and `preferences.response_style` contain exact clean values with `access_count=1`.
- RAG E2E: answer `Milvus, nomic-embed-text`.
- RAG trace `324448193208250368`: `VectorGlobalSearch` hit `rag_final_v2`; `extra_data.metadata` includes `embeddingModel=nomic-embed-text`, `collectionCount=2`, `searchableCollectionCount=2`, `failedCollectionCount=1`.

## DriftCheckDraft

- Original task intent: still active.
- Compatibility boundary: preserve data volumes and unrelated dirty docs.
- New owners/fallbacks/adapters: none.
- Evidence sufficiency: sufficient for this task; unit, frontend, build, container health, API E2E, database, and trace evidence are present.
- Decision: ready to report.

## Risk / Unknown

- Existing Docker data contains an old missing Milvus collection reference; global vector search now skips it and records `failedCollectionCount=1` instead of failing the request.
- Full repository test suite was not run; verification focused on the modified memory/RAG/auth surface.
