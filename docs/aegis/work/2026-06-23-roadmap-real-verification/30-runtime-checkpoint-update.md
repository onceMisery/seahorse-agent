# Runtime Checkpoint Update

Date: 2026-06-23

## TodoCheckpointDraft

- Current todo: continue real-case verification for recently merged roadmap features in the local full Docker deployment.
- Completed todos:
  - Repaired deployed-page API compatibility for User Memory Center and Agent Marketplace when the Docker frontend strips the `/api` prefix before proxying to the backend.
  - Rebuilt and redeployed `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` into the running `seahorse-backend` container.
  - Verified backend health after redeploy.
  - Reran real browser page smoke against `http://127.0.0.1`.
  - Reran full Docker backend smoke against `http://127.0.0.1:9090`.
  - Reran strict RAG evaluation smoke.
  - Reran smoke contract guard.
- Active slice: Agent control-plane and near-term runtime verification.
- Next step:
  - Continue with run experiment trial/scoring/cost/trace/fork evidence.

## 2026-06-23 Run Profile Inheritance Slice

- Completed todos:
  - Added a repeatable full-Docker smoke script for conversation-level run profile inheritance:
    `scripts/e2e-run-profile-inheritance-smoke.ps1`.
  - Verified the script against the running local backend at `http://127.0.0.1:9090`.
  - Covered login, system run profile discovery, conversation creation, applying profile `-9105`, reading applied profile, sending chat without explicit `runProfileId`, and checking `t_run_context_snapshot`.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-run-profile-inheritance-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 7 / 7 passed, 0 failed.
  - Smoke conversation: `327842563404230656`.
  - Snapshot run: `TbXiqhPVpfUSgJL5XGD4`.
  - Snapshot captured `runProfileId=-9105`, `roleCardId=-9004`, `executorEngine=kernel`, role card name `测试质量审查`, run profile name `安全审批方案`, and `explicitToolAllowlist=true`.
- DriftCheckDraft:
  - Scope: still validating already-merged roadmap capabilities with real Docker/API/chat evidence.
  - Compatibility: no data reset; no unrelated runtime behavior changed.
  - Decision: continue.
- Next step:
  - Verify run experiment trial, scoring, cost/trace evidence, and fork-to-branch.
  - Verify MCP stdio/HTTP and AgentScope/Nacos paths, or explicitly record configured-off/deferred status.

## 2026-06-23 Message Tree Branch Slice

- Completed todos:
  - Added a repeatable full-Docker smoke script for message tree branching:
    `scripts/e2e-message-tree-branch-smoke.ps1`.
  - Verified the script against the running local backend at `http://127.0.0.1:9090`.
  - Covered login, conversation creation, real chat SSE, message listing, assistant-message fork, branch switch, branch cursor save/load, active tree reload, and DB verification.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-message-tree-branch-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result after script parser repair: 7 / 7 passed, 0 failed.
  - Smoke conversation: `327844159806664704`.
  - Original user message: `327844161396305920`.
  - Original assistant message: `327844191305887744`.
  - Fork message: `327844201363828736`.
  - DB verification confirmed original assistant `active=0`, fork message `parent_id=327844161396305920`, `active=1`, `sibling_seq=1`, and branch cursor leaf `327844201363828736`.
- DriftCheckDraft:
  - Scope: validates already-merged message-tree and branch-cursor capabilities with real Docker/API/chat/DB evidence.
  - Compatibility: no data reset; the script leaves an auditable smoke conversation.
  - Decision: continue.
- Next step:
  - Verify run experiment trial, scoring, cost/trace evidence, and fork-to-branch.

## 2026-06-24 Run Experiment Slice

- Completed todos:
  - Added a repeatable full-Docker smoke script for run experiments:
    `scripts/e2e-run-experiment-smoke.ps1`.
  - Repaired old Docker-volume startup self-healing for missing run experiment tables.
  - Repaired run experiment trial execution wiring so experiments resolve the real trial executor when an experiment is created.
  - Verified the script against the running local backend at `http://127.0.0.1:9090`.
  - Covered login, run profile discovery, conversation creation, base chat SSE, experiment creation, trial execution, trial scoring, fork-to-branch, database verification, run context snapshots, and output messages.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result on 2026-06-24: 9 / 9 passed, 0 failed.
  - Smoke conversation: `327970727723954176`.
  - Base user message: `327970727866560512`.
  - Base assistant message: `327970765300723712`.
  - Experiment: `327970775857786880`.
  - Forked trial: `327970775861981184`.
  - Forked output message: `327970814571212800`.
  - Docker DB currently contains `sa_run_experiment` and `sa_run_experiment_trial`.
- Root causes repaired:
  - Old Docker volumes could lack `sa_run_experiment` and `sa_run_experiment_trial`, so run experiment creation failed with a missing table error.
  - `SeahorseAgentKernelAutoConfiguration` imported Ops before Agent, so `KernelRunExperimentService` could be created before the real `ReActExecutorPort`-backed trial executor was visible and experiments stayed on the noop path.
- Repair details:
  - `JdbcChatSchemaUpgrade.ensureRunExperimentTables()` now creates the experiment/trial tables and indexes during startup upgrade.
  - `KernelRunExperimentService` now accepts a lazy `Supplier<RunExperimentTrialExecutorPort>` and resolves it at experiment creation time.
  - `SeahorseAgentKernelAutoConfiguration` now imports Agent before Ops; Ops passes the lazy supplier.
- DriftCheckDraft:
  - Scope: validates already-merged run experiment capabilities with real Docker/API/chat/DB evidence.
  - Compatibility: no Docker volume reset; the repair self-heals missing schema in place and preserves existing runtime contracts.
  - Decision: continue.
- Next step:
  - Rerun the broader real regression suite after documenting this slice.
  - Continue MCP stdio/HTTP, AgentScope/Nacos, and governance permission/error-state gates.

## 2026-06-24 MCP Stdio Slice

- Completed todos:
  - Added a repeatable real Docker smoke script for MCP stdio:
    `scripts/e2e-mcp-stdio-smoke.ps1`.
  - Verified the currently running full-compose backend keeps MCP disabled and returns the expected unavailable state for MCP management APIs.
  - Started a temporary MCP-enabled backend container on the existing Docker network without clearing volumes or restarting the main backend.
  - Covered MCP server discovery, safe stdio echo call, MCP tool catalog exposure, refresh-tools, restart, and stderr-tail.
  - Repaired the tool catalog API contract so the already-built provider/risk filters from the admin tools page are honored by the backend.
- Evidence refs:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelToolCatalogManagementServiceTests,JdbcToolCatalogRepositoryAdapterTests,SeahorseAgentControllerTests,AdvancedFeatureControllerGateTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; 31 tests run, 0 failures.
  - `.\mvnw -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true"`
  - Result: build success; fresh bootstrap exec jar produced.
  - `.\scripts\e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093`
  - Fresh result: 8 / 8 passed, 0 failed.
  - Smoke backend: temporary `seahorse-mcp-stdio-smoke` on `http://127.0.0.1:9093`; the script removed the container after the run.
  - MCP server: `local-echo`.
  - MCP tool catalog row: `toolId=echo`, `provider=MCP`, `riskLevel=MEDIUM`, `actionType=EXECUTE`, `resourceType=MCP`, `enabled=true`.
- Root causes repaired:
  - The temporary MCP smoke backend did not set `SEAHORSE_AGENT_PRODUCT_MODE=enterprise`, so provider exposure policy could hide MCP tools even though the MCP server itself was available.
  - The smoke backend also did not enable `SEAHORSE_AGENT_ADVANCED_TOOL_CATALOG_MANAGEMENT_ENABLED=true`, so `/api/tools` could be blocked by the feature gate.
  - The admin tools page sent `provider` and `riskLevel` filters, but `SeahorseToolCatalogController`, `ToolCatalogManagementInboundPort`, and `ToolCatalogQuery` did not accept them; JDBC only filtered `resource_type`, `keyword`, and `enabled`.
- Repair details:
  - `ToolCatalogQuery` now carries `provider` and `riskLevel` while preserving the old constructor shape for existing resource-type queries.
  - `SeahorseToolCatalogController` now accepts `provider` and `riskLevel` request params and passes them through the inbound port.
  - `KernelToolCatalogManagementService` and `JdbcToolCatalogRepositoryAdapter` now propagate and apply those filters.
  - `scripts/e2e-mcp-stdio-smoke.ps1` now starts the temporary backend in enterprise mode with tool catalog management enabled.
- DriftCheckDraft:
  - Scope: validates the roadmap MCP stdio baseline with a real Docker backend, real stdio child process, real API calls, and the existing PostgreSQL catalog.
  - Compatibility: no Docker volumes were cleared; only the temporary smoke container was removed after the run.
  - Decision: continue.
- Next step:
  - Add repeatable AgentScope smoke evidence and continue governance permission/error-state gates.

## 2026-06-24 MCP HTTP Slice

- Completed todos:
  - Added a repeatable real Docker smoke script for MCP streamable HTTP:
    `scripts/e2e-mcp-http-smoke.ps1`.
  - Added a minimal HTTP MCP JSON-RPC echo server for the smoke run:
    `resources/docker/mcp-http-echo.js`.
  - Started a temporary HTTP MCP server container and a temporary MCP HTTP-enabled backend on the existing Docker network without clearing volumes or restarting the main backend.
  - Verified direct HTTP MCP JSON-RPC `initialize`, `tools/list`, and `tools/call` against the temporary HTTP MCP server.
  - Verified `/api/mcp/servers` reports `http-echo` as `READY` and a configured `broken-http` server as `FAILED`.
  - Verified safe HTTP MCP echo call, MCP tool catalog exposure, refresh-tools, restart, stderr-tail, and broken-server containment.
  - Repaired auto-configuration ordering so MCP tool allowlist/catalog registration sees the retrieval MCP orchestrator before agent auto-configuration runs.
- Evidence refs:
  - `.\mvnw.cmd -B -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentAutoConfigurationOrderingTests,McpToolAllowlistRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"`
  - Result: 6 tests run, 0 failures.
  - `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
  - Result: build success; fresh bootstrap exec jar produced.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-mcp-http-smoke.ps1 -BaseUrl http://127.0.0.1:9096`
  - Fresh result: 12 / 12 passed, 0 failed.
  - Smoke backend: temporary `seahorse-mcp-http-smoke` on `http://127.0.0.1:9096`; the script removed the temporary backend and HTTP MCP server containers after the run.
  - MCP HTTP server: `http-echo`.
  - MCP HTTP tool: `http.echo`.
  - Tool catalog evidence: `sa_tool_catalog` contains `tool_id=http.echo`, `provider=MCP`, `enabled=true`.
- Root causes repaired:
  - PowerShell `Set-Content -Encoding UTF8` wrote a BOM in JSON request bodies, which broke direct JSON-RPC checks against the minimal HTTP server. The smoke now writes temporary request files with UTF-8 without BOM.
  - HTTP MCP discovery and safe calls worked, but `http.echo` initially did not enter `sa_tool_catalog`.
  - `SeahorseAgentKernelAutoConfiguration` imported Agent before Retrieval, so `McpToolAllowlistRegistrar` could be skipped when `KernelMcpOrchestrator` and `McpToolRegistryPort` were not visible yet.
- Repair details:
  - `scripts/e2e-mcp-http-smoke.ps1` writes JSON request temp files with `[System.Text.UTF8Encoding]($false)`.
  - `SeahorseAgentKernelAutoConfiguration` now imports Retrieval before Agent while preserving Agent before Ops for run experiment executor wiring.
  - `SeahorseAgentAutoConfigurationOrderingTests` now asserts Retrieval is imported before Agent.
- DriftCheckDraft:
  - Scope: validates the roadmap MCP HTTP baseline with a real Docker backend, real HTTP MCP JSON-RPC server, real API calls, and the existing PostgreSQL catalog.
  - Compatibility: no Docker volumes were cleared; only temporary smoke containers were removed after the run.
  - Decision: continue.

## 2026-06-24 AgentScope Slice

- Completed todos:
  - Added a repeatable full-Docker AgentScope smoke script:
    `scripts/e2e-agentscope-smoke.ps1`.
  - Verified the main full-compose backend has `SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true` and A2A disabled.
  - Sent a real chat through SYSTEM run profile `-9104` (`AgentScope 观测方案`) and verified the persisted run context snapshot used `executor_engine=agentscope`.
  - Verified A2A endpoints are currently disabled and return 404.
  - Sent a second real chat through SYSTEM kernel profile `-9101` to prove the kernel path still works after the AgentScope path.
- Evidence refs:
  - `.\scripts\e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result: 10 / 10 passed, 0 failed.
  - AgentScope conversation: `327985792133001216`.
  - AgentScope run: `7vW6rTJ_MtDGoeE2QyWy`.
  - AgentScope snapshot: `roleCardId=-9007`, `runProfileId=-9104`, `executorEngine=agentscope`, run profile name `AgentScope 观测方案`.
  - Kernel fallback conversation: `327985966565715968`.
  - Kernel fallback run: `fwc3p8K5CVnZJtRN1zCk`.
  - Kernel snapshot: `roleCardId=-9001`, `runProfileId=-9101`, `executorEngine=kernel`, run profile name `默认轻量方案`.
- DriftCheckDraft:
  - Scope: validates the current AgentScope executor baseline and configured-off A2A state in the main full Docker deployment.
  - Compatibility: no Docker volumes were cleared; the script leaves auditable smoke conversations and snapshots.
  - Decision: continue.
- Next step:
  - Continue governance permission/error-state gates and remaining A2A long-path evidence if required.

## 2026-06-24 Role Card Runtime Recheck

- Completed todos:
  - Rechecked built-in role cards through the running full Docker stack after the user reported the page did not appear to offer selectable role cards.
  - Verified the API returns 7 SYSTEM role cards from `GET /api/role-cards`.
  - Verified the real `/chat` page role-card combobox opens and lists all 7 cards.
  - Verified `/admin/role-cards` lists the same 7 SYSTEM cards and shows 7 enable actions.
  - Added and verified a repeatable full-Docker smoke for explicit role-card chat:
    `scripts/e2e-role-card-chat-smoke.ps1`.
  - Repaired direct chat handling for SYSTEM preset role cards with negative IDs.
- Evidence refs:
  - API result on `http://127.0.0.1:9090/api/role-cards`: `Count=7`.
  - SYSTEM role cards: `-9001 general assistant`, `-9002 requirement analyst`, `-9003 code developer`, `-9004 quality reviewer`, `-9005 knowledge writer`, `-9006 data analyst`, `-9007 AgentScope debugger`.
  - Database result from `sa_role_card`: the 7 SYSTEM preset rows exist, are `published=1`, `approval_status=APPROVED`, `readonly=1`, and currently have `enabled=0`.
  - Browser `/chat` dropdown opened and listed all 7 SYSTEM cards; selecting a card changed the combobox value.
  - Browser `/admin/role-cards` listed the same 7 SYSTEM cards and exposed 7 enable actions.
  - `mvnw.cmd -B -pl seahorse-agent-kernel -am "-Dtest=StreamChatCommandTests,KernelChatRoleCardTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"`
  - Result: 12 tests run, 0 failures.
  - `mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
  - Result: build success; redeployed `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` into `seahorse-backend`; health returned `UP`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result after fix: 5 / 5 passed, 0 failed.
  - Smoke conversation: `328155377939410944`.
  - Snapshot run: `_fQ_IzFbXDS2kqID5LLS`.
  - Snapshot captured `roleCardId=-9003`, `roleCardName=code developer`, `executorEngine=kernel`, and no run profile override.
- Root cause repaired:
  - `StreamChatCommand.normalizeRoleCardId()` reused positive-ID normalization, so SYSTEM preset IDs such as `-9003` were converted to `null` before the chat kernel could resolve or snapshot them.
- Repair details:
  - `StreamChatCommand.normalizeRoleCardId()` now only treats `0` as blank and preserves negative SYSTEM preset IDs.
  - `StreamChatCommandTests` now covers preservation of `-9003` and normalization of `0`.
- Interpretation:
  - Built-in role cards exist and are selectable.
  - `enabled=0` means no SYSTEM role card is currently the user's default active card; it does not mean the cards are unavailable for manual selection.
  - Explicit role-card chat now has direct run context snapshot evidence, not only UI visibility or run-profile-bound inheritance evidence.
- DriftCheckDraft:
  - Scope: confirms the user's role-card concern with real API, database, browser, chat, and run context snapshot evidence.
  - Compatibility: no data was reset; the script leaves an auditable smoke conversation and snapshot.
  - Decision: continue.

## 2026-06-24 Governance Error-State Slice

- Completed todos:
  - Added a repeatable real Docker/API smoke script for governance and structured error states:
    `scripts/e2e-governance-error-states-smoke.ps1`.
  - Verified bad login returns a structured 400 response.
  - Verified admin login works and governance APIs return normal envelopes.
  - Verified normal user `demo_user_001/demo123` login works.
  - Verified normal user permission errors for protected governance APIs are structured instead of raw failures.
  - Verified the main backend MCP disabled state returns structured 409 service-unavailable behavior.
- Evidence refs:
  - `.\scripts\e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result: 8 / 8 passed, 0 failed.
  - Admin user ID: `2001523723396308993`.
  - Normal user ID: `323451851919118336`.
  - Covered `/api/tools`, `/api/agents`, `/api/approvals`, `/api/access-decisions`, unknown-tool 404, empty tool search, and MCP-disabled 409 behavior.
- DriftCheckDraft:
  - Scope: validates roadmap governance/error-state expectations with real Docker/API evidence.
  - Compatibility: no Docker volumes were cleared; the script leaves only auditable access/error calls.
  - Decision: continue.

## 2026-06-24 Governance Page-State Slice

- Completed todos:
  - Added a repeatable real browser smoke script for governance/admin visual states:
    `scripts/e2e-governance-page-states-smoke.ps1` and `scripts/e2e-governance-page-states-smoke.mjs`.
  - Verified admin data state on `/admin/tools` renders real tool catalog rows from the running Docker backend.
  - Verified admin empty state by searching a guaranteed non-existent tool keyword and checking the visible `暂无工具` state.
  - Verified a normal user cannot enter `/admin/tools` and is returned to `/workspace`.
  - Verified page-level permission-denied and backend-unavailable states by forcing the tools API to return 409 and 503 inside a real browser session.
  - Repaired the tool catalog page so tool-list load failures are shown as a persistent inline error instead of falling through to `暂无工具`.
  - Repaired frontend error message extraction so Axios errors prefer backend `response.data.message` before the generic transport message.
  - Rebuilt the frontend and redeployed `frontend/dist` into the running `seahorse-frontend` container.
- Evidence refs:
  - `npm run build` in `frontend`
  - Result: Vite production build success.
  - `docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html/`
  - Result: updated the running frontend container without clearing volumes.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-governance-page-states-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: 5 / 5 scenarios passed, 0 failed.
  - Screenshots:
    - `output/playwright/artifacts/governance-admin-tools-data-state.png`
    - `output/playwright/artifacts/governance-admin-tools-empty-state.png`
    - `output/playwright/artifacts/governance-normal-user-admin-route-guard.png`
    - `output/playwright/artifacts/governance-admin-tools-permission-denied-state.png`
    - `output/playwright/artifacts/governance-admin-tools-backend-unavailable-state.png`
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result: 8 / 8 passed, 0 failed.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: passed; checked routes `15`; readiness `enterprise/healthy`; workspace task `task_405444226`; workspace conversation `328168106678972416`; chat SSE `200 text/event-stream;charset=UTF-8`.
- Root causes repaired:
  - `ToolCatalogPage` caught tool-list failures, showed a transient toast, and then rendered the same empty-table state as a legitimate no-data result.
  - `getErrorMessage()` read Axios `error.message` before `error.response.data.message`, so backend messages such as `权限不足` could be hidden behind `Request failed with status code 409`.
- Repair details:
  - `ToolCatalogPage` now tracks `toolsError`, clears it on successful load, and renders a persistent inline error panel with a retry action.
  - `getErrorMessage()` now returns `response.data.message` when present before falling back to `error.message`.
- DriftCheckDraft:
  - Scope: validates governance page states with real browser evidence in the deployed Docker frontend.
  - Compatibility: no Docker volumes were cleared; the normal-user route guard and simulated 409/503 page states are non-mutating.
  - Decision: continue.

## 2026-06-24 A2A/Nacos Temporary Pair Slice

- Completed todos:
  - Started a temporary A2A-enabled main backend on port `9094` using the existing Docker network, Nacos, PostgreSQL, and `seahorse-agent-backend:latest`.
  - Ran `scripts/agentscope-a2a-e2e.ps1` against the temporary main backend and a temporary remote backend on port `9095`.
  - Verified main and remote Agent Card endpoints.
  - Verified no-auth and wrong-token POSTs are rejected with 401.
  - Verified authenticated A2A JSON-RPC calls succeed on both instances.
  - Verified the live Nacos connector smoke test discovers/calls the remote A2A agent.
  - Confirmed no `seahorse-a2a*` temporary containers remained after the run.
- Evidence refs:
  - Temporary main URL: `http://127.0.0.1:9094/a2a`.
  - Temporary remote URL: `http://localhost:9095/a2a`.
  - Remote agent: `seahorse-e2e-1782274275`.
  - `MAIN_CARD_OK=200`, `MAIN_POST_NO_AUTH=401`, `MAIN_POST_WRONG_TOKEN=401`, `MAIN_POST_AUTH=200`.
  - `REMOTE_CARD_OK=200`, `REMOTE_POST_NO_AUTH=401`, `REMOTE_POST_WRONG_TOKEN=401`, `REMOTE_POST_AUTH=200`.
  - `REMOTE_DIRECT_OK`, `NACOS_CONNECTOR_SMOKE_OK`, `E2E_RESULT=PASS`.
  - Maven live smoke result: `AgentScopeA2ALiveSmokeTest` ran 1 test, 0 failures, build success.
- DriftCheckDraft:
  - Scope: validates the A2A/Nacos live path with temporary containers because the main full-compose backend intentionally has A2A disabled.
  - Compatibility: no Docker volumes were cleared; the main running backend was not reconfigured; temporary containers were removed.
  - Decision: continue.

## 2026-06-24 OpenAPI Connector Slice

- Completed todos:
  - Added the OpenAPI adapter to the executable bootstrap dependency graph and explicitly imported `OpenApiAdapterAutoConfiguration`.
  - Rebuilt and redeployed the main `seahorse-backend` container.
  - Verified a real OpenAPI import with GET and DELETE operations against the running Docker backend.
  - Verified low-risk GET operation enablement, OpenAPI tool catalog exposure, and high-risk DELETE enablement failure without approval.
  - Repaired Docker frontend proxy compatibility by exposing `/connectors...` aliases for the OpenAPI connector controller.
  - Repaired the OpenAPI admin page contract so operation `status=ENABLED/DISABLED` is mapped to the UI `enabled` state and `createdAt` is shown as create time.
  - Rebuilt and redeployed the frontend container, then verified the OpenAPI connector list/detail pages in a real browser.
- Evidence refs:
  - `.\mvnw.cmd -B -pl seahorse-agent-adapter-web,seahorse-agent-bootstrap,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-adapter-openapi -am "-Dtest=SeahorseAgentControllerTests,SeahorseAgentBootstrapDependencyTests,SeahorseAgentRegistryAutoConfigurationTests,OpenApiSpecParserAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"`
  - Result: build success; `SeahorseAgentControllerTests` 17 tests, 0 failures; `SeahorseAgentRegistryAutoConfigurationTests` 2 tests, 0 failures; `SeahorseAgentBootstrapDependencyTests` 5 tests, 0 failures.
  - `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
  - Result: build success; redeployed `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` into `seahorse-backend`; health returned `UP`.
  - `npm run build` in `frontend`: build success; redeployed `frontend/dist` into `seahorse-frontend` and reloaded nginx.
  - Final alias-path API smoke on `http://127.0.0.1:9090/connectors/openapi`: `connectorId=conn_935f85cd23eb8a7f`, `importCode=0`, operations `DELETE:HIGH:DISABLED:true`, `GET:LOW:DISABLED:false`, GET enabled as `openapi_b0172e7ec239f854`, DELETE without approval returned HTTP 409.
  - Database verification in `sa_connector`/`sa_connector_operation`: `conn_935f85cd23eb8a7f`, name `Codex OpenAPI Alias Smoke 1782276608`, status `IMPORTED`, operation count `2`, operations `DELETE:HIGH:DISABLED, GET:LOW:ENABLED`.
  - Real browser page verification:
    - `/admin/integrations/connectors` lists `Codex OpenAPI Smoke 1782275921`, shows create time, and no longer falls into the empty state through the Docker frontend proxy.
    - `/admin/integrations/connectors/conn_6ac1c4e70cbf7746` lists `DELETE /pets/{petId} deletePet HIGH disabled` and `GET /pets listPets LOW enabled`.
    - Screenshot: `output/playwright/openapi-connector-detail-current.png`.
- Root causes repaired:
  - The executable backend did not instantiate the OpenAPI parser adapter in the deployed bootstrap, so import returned service-unavailable behavior.
  - The Docker frontend proxies `/api/connectors...` to backend `/connectors...`, but the OpenAPI connector controller only exposed `/api/connectors...`.
  - The frontend detail page expected an `enabled` boolean, while the backend operation payload exposes `status=ENABLED/DISABLED`.
- DriftCheckDraft:
  - Scope: validates the roadmap OpenAPI connector baseline with real Docker backend, real frontend proxy, real browser page, real PostgreSQL rows, and real governance failure for high-risk enablement.
  - Compatibility: no Docker volumes were cleared; smoke connectors are auditable runtime data.
  - Decision: continue.

## 2026-06-24 Post-OpenAPI Redeploy Regression Rerun

- Completed todos:
  - Reran the main real-smoke suite after redeploying both backend and frontend for the OpenAPI connector fixes.
  - Verified backend health, login, feature flags, readiness, knowledge CRUD/upload/chunking, RAG SSE, trace API, memory/profile, catalog, audit, metadata governance, and SRE health.
  - Verified real browser page smoke for 15 routes, workspace task creation, and chat SSE.
  - Reran strict RAG evaluation smoke after the redeploy.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 20 checks, 0 failed.
  - Fresh backend evidence: KB `328035074466803712`, doc `328035075200806912`, trace `328035094515576832`, conversation `smoke-1782276855`, memory value `smoke-profile-1782276855`, facts `7`, data export task `74`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Result: passed; checked routes `15`; readiness `enterprise/healthy`; workspace task `task_278830258`; workspace conversation `328035156234760192`; chat SSE `200 text/event-stream;charset=UTF-8`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 7 / 7 passed, 0 failed; KB `328035427115495424`, doc `328035427392319488`, dataset `328035440721817600`, recall@k `1.0`, precision@k `0.2`, MRR `1.0`, NDCG `0.46927872602275655`, avg latency `72.0ms`.
- DriftCheckDraft:
  - Scope: confirms the OpenAPI redeploy did not regress the core full-Docker smoke baseline.
  - Compatibility: no Docker volumes were cleared; RAG evaluation cleanup deleted its temporary KB/doc.
  - Decision: continue.

## 2026-06-24 Post-Role-Card Fix Regression Rerun

- Completed todos:
  - Reran the main real-smoke suite after redeploying the backend for the SYSTEM role-card ID fix.
  - Verified backend health, login, feature flags, readiness, knowledge CRUD/upload/chunking, RAG SSE, trace API, memory/profile, catalog, audit, metadata governance, and SRE health.
  - Verified real browser page smoke for 15 routes, workspace task creation, and chat SSE.
  - Reran strict RAG evaluation smoke after the redeploy.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 20 checks, 0 failed.
  - Fresh backend evidence: KB `328157253816709120`, doc `328157254227750912`, trace `328157259462242304`, conversation `smoke-1782305986`, memory value `smoke-profile-1782305986`, facts `7`, data export task `75`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Result: passed; checked routes `15`; readiness `enterprise/healthy`; workspace task `task_687199270`; workspace conversation `328157871755128832`; chat SSE `200 text/event-stream;charset=UTF-8`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 7 / 7 passed, 0 failed; KB `328158002218954752`, doc `328158004278358016`, dataset `328158049794945024`, recall@k `1.0`, precision@k `0.2`, MRR `1.0`, NDCG `0.46927872602275655`, avg latency `4086.0ms`; cleanup deleted the temporary KB/doc.
- DriftCheckDraft:
  - Scope: confirms the role-card fix redeploy did not regress the core full-Docker smoke baseline.
  - Compatibility: no Docker volumes were cleared; RAG evaluation cleanup deleted its temporary KB/doc.
  - Decision: continue.

## 2026-06-24 Ingestion Pipeline Real Verification Slice

- Completed todos:
  - Added a repeatable real Docker/API/DB smoke for ingestion pipeline execution, retry, and rollback:
    `scripts/e2e-ingestion-pipeline-smoke.ps1`.
  - Added a repeatable real browser smoke for the deployed ingestion admin page:
    `scripts/e2e-ingestion-page-smoke.ps1` and `scripts/e2e-ingestion-page-smoke.mjs`.
  - Repaired PostgreSQL persistence for ingestion tasks so real BIGINT and JSONB columns are written with typed values instead of string values.
  - Rebuilt and redeployed the backend, then verified the current full Docker deployment.
- Evidence refs:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-ingestion-pipeline-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result on 2026-06-24: 13 / 13 passed, 0 failed.
  - Marker: `CODX_INGESTION_PIPELINE_1782312876619`.
  - Success pipeline: `328186154588729344`.
  - Success task: `328186154773278720`.
  - Failed task: `328186157755428864`.
  - Retry task: `328186158007087104`.
  - Rollback task: `328186160221679616`.
  - Rollback doc: `328186160049713152`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-ingestion-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: `ok=true`, marker `CODX_INGESTION_PAGE_1782312892003`, pipeline `328186219164233728`, task `328186219227148288`.
  - Screenshot: `D:\code\seahorse-agent\output\playwright\artifacts\ingestion-page-CODX_INGESTION_PAGE_1782312892003.png`.
- Root cause repaired:
  - Real Docker API creation of ingestion tasks initially failed against PostgreSQL because `JdbcIngestionTaskRepositoryAdapter` wrote string `pipelineId`, `taskId`, `nodeId`, and JSON strings into BIGINT/JSONB columns.
- Repair details:
  - `JdbcIngestionTaskRepositoryAdapter` now converts IDs to numeric values before insert and casts JSON values with `CAST(? AS JSONB)`.
  - The focused JDBC tests now model the real schema more closely with BIGINT and JSONB columns.
- Coverage:
  - Success pipeline creation and parser/chunker task execution.
  - Task node readback through API.
  - `t_ingestion_task` and `t_ingestion_task_node` verification in PostgreSQL.
  - Controlled failure at `indexer`, including failed node evidence and `INDEXER_FAILED`.
  - Retry from failed node with metadata evidence: `retryOfTaskId`, `retryFromNodeId`, `restoredNodeIds`, and `retryCount=1`.
  - Rollback target execution and compensation evidence: task marked rolled back and `t_knowledge_document.deleted=1`.
  - Deployed `/admin/ingestion?tab=pipelines` and `/admin/ingestion?tab=tasks` pages render the seeded pipeline and task in a real browser.
- DriftCheckDraft:
  - Scope: validates the roadmap M2 ingestion governance and recoverable pipeline baseline with real Docker API, PostgreSQL, rollback compensation, and browser page evidence.
  - Compatibility: no Docker volumes were cleared; smoke-created pipeline/task/document rows remain auditable runtime data.
  - Decision: continue.

## 2026-06-24 RAG Strategy Promotion Real Verification Slice

- Completed todos:
  - Added a repeatable real Docker/API/browser/DB smoke for RAG strategy promotion:
    `scripts/e2e-rag-strategy-promotion-smoke.ps1` and `scripts/e2e-rag-strategy-promotion-smoke.mjs`.
  - Seeded a real knowledge base, uploaded and chunked a document, created a retrieval evaluation dataset, ran strategy comparison, opened the deployed `/admin/rag-evaluation/{kbId}/{datasetId}` page, clicked `Promote as online strategy`, and verified API/DB/audit evidence.
  - Repaired PostgreSQL JSONB persistence for retrieval evaluation run/comparison reports and strategy template options while preserving H2 test compatibility.
  - Repaired the frontend promotion payload so the page sends the backend's expected template payload instead of only `strategyKey`.
  - Rebuilt and redeployed the backend container, then verified the current full Docker deployment.
- Evidence refs:
  - `.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"`
  - Result: 8 tests run, 0 failures.
  - `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
  - Result: build success; redeployed `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` into `seahorse-backend`; health returned `UP`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-strategy-promotion-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: `ok=true`, marker `CODX_RAG_PROMOTION_1782315276726`.
  - KB: `328196222944178176`.
  - Document: `328196223397163008`.
  - Dataset: `328196250622390272`.
  - Comparison: `328196271665213440`.
  - Winner/template: `hybrid_rrf`.
  - Template DB row: `hybrid_rrf|1|1`.
  - Audit row: `audit_328196287549042688|RETRIEVAL_STRATEGY_PROMOTED|RETRIEVAL_STRATEGY_TEMPLATE|328196222944178176:hybrid_rrf|...comparisonId 328196271665213440...`.
  - Screenshot: `D:\code\seahorse-agent\output\playwright\artifacts\rag-strategy-promotion-CODX_RAG_PROMOTION_1782315276726.png`.
- Root causes repaired:
  - The comparison API could return a report but not persist `t_retrieval_evaluation_comparison` in PostgreSQL because report JSON was bound as a string for a JSONB column.
  - The promotion API failed in PostgreSQL when strategy template options were bound as a string for `options_json JSONB`.
  - H2 focused tests read default retrieval options after a PostgreSQL-only cast double-encoded JSON into string test columns.
  - The deployed page sent only `{ strategyKey }`, while the backend promotion endpoint expects the complete template payload.
- Repair details:
  - `JdbcRetrievalEvaluationDatasetRepositoryAdapter` now uses a database-aware JSON placeholder: PostgreSQL writes `?::jsonb`, H2/test writes `?`.
  - `JdbcRetrievalStrategyTemplateRepositoryAdapter` uses the same database-aware placeholder for template options.
  - `frontend/src/services/ragEvaluationService.ts` and `frontend/src/pages/admin/rag-evaluation/RetrievalDatasetDetailPage.tsx` now send the full promotion template payload.
  - The smoke verifies the audit row by `resource_id` and `comparisonId`, matching the actual audit payload contract.
- Regression refs after backend redeploy:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 7 / 7 passed, 0 failed; KB `328196356683755520`, doc `328196357812023296`, dataset `328196373037346816`; cleanup deleted the temporary KB/doc.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 5 / 5 passed, 0 failed; conversation `328196357501644800`, role card `-9003`, run `QSugBDZsdGKQChc0juHb`, executor `kernel`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Result: passed; checked routes `15`; readiness `enterprise/healthy`; workspace task `task_747979953`; workspace conversation `328196584254107648`; chat SSE `200 text/event-stream;charset=UTF-8`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 20 checks, 0 failed; KB `328197187269193728`, doc `328197187772510208`, trace `328197193464180736`, conversation `smoke-1782315506`, data export task `77`.
- DriftCheckDraft:
  - Scope: validates the roadmap RAG strategy promotion and audit-link baseline with real Docker API, deployed browser page, PostgreSQL state, and audit-event evidence.
  - Compatibility: no Docker volumes were cleared; smoke-created KB/dataset/comparison/template/audit rows remain auditable runtime data.
  - Decision: continue.

## 2026-06-24 Memory Governance Real Verification Slice

- Completed todos:
  - Added a repeatable real Docker/API/DB/browser smoke for memory governance:
    `scripts/e2e-memory-governance-smoke.ps1` and `scripts/e2e-memory-governance-smoke.mjs`.
  - Seeded two real `t_short_term_memory` rows for admin user `2001523723396308993` with the same `metadata_json.semanticKey=profile:occupation` and different content.
  - Ran `POST /api/memories/governance/run?userId=2001523723396308993&assessQuality=true` through the deployed frontend proxy.
  - Verified `GET /api/memories/conflicts?userId=...&status=PENDING` returns the generated conflict while the legacy `status=open` filter does not.
  - Repaired the deployed memory governance frontend contract so it queries current-user `PENDING` conflicts, sends `{ action }` on resolve, and renders the backend's real conflict/snapshot fields.
  - Rebuilt and redeployed the frontend into `seahorse-frontend`.
  - Verified the real `/admin/memory-governance` page displays the seeded conflict, resolves it from the page, and PostgreSQL marks it `RESOLVED`.
  - Reran broad page and backend smokes after the frontend redeploy.
- Evidence refs:
  - `npm run build` in `frontend`
  - Result: Vite production build succeeded.
  - `docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html/`
  - `docker exec seahorse-frontend nginx -s reload`
  - Result: nginx reload signal accepted.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-memory-governance-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: `ok=true`, marker `CODX_MEMORY_GOVERNANCE_1782317341190`.
  - Seed memories: `codxmgA1782317341241`, `codxmgB1782317341241`.
  - Conflict: `mem-conflict-328204881313034240`, status before page resolve `PENDING`.
  - Quality snapshot: `mem-quality-328204881338200064`.
  - DB resolve row: `mem-conflict-328204881313034240|RESOLVED|keep_a|system`.
  - Screenshot: `D:\code\seahorse-agent\output\playwright\artifacts\memory-governance-CODX_MEMORY_GOVERNANCE_1782317341190.png`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Result: passed; checked routes `15`; readiness `enterprise/healthy`; workspace conversation `328205035927662592`; chat SSE `200 text/event-stream;charset=UTF-8`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Result: 20 checks, 0 failed; trace `328204967807971328`, conversation `smoke-1782317359`, memory value `smoke-profile-1782317359`, facts `7`, data export task `78`.
- Root causes repaired:
  - `MemoryConflictPanel` queried `status=open`, but the backend stores pending conflicts as `PENDING`.
  - `MemoryConflictPanel` and `memoryGovernanceService` used stale field names (`conflictId`, `memoryIdA`, `status`) while the backend returns `id`, `memoryId1`, `memoryId2`, and `resolutionStatus`.
  - `resolveMemoryConflict` sent `{ resolution }`, but `SeahorseMemoryController` expects request field `action`.
  - `MemoryQualityPanel` queried default backend user `system`, not the logged-in user, and rendered fields that are not in the backend `MemoryQualitySnapshot` contract.
- DriftCheckDraft:
  - Scope: validates the roadmap M3 memory quality/governance baseline with real Docker frontend, API, PostgreSQL, and browser interaction.
  - Compatibility: no Docker volumes were cleared; seeded memory/conflict/snapshot rows remain auditable runtime evidence.
  - Decision: continue.

## 2026-06-24 D4 Trace Reference Cleanup Slice

- Completed todos:
  - Removed the roadmap-reported stale `/admin/traces` reference from `docs/deployment/local-embedding-model-guide.md`.
  - Updated `docs/analysis/roadmap-completion-status-report.md` so D4 no longer lists this resolved item as pending.
- Evidence refs:
  - `rg -n "/admin/traces" docs\deployment\local-embedding-model-guide.md`
  - Result: no matches; command wrapper printed `local guide stale trace reference: none`.
- DriftCheckDraft:
  - Scope: closes the D4 documentation fact-source cleanup item named in the status report.
  - Compatibility: no runtime behavior changed; valid frontend route references in other documents were not mass-edited.
  - Decision: continue.

## 2026-06-25 Agent Rollout Promote Real Verification Slice

- Completed todos:
  - Added and verified a repeatable real Docker/API/browser/DB smoke for Agent rollout promotion:
    `scripts/e2e-agent-rollout-smoke.ps1` and `scripts/e2e-agent-rollout-smoke.mjs`.
  - Verified the missing production gate path through real API: create Canary rollout, promote without a gate, expect `FAILED` and `GATE_MISSING`.
  - Verified the deployed `/admin/agents/{agentId}/rollout` page creates a Canary rollout, shows `RUNNING`, promotes it to full rollout, and shows `PROMOTED`.
  - Verified PostgreSQL `sa_agent_version_rollout` captures the promoted status and seeded gate report ID.
  - Verified `sa_audit_event` contains `AGENT_ROLLOUT_FAILED`, `AGENT_ROLLOUT_STARTED`, and `AGENT_ROLLOUT_PROMOTED` rows.
  - Rebuilt and redeployed the backend jar into the running `seahorse-backend` container so the frontend proxy can reach the unprefixed rollout routes.
  - Reran broad backend and page smokes after the backend redeploy.
- Evidence refs:
  - First failing run:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-agent-rollout-smoke.ps1 -BaseUrl http://127.0.0.1`
    failed waiting for page `RUNNING`.
  - Root-cause probe:
    `POST http://127.0.0.1/api/agents/.../rollouts/canary` through the deployed frontend proxy returned 404 before backend redeploy, while direct backend `POST http://127.0.0.1:9090/api/agents/.../rollouts/canary` returned 200.
  - `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
    result: build success; fresh bootstrap exec jar produced.
  - `docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar`
    and `docker restart seahorse-backend`; health returned `UP`.
  - Alias probes after redeploy:
    frontend proxy and direct unprefixed backend rollout canary endpoints both returned `200` with `RUNNING`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-agent-rollout-smoke.ps1 -BaseUrl http://127.0.0.1`
    fresh result: `ok=true`, marker `CODX_AGENT_ROLLOUT_1782321569033`.
  - Failure rollout: `avr_328222617137213440`, audit `AGENT_ROLLOUT_FAILED|AGENT_ROLLOUT|avr_328222617137213440`.
  - Promoted rollout: `avr_328222695876882432`, gate report `gate_1782321571731`, DB row `PROMOTED||gate_1782321571731`.
  - Audit rows: `AGENT_ROLLOUT_STARTED|AGENT_ROLLOUT|avr_328222695876882432` and `AGENT_ROLLOUT_PROMOTED|AGENT_ROLLOUT|avr_328222695876882432`.
  - Screenshot: `D:\code\seahorse-agent\output\playwright\artifacts\agent-rollout-CODX_AGENT_ROLLOUT_1782321569033.png`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
    result: 20 checks, 0 failed; trace `328222925850570752`, conversation `smoke-1782321618`, data export task `79`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
    result: checked routes `15`; readiness `enterprise/healthy`; workspace conversation `328222959727964160`; chat SSE `200 text/event-stream;charset=UTF-8`.
- Root cause repaired:
  - The deployed frontend calls `/api/agents/...`, and `frontend/nginx.conf` strips the `/api` prefix before proxying to the backend.
  - The running backend jar still exposed rollout endpoints only under `/api/agents/...`, so deployed-page rollout creation hit backend `/agents/...` and returned 404.
- Repair details:
  - `SeahorseAgentRolloutController` exposes both prefixed and unprefixed rollout routes.
  - `AgentRolloutPage` treats `RUNNING` as an active rollout status so the real API-created Canary rollout shows the full-promotion action.
- DriftCheckDraft:
  - Scope: validates the roadmap P2 promote rollout full flow with real Docker API, deployed browser page, PostgreSQL, and audit evidence.
  - Compatibility: no Docker volumes were cleared; smoke-created rollout and gate rows remain auditable runtime evidence.
  - Decision: continue.

## Verification Matrix Delta

## 2026-06-25 S3 Object Storage Switching Real Verification Slice

- Completed todos:
  - Added `scripts/e2e-s3-storage-smoke.ps1`.
  - Started a temporary S3-enabled backend container on the existing Docker network without restarting the main backend.
  - Verified login, conversation creation, conversation attachment upload, PostgreSQL `sa_conversation_attachment.storage_ref`, MinIO object existence, API listing, API delete, PostgreSQL soft delete, and MinIO object removal.
  - Repaired S3-mode auto-configuration ordering/conditions so services that depend on `ObjectStoragePort` are not skipped before the S3 adapter bean is registered.
- Evidence refs:
  - Initial failing smoke exposed the real runtime issue:
    `POST /api/conversations/{conversationId}/attachments` returned `409 Service not available` even though `seahorseS3ObjectStorageAdapter` existed.
  - Runtime `actuator/conditions` showed `seahorseConversationAttachmentInboundPort` and `seahorseConversationAttachmentContextAssembler` were skipped because method-level `@ConditionalOnBean(ObjectStoragePort.class)` evaluated before the S3 storage bean was visible.
  - `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
    result: build success; fresh bootstrap exec jar produced.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-s3-storage-smoke.ps1`
    result: `10 / 10 passed, 0 failed`.
  - Marker: `CODX_S3_STORAGE_20260625021150519`.
  - Conversation: `328235789327970304`.
  - Attachment: `attachment-328235796567339008`.
  - DB row before delete:
    `attachment-328235796567339008|328235789327970304|2001523723396308993|s3://conversation-attachments/7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp|0`.
  - MinIO stat before delete:
    bucket `conversation-attachments`, key `7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp`, size `53 B`, content type `text/plain`.
  - Delete evidence:
    PostgreSQL `deleted=1`; MinIO `mc stat` returned non-zero after API delete.
  - Deployed the rebuilt backend jar into `seahorse-backend`; health returned `UP`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
    result: 20 checks, 0 failed; trace `328237607990751232`, conversation `smoke-1782325131`, data export task `80`.
  - First page smoke after redeploy saw transient route-change `net::ERR_ABORTED` requests but no page errors, bad responses, or empty pages.
  - Fresh rerun:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
    result: checked routes `15`; readiness `enterprise/healthy`; workspace conversation `328238006193778688`; chat SSE `200 text/event-stream;charset=UTF-8`.
- Root cause repaired:
  - S3 auto-configuration produced `S3Client` and `S3ObjectStorageAdapter`, but `KernelOps` and chat attachment configuration used method-level `@ConditionalOnBean(ObjectStoragePort.class)` and were evaluated before the S3 object storage bean was visible in the condition phase.
- Repair details:
  - `SeahorseAgentKernelOpsAutoConfiguration` now runs after storage/S3 configuration and no longer gates the attachment inbound service on early `ObjectStoragePort` condition checks.
  - `SeahorseAgentKernelChatAutoConfiguration` now runs after S3 storage configuration and no longer gates the attachment context assembler on early `ObjectStoragePort` condition checks.
  - `SeahorseAgentKernelDocumentRefreshAutoConfiguration` now explicitly runs after S3 storage configuration.
- DriftCheckDraft:
  - Scope: validates M5 adapter switching for S3 object storage with real Docker backend, MinIO, API, and PostgreSQL evidence.
  - Compatibility: the main backend was not restarted during the S3 smoke; the temporary container was removed after the smoke; no Docker volumes were cleared.
  - Decision: continue.

## 2026-06-25 Pulsar MQ Consume Loop Real Verification Slice

- Completed todos:
  - Added `scripts/e2e-pulsar-mq-smoke.ps1`.
  - Verified the main full Docker backend is configured with `SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar` and `SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650`.
  - Verified the real Pulsar topic `persistent://seahorse-agent/ai/knowledge-document-chunk` has an active `seahorse-document-chunk-consumer` subscription.
  - Created a real knowledge base, uploaded a marker document, called `/knowledge-base/docs/{docId}/chunk`, and waited for the Pulsar consumer to materialize the document into PostgreSQL chunks.
  - Verified Pulsar publish/consume counters advanced and the active subscription ended with `msgBacklog=0` and `unackedMessages=0`.
  - Verified backend logs contain `Document chunk processing completed` for the same document id.
- Evidence refs:
  - First run exposed a script-only issue: `docker logs` emitted an existing sa-token deprecation warning on stderr, and PowerShell treated that stderr line as a terminating error while the real Pulsar/API/DB checks had already passed 8 / 9.
  - Script repair: `Wait-ForBackendLog` now captures `docker logs` with native stderr tolerated and still checks the process exit code.
  - Fresh command:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-pulsar-mq-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
  - Fresh result: `9 / 9 passed, 0 failed`.
  - Marker: `CODX_PULSAR_MQ_20260625023439715`.
  - Knowledge base: `328241584501321728`.
  - Document: `328241585365348352`.
  - PostgreSQL evidence:
    `success|1|1|1`, meaning `t_knowledge_document.status=success`, document `chunk_count=1`, one live `t_knowledge_chunk`, and one live chunk containing the marker.
  - Pulsar evidence:
    before `msgIn/msgOut=27/27`; after `msgIn/msgOut=28/28`; subscription `msgBacklog=0`, `unackedMessages=0`, `lastAckedTimestamp=1782326095967`.
  - Backend log evidence:
    `Document chunk processing completed: docId=328241585365348352`.
- DriftCheckDraft:
  - Scope: validates the roadmap P2 Pulsar consume-loop item with the currently running full Docker backend, real Pulsar broker, real API calls, PostgreSQL side effects, and backend log evidence.
  - Compatibility: no Docker volumes were cleared; no service was restarted; the script leaves an auditable smoke KB/document/chunk.
  - Decision: continue.

## 2026-06-25 Memory Profile Facts Source Tracing Real Verification Slice

- Completed todos:
  - Added `scripts/e2e-memory-profile-facts-smoke.mjs` and `scripts/e2e-memory-profile-facts-smoke.ps1`.
  - Repaired `ProfileFact` and `JdbcProfileMemoryRepositoryAdapter` so `t_user_profile_fact.source_ids` is exposed as `sourceIds` in `/api/memories/profile-facts`.
  - Updated the deployed memory governance operations panel to request profile facts for the logged-in user instead of falling back to the backend `system` default.
  - Replaced the running backend jar and frontend static assets in the existing full Docker deployment without clearing volumes.
  - Verified API, PostgreSQL, and real browser page source-tracing evidence.
- Evidence refs:
  - Backend mapping test:
    `.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests#shouldUpsertProfileFactAsStrongFactSource" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"`
  - Result: 1 test, 0 failures.
  - Frontend build:
    `npm run build` in `frontend`.
  - Result: Vite production build completed.
  - Backend package:
    `.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
  - Runtime deploy:
    `docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar`,
    `docker restart seahorse-backend`, and `docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html`.
  - Backend health after restart: `UP`.
  - Fresh command:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-memory-profile-facts-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Fresh result: passed.
  - Marker: `CODX_PROFILE_SOURCE_1782328814663`.
  - User ID: `2001523723396308993`.
  - Profile fact ID: `1782328814817350`.
  - Slot key: `codex.profile_source.1782328814817`.
  - API evidence: `sourceIds` returned both `memory-snapshot-1782328814817350` and `conversation-message-1782328814817350`; `confidenceLevel=0.923`, `version=3`, `accessCount=7`.
  - PostgreSQL evidence:
    `1782328814817350|codex.profile_source.1782328814817|CODX_PROFILE_SOURCE_1782328814663|0.923|explicit_user_memory|["memory-snapshot-1782328814817350", "conversation-message-1782328814817350"]|codex.profile_source.1782328814817:generation|3|7`.
  - Browser evidence:
    screenshot `output/playwright/artifacts/memory-profile-facts-CODX_PROFILE_SOURCE_1782328814663.png`; the deployed `/admin/memory-governance` operations/profile-facts view rendered the marker row and source id after row expansion.
  - Post-redeploy backend regression:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090`
    result: 20 checks, 0 failed; trace `328254004200370176`; conversation `smoke-1782329050`; memory facts `9`; data export task `81`.
  - Post-redeploy page regression:
    `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
    result: checked routes `15`; readiness `enterprise/healthy`; workspace task `task_239464495`; workspace conversation `328254407939878912`; chat SSE `200 text/event-stream;charset=UTF-8`.
- DriftCheckDraft:
  - Scope: validates the M3 profile-fact detail/source-tracing item with the currently running full Docker backend/frontend, real PostgreSQL state, API response, and browser page interaction.
  - Compatibility: no Docker volumes were cleared; the smoke leaves an auditable profile-fact row for the admin user.
  - Decision: continue.

| Feature area | Current evidence | Status |
|---|---|---|
| Role cards | SYSTEM role cards self-heal into old Docker volumes; API returns 7 cards; `/admin/role-cards` and `/chat` role-card dropdown were verified in a real browser. A fresh recheck confirmed the cards are selectable even though all SYSTEM presets currently have `enabled=0`, meaning no system card is the default active role. `scripts/e2e-role-card-chat-smoke.ps1` sent a real chat with explicit `roleCardId=-9003` and verified `t_run_context_snapshot` captured `roleCardId=-9003`, `roleCardName=code developer`, and `executorEngine=kernel`. Run profile inheritance smoke also captured role card `-9004` as a full role-card snapshot. | passed for visibility/selectability, explicit chat snapshot influence, and run-profile-bound snapshot influence |
| Run profiles | `scripts/e2e-run-profile-inheritance-smoke.ps1` applied SYSTEM profile `-9105` to a real conversation, sent chat without explicit `runProfileId`, and verified `t_run_context_snapshot` captured `runProfileId=-9105`, `roleCardId=-9004`, `executorEngine=kernel`, and copied profile/role-card names. | passed for conversation inheritance baseline |
| Message tree | `scripts/e2e-message-tree-branch-smoke.ps1` created a real chat path, forked an assistant branch, switched to it, saved/reloaded branch cursor, and verified `t_message` parent/active/sibling fields plus `t_conversation_branch_cursor`. | passed for fork/switch/restore baseline |
| Run experiments | `scripts/e2e-run-experiment-smoke.ps1` created a real base chat, ran two trial profiles, scored a trial, forked the trial output to a branch, and verified experiment/trial rows, snapshots, and output messages in PostgreSQL. | passed for trial execution/scoring/fork baseline |
| MCP stdio / HTTP | `scripts/e2e-mcp-stdio-smoke.ps1` started a temporary MCP-enabled backend, discovered `local-echo`, executed a safe stdio echo call, exposed `echo` through `/api/tools?provider=MCP`, and verified refresh/restart/stderr-tail. `scripts/e2e-mcp-http-smoke.ps1` started a temporary HTTP MCP server and backend, verified direct JSON-RPC `initialize/tools/list/tools/call`, `http-echo READY`, `broken-http FAILED`, safe echo call, `http.echo` catalog exposure, refresh/restart/stderr-tail, and broken-server containment. | passed for stdio and HTTP discovery/call/catalog/admin API baselines |
| AgentScope / A2A | `scripts/e2e-agentscope-smoke.ps1` used SYSTEM profile `-9104` for real chat, verified `t_run_context_snapshot.executor_engine=agentscope`, verified A2A endpoints are disabled with 404 on the main backend, then verified kernel profile `-9101` still writes `executor_engine=kernel`. A temporary A2A-enabled main/remote pair verified Agent Card, auth failures, auth success, direct remote call, and Nacos connector live smoke. | passed for executor baseline, configured-off isolation, and temporary A2A/Nacos live path |
| Agent rollout promotion | `scripts\e2e-agent-rollout-smoke.ps1` verified missing-gate promotion fails with `FAILED/GATE_MISSING`, then opened the deployed rollout page, created a real Canary rollout, promoted it to `PROMOTED`, verified `sa_agent_version_rollout`, and verified `AGENT_ROLLOUT_FAILED`, `AGENT_ROLLOUT_STARTED`, and `AGENT_ROLLOUT_PROMOTED` audit rows. | passed for promote rollout full-flow baseline |
| OpenAPI connector | Final deployed backend accepts alias-path imports through `/connectors/openapi`, parses GET/DELETE operations, enables low-risk GET into `/api/tools?provider=OPENAPI`, blocks high-risk DELETE enablement without approval with structured 409, persists connector/operation rows, and the real Docker frontend list/detail pages render the connector and correct operation states. | passed for import/parse/catalog/governance/page baseline |
| Governance/admin pages | Page smoke visited 15 routes and found no 404/empty-page/console hard failures after the proxy alias repair. `scripts/e2e-governance-error-states-smoke.ps1` verified structured bad-login, admin/normal-user auth, protected governance permission errors, unknown-tool 404, empty tool search, and MCP-disabled 409 behavior. `scripts/e2e-governance-page-states-smoke.ps1` now verifies the real `/admin/tools` page data state, empty state, normal-user route guard, inline `权限不足` state, and inline `Service not available` state in a browser. | passed for API and visual page-state baselines |
| Ingestion pipeline | `scripts/e2e-ingestion-pipeline-smoke.ps1` created success/failing/rollback pipelines and tasks, verified task/node rows in PostgreSQL, proved retry metadata from failed node, and verified rollback compensation against `t_knowledge_document.deleted=1`. `scripts/e2e-ingestion-page-smoke.ps1` seeded a real pipeline/task and verified `/admin/ingestion` pipeline and task tabs render them in the deployed Docker frontend. | passed for execution, failure evidence, retry, rollback, DB, and page baselines |
| RAG strategy promotion | `scripts/e2e-rag-strategy-promotion-smoke.ps1` seeded a real KB/doc/chunks/dataset, ran strategy comparison, opened the deployed strategy comparison page, clicked promote, verified the recommended template through API and `t_retrieval_strategy_template`, and verified a `RETRIEVAL_STRATEGY_PROMOTED` row in `sa_audit_event` linked to the comparison ID. | passed for comparison persistence, page promotion, recommended template DB state, and audit linkage |
| RAG/Trace | Backend smoke produced document chunk evidence and RAG trace API evidence with retrieval nodes. | passed for smoke baseline |
| Memory/profile/governance | Backend smoke produced active profile facts and readiness evidence; User Memory Center route no longer 404s through Docker frontend. `scripts/e2e-memory-governance-smoke.ps1` now seeds real conflicting short-term memories, runs governance, verifies `PENDING` conflict API evidence and quality snapshot evidence, opens `/admin/memory-governance`, resolves the conflict from the page, and verifies `t_memory_conflict_log` changes to `RESOLVED`. `scripts/e2e-memory-profile-facts-smoke.ps1` seeds a profile fact with `source_ids`, verifies `/api/memories/profile-facts` returns `sourceIds`, opens the deployed governance profile-facts view, expands the row, and verifies source tracing is visible in the browser. | passed for profile source tracing, profile smoke, conflict/quality/page-resolution baseline |
| RAG evaluation | Strict smoke created KB/doc/chunks/dataset, evaluated 2/2 cases, and required non-zero recall. | passed for smoke baseline |
| S3 object storage switching | `scripts/e2e-s3-storage-smoke.ps1` started a temporary S3-enabled backend against the existing full Docker Postgres/MinIO network, uploaded a real conversation attachment, verified `sa_conversation_attachment.storage_ref` as `s3://conversation-attachments/...`, verified the object through MinIO `mc stat`, listed it through API, then deleted it through API and verified PostgreSQL `deleted=1` plus MinIO object removal. | passed for S3 adapter switch/upload/list/delete/DB/MinIO baseline |
| Pulsar consume loop | `scripts/e2e-pulsar-mq-smoke.ps1` ran against the main full Docker backend with `SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar`, uploaded a marker knowledge document, triggered `/knowledge-base/docs/{docId}/chunk`, verified PostgreSQL `t_knowledge_document.status=success`, live marker chunk creation, Pulsar `msgIn/msgOut` counter advance from `27/27` to `28/28`, subscription `msgBacklog=0` and `unackedMessages=0`, and backend log completion for the same document id. | passed for Pulsar publish/consume/ack/materialization baseline |
| Full compose | Full Docker stack is running; after the role-card fix redeploy, backend smoke passed 20/20, page smoke passed across 15 routes with chat SSE, and RAG eval smoke passed 7/7. | passed for current smoke baseline |

## DriftCheckDraft

- Scope remains aligned with the original request: stabilize recent features through real Docker/API/browser evidence.
- The proxy alias repair restores the existing deployment contract documented by `frontend/nginx.conf`; it does not change business logic.
- No Docker volumes were cleared.
- Evidence is enough to claim the role-card visibility/selectability problem is fixed in the current full Docker deployment.
- Evidence now covers the previously open governance visual no-data, permission-denied, and backend-unavailable states for the tool catalog page.
- Evidence now covers S3 object storage switching through a temporary S3-enabled backend, real MinIO object presence, and deletion cleanup.
- Evidence now covers the Pulsar knowledge-document consume loop through real broker counters, active subscription ack state, PostgreSQL materialization, and backend logs.
- Evidence now covers M3 profile-fact source tracing through `source_ids` -> API `sourceIds` -> deployed governance page expanded detail.
- Decision: continue.

## Risk / Unknown

- Governance permission/error states now have both API smoke evidence and browser page-state evidence for the tool catalog page; other admin pages can reuse this pattern if a later gate requires per-page visual coverage.
- The older `20-checkpoint.md` file contains mojibake text; this file records the latest checkpoint in ASCII to avoid further encoding churn.
- First page smoke after the backend redeploy reported transient `net::ERR_ABORTED` navigation-cancelled requests; an immediate rerun passed with no page errors, bad responses, or empty pages.
