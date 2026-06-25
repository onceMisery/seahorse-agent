# Runtime Evidence Update

Date: 2026-06-23

## Page Proxy Alias Repair

- Symptom: real browser page smoke failed after login because deployed frontend requests returned 404 for:
  - `GET http://127.0.0.1/api/me/memories?limit=50`
  - `GET http://127.0.0.1/api/marketplace/agents?sort=popularity&page=1&size=12`
  - `GET http://127.0.0.1/api/marketplace/agents/my-subscriptions`
- Reproduction:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1`
  - Result before repair: page flow reached login, 15 routes, readiness, workspace task, and chat SSE, but failed because those three responses were 404.
- Root cause:
  - `frontend/nginx.conf` proxies `location /api/` to `http://seahorse-backend:9090/`, stripping the `/api` prefix.
  - `SeahorseUserMemoryController` and `SeahorseMarketplaceController` only exposed `/api/...` mappings, unlike controllers such as role cards and run profiles that expose both prefixed and unprefixed paths.
- Repair:
  - Added unprefixed aliases to `SeahorseUserMemoryController`.
  - Added unprefixed aliases to `SeahorseMarketplaceController`.
  - Kept `/api/...` mappings for direct backend/API compatibility.

## Verification Commands

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseUserMemoryControllerTests,SeahorseMarketplaceControllerTests,SeahorseRunProfileControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test '-Dspotless.check.skip=true'
```

Result: build success; 18 tests run, 0 failures.

```powershell
.\mvnw.cmd package -B -pl seahorse-agent-bootstrap -am -DskipTests '-Dmaven.test.skip=true' '-Dspotless.check.skip=true'
```

Result: build success; `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` produced.

```powershell
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result: backend restarted; `GET http://127.0.0.1:9090/actuator/health` returned `UP`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result: passed.

- Checked routes: 15.
- Login redirected to `http://127.0.0.1/workspace`.
- Readiness: `mode=enterprise`, `overall=healthy`, `checks=13`.
- Workspace task: `taskId=task_070406959`, `conversationId=327825025186295808`.
- Chat SSE: `GET /api/rag/v3/chat?...` returned 200 with `text/event-stream`.
- Artifacts: `D:\code\seahorse-agent\output\playwright\artifacts`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result: 20 checks, 0 failed.

- Covered health, full compose runtime, auth, feature flags, readiness, user/quota/notifications/export, KB CRUD, document upload/chunk, RAG SSE, trace API, memory/profile facts, agent/tool/skill catalog, audit API, metadata governance API, and SRE health.
- Latest smoke ids included `kbId=327825125849591808`, `docId=327825126399045632`, `traceId=327825131847446528`, `conversationId=smoke-1782226800`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result: 7 / 7 passed, 0 failed.

- KB ID: `327825420918878208`.
- Document ID: `327825421216673792`.
- Chunk records: 1.
- Dataset ID: `327825434583920640`.
- Cases evaluated: 2 / 2.
- Metrics: `recall@k=1.0`, `precision@k=0.2`, `MRR=1.0`, `NDCG=0.46927872602275655`, `emptyRecallRate=0.0`.
- Cleanup deleted the document and KB.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1
```

Result: passed for `scripts/e2e-backend-smoke.ps1`, `scripts/e2e-rag-evaluation-smoke.ps1`, and `frontend/Dockerfile.frontend`.

## Evidence Gap

- Role card visibility, page selection, explicit `/chat?roleCardId=...` influence, and run-profile-bound role-card influence are verified in `t_run_context_snapshot`.
- Message tree fork/branch restore now has a repeatable real Docker/API/chat/DB smoke.
- Run experiment trial/scoring/fork evidence now has a repeatable real Docker/API/chat/DB smoke.
- MCP stdio and HTTP now have repeatable real Docker/API smokes. AgentScope executor/failure-isolation now has a repeatable real Docker/API/chat/DB smoke. A2A/Nacos long-path verification now has temporary-pair live evidence.

## Run Profile Inheritance Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-run-profile-inheritance-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result: 7 / 7 passed, 0 failed.

Covered:

- Login as `admin`.
- `GET /api/run-profiles` returns SYSTEM profile `-9105` (`安全审批方案`) with bound role card `-9004`.
- `POST /api/conversations` creates a real conversation.
- `POST /api/conversations/{conversationId}/run-profile/-9105/apply` applies the profile.
- `GET /api/conversations/{conversationId}/run-profile` reads back the applied profile.
- `GET /rag/v3/chat?conversationId=...&question=...` sends a real SSE chat request without explicit `runProfileId`.
- `t_run_context_snapshot` has the inherited profile and role-card context.

Observed output:

```text
Summary: 7 / 7 passed, 0 failed
Conversation ID: 327842563404230656
Run profile ID: -9105
Role card ID: -9004
Chat content type: text/event-stream;charset=UTF-8
Chat bytes: 942
```

Snapshot evidence:

```json
{"runId":"TbXiqhPVpfUSgJL5XGD4","roleCardId":"-9004","runProfileId":"-9105","executorEngine":"kernel","roleCardName":"测试质量审查","runProfileName":"安全审批方案","explicitToolAllowlist":"true"}
```

This closes the current baseline for conversation-level run profile inheritance into real chat requests and run context snapshots.

## Message Tree Branch Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-message-tree-branch-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

First run found a script parser bug, not an application failure:

- Symptom: DB cursor check read `3` instead of the full cursor ID.
- Root cause: PowerShell unrolled a single-row result to a string, so `$cursorRows[0]` returned the first character.
- Repair: wrap DB rows in `@(...)` before indexing.

Final result: 7 / 7 passed, 0 failed.

Covered:

- Login as `admin`.
- `POST /api/conversations` creates a real conversation.
- `GET /rag/v3/chat?conversationId=...&question=...` creates a real user/assistant message path through SSE.
- `GET /api/conversations/{conversationId}/messages` reads the active messages.
- `POST /api/conversations/{conversationId}/messages/fork` creates an assistant sibling branch.
- `POST /api/conversations/{conversationId}/messages/branch/switch` switches active path to the fork.
- `POST /api/conversations/{conversationId}/branch-cursor` saves the active leaf.
- `GET /api/conversations/{conversationId}/branch-cursor` and `GET /api/conversations/{conversationId}/messages/tree` reload the selected branch.
- DB verification checks `t_message.parent_id`, `active`, `sibling_seq`, and `t_conversation_branch_cursor.leaf_message_id`.

Observed output:

```text
Summary: 7 / 7 passed, 0 failed
Conversation ID: 327844159806664704
Original user message ID: 327844161396305920
Original assistant message ID: 327844191305887744
Fork message ID: 327844201363828736
```

DB assertions:

- Original assistant message `327844191305887744` became `active=0`.
- Fork message `327844201363828736` has `parent_id=327844161396305920`, `role=assistant`, `active=1`, `sibling_seq=1`.
- Branch cursor leaf is `327844201363828736`.

This closes the current baseline for message fork, branch switch, refresh-style cursor restore, and persisted branch/path fields.

## Run Experiment Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Fresh result on 2026-06-24: 9 / 9 passed, 0 failed.

Covered:

- Login as `admin`.
- `GET /api/run-profiles` returns SYSTEM kernel profiles `-9101` and `-9102`.
- `POST /api/conversations` creates a real conversation.
- `GET /rag/v3/chat?conversationId=...&question=...` creates a real base user/assistant path through SSE.
- `POST /api/run-experiments` creates an experiment from the base assistant message and executes one trial per run profile.
- Every trial returns `SUCCEEDED`, `runId`, `outputMessageId`, and `metricJson`.
- `POST /api/run-experiments/{experimentId}/trials/{trialId}/score` persists `scoreJson`.
- `POST /api/run-experiments/{experimentId}/trials/{trialId}/fork-to-branch` returns the trial output as branch data.
- PostgreSQL checks verify `sa_run_experiment`, `sa_run_experiment_trial`, `t_run_context_snapshot`, and `t_message`.

Observed output:

```text
Summary: 9 / 9 passed, 0 failed
Conversation ID: 327970727723954176
Base user message ID: 327970727866560512
Base assistant message ID: 327970765300723712
Experiment ID: 327970775857786880
Forked trial ID: 327970775861981184
Forked output message ID: 327970814571212800
```

Defect 1 found by real Docker smoke:

- Symptom: creating a run experiment failed because `sa_run_experiment` did not exist in the old local Docker volume.
- Root cause: startup self-healing had not created run experiment tables for existing volumes.
- Repair: `JdbcChatSchemaUpgrade.ensureRunExperimentTables()` now creates `sa_run_experiment`, `sa_run_experiment_trial`, and indexes during startup upgrade.
- Focused regression:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcChatSchemaUpgradeTests' '-Dsurefire.failIfNoSpecifiedTests=false' test '-Dspotless.check.skip=true'
```

Result from repair run: 11 tests run, 0 failures.

Defect 2 found by real Docker smoke:

- Symptom: after schema repair, experiment trials stayed `PENDING`; `run_id` and `output_message_id` remained blank.
- Root cause: `SeahorseAgentKernelAutoConfiguration` imported Ops before Agent, so the `KernelRunExperimentService` could be wired before the real trial executor was available and used the noop executor.
- Repair:
  - `KernelRunExperimentService` resolves `RunExperimentTrialExecutorPort` lazily when an experiment is created.
  - `SeahorseAgentKernelOpsAutoConfiguration` passes a lazy supplier.
  - `SeahorseAgentKernelAutoConfiguration` imports Agent before Ops.
- Focused regressions:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-kernel '-Dtest=KernelRunExperimentServiceTests' test '-Dspotless.check.skip=true'
```

Result from repair run: 5 tests run, 0 failures.

```powershell
.\mvnw.cmd -B -pl seahorse-agent-spring-boot-autoconfigure -am '-Dtest=SeahorseAgentAutoConfigurationOrderingTests' '-Dsurefire.failIfNoSpecifiedTests=false' test '-Dspotless.check.skip=true'
```

Result from repair run: 3 tests run, 0 failures.

Deployment verification:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests '-Dmaven.test.skip=true' '-Dspotless.apply.skip=true' '-Dspotless.check.skip=true'
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
curl.exe -s http://127.0.0.1:9090/actuator/health
```

Result: backend jar rebuilt and hot-deployed; health returned `{"status":"UP"}`.

DB table evidence:

```powershell
docker exec seahorse-postgres psql -U seahorse -d seahorse -t -A -F "|" -c "select tablename from pg_tables where schemaname='public' and tablename ilike '%experiment%' order by tablename;"
```

Result:

```text
sa_run_experiment
sa_run_experiment_trial
```

This closes the current baseline for run experiment creation, trial execution, manual scoring, fork-to-branch, and persisted run context/output evidence.

## MCP Stdio Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093
```

Fresh result on 2026-06-24: 8 / 8 passed, 0 failed.

Covered:

- Started temporary backend container `seahorse-mcp-stdio-smoke` on the existing `seahorse-agent_default` Docker network.
- Enabled MCP only in the temporary backend; the main `seahorse-backend` container and Docker volumes were not reset.
- Logged in as `admin`.
- `GET /api/mcp/servers` returned `local-echo` with tool `echo`.
- `POST /api/mcp/servers/local-echo/test` executed the local stdio MCP server and returned `stdio:seahorse mcp health check`.
- `GET /api/tools?current=1&size=50&provider=MCP` returned `echo` with provider `MCP`.
- `POST /api/mcp/servers/local-echo/refresh-tools` returned `echo`.
- `POST /api/mcp/servers/local-echo/restart` returned `echo`.
- `GET /api/mcp/servers/local-echo/stderr-tail` returned through the management API.

Observed output:

```text
Summary: 8 / 8 passed, 0 failed
Smoke backend: http://127.0.0.1:9093
MCP server: local-echo
MCP tool: echo
```

Tool catalog evidence:

```json
{"toolId":"echo","provider":"MCP","name":"echo","description":"Echo text from a local stdio MCP server.","riskLevel":"MEDIUM","actionType":"EXECUTE","resourceType":"MCP","ownerTeam":"mcp","enabled":true,"requiresApproval":false}
```

Defect found by real Docker smoke:

- Symptom: MCP server discovery and safe stdio calls worked, but `/api/tools?current=1&size=50&provider=MCP` did not return the MCP `echo` tool.
- Root causes:
  - The temporary smoke backend was not started in enterprise mode, so MCP provider exposure could be hidden by demo defaults.
  - The temporary smoke backend did not enable tool catalog management, so `/api/tools` could be rejected by the advanced feature gate.
  - The admin tools page already sent `provider` and `riskLevel`, but the backend tool catalog API only accepted `resourceType`, `keyword`, `current`, `size`, and `enabled`.
- Repairs:
  - `scripts/e2e-mcp-stdio-smoke.ps1` now sets `SEAHORSE_AGENT_PRODUCT_MODE=enterprise`.
  - `scripts/e2e-mcp-stdio-smoke.ps1` now sets `SEAHORSE_AGENT_ADVANCED_TOOL_CATALOG_MANAGEMENT_ENABLED=true`.
  - `SeahorseToolCatalogController`, `ToolCatalogManagementInboundPort`, `KernelToolCatalogManagementService`, `ToolCatalogQuery`, and `JdbcToolCatalogRepositoryAdapter` now carry and apply `provider` and `riskLevel` filters.

Focused regression:

```powershell
.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelToolCatalogManagementServiceTests,JdbcToolCatalogRepositoryAdapterTests,SeahorseAgentControllerTests,AdvancedFeatureControllerGateTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; 31 tests run, 0 failures.

Deployment verification for the temporary backend image:

```powershell
.\mvnw.cmd -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true"
```

Result: build success; fresh `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` produced and included in the updated `seahorse-agent-backend:latest` image used by the temporary smoke container.

This closes the current baseline for MCP stdio discovery, safe call execution, tool catalog exposure, refresh/restart, and stderr-tail management.

## MCP HTTP Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-mcp-http-smoke.ps1 -BaseUrl http://127.0.0.1:9096
```

Fresh result on 2026-06-24: 12 / 12 passed, 0 failed.

Covered:

- Started temporary HTTP MCP server container `seahorse-mcp-http-server-smoke` on the existing `seahorse-agent_default` Docker network.
- Started temporary backend container `seahorse-mcp-http-smoke` on the same Docker network with MCP HTTP enabled.
- Verified direct HTTP MCP JSON-RPC `initialize`, `tools/list`, and `tools/call`.
- Logged in as `admin`.
- `GET /api/mcp/servers` returned `http-echo` as `READY` with transport `STREAMABLE_HTTP`.
- `GET /api/mcp/servers` returned `broken-http` as `FAILED`, proving one bad HTTP MCP endpoint does not break the ready server.
- `POST /api/mcp/servers/http-echo/test` executed a safe echo call and returned `http:seahorse mcp health check`.
- `GET /api/tools?current=1&size=50&provider=MCP&keyword=http.echo` returned enabled MCP tool `http.echo`.
- `POST /api/mcp/servers/http-echo/refresh-tools` and `/restart` returned `http.echo`.
- `POST /api/mcp/servers/broken-http/test` returned a contained failure instead of succeeding or breaking the management API.
- `GET /api/mcp/servers/http-echo/stderr-tail` returned through the management API.

Observed output:

```text
Summary: 12 / 12 passed, 0 failed
Smoke backend: http://127.0.0.1:9096
MCP HTTP server: http-echo
MCP HTTP tool: http.echo
```

Direct JSON-RPC evidence:

```text
initialize serverInfo.name=seahorse-http-echo
tools/list includes http.echo
tools/call http.echo returned http:direct smoke
```

Tool catalog evidence:

```text
sa_tool_catalog contains tool_id=http.echo, provider=MCP, enabled=true
```

Defect 1 found by real smoke:

- Symptom: the direct HTTP MCP JSON-RPC call failed while the same payload looked valid in the script.
- Root cause: PowerShell `Set-Content -Encoding UTF8` wrote a BOM into temporary JSON request files, and the minimal HTTP MCP server rejected the body.
- Repair: `scripts/e2e-mcp-http-smoke.ps1` now writes request temp files with `[System.Text.UTF8Encoding]($false)`.

Defect 2 found by real smoke:

- Symptom: HTTP MCP discovery and safe echo calls worked, but `/api/tools?provider=MCP&keyword=http.echo` did not return `http.echo`.
- Root cause: `SeahorseAgentKernelAutoConfiguration` imported Agent before Retrieval, so `McpToolAllowlistRegistrar` could be skipped when `KernelMcpOrchestrator` and `McpToolRegistryPort` were not visible yet.
- Repair:
  - `SeahorseAgentKernelAutoConfiguration` now imports Retrieval before Agent.
  - Agent still imports before Ops to preserve the run experiment executor wiring repaired earlier.
  - `SeahorseAgentAutoConfigurationOrderingTests` asserts the Retrieval-before-Agent ordering.

Focused regressions:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentAutoConfigurationOrderingTests,McpToolAllowlistRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"
```

Result:

```text
Tests run: 6
Failures: 0
Errors: 0
Build: SUCCESS
```

Deployment verification for the temporary backend image:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
```

Result: build success; fresh `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` produced and mounted into the temporary smoke backend.

Cleanup evidence:

```powershell
docker ps -a --format "{{.Names}}" | Select-String "seahorse-mcp-http"
```

Result: no matching temporary containers remained.

This closes the current baseline for MCP HTTP discovery, direct HTTP JSON-RPC compatibility, safe call execution, tool catalog exposure, refresh/restart, stderr-tail management, and failed-server isolation.

## AgentScope Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Fresh result on 2026-06-24: 10 / 10 passed, 0 failed.

Covered:

- Verified the running main backend container has `SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true`.
- Verified the running main backend container has `SEAHORSE_AGENTSCOPE_A2A_ENABLED=false`.
- Logged in as `admin`.
- `GET /api/run-profiles` returned SYSTEM AgentScope profile `-9104` (`AgentScope 观测方案`) with `executorEngine=agentscope`.
- Created a real conversation and applied run profile `-9104`.
- Sent real SSE chat through `/rag/v3/chat`.
- Verified `t_run_context_snapshot` captured `executor_engine=agentscope`, `runProfileId=-9104`, and `roleCardId=-9007`.
- Verified `/a2a` and `/a2a/.well-known/agent-card.json` return 404 in the current configured-off A2A state.
- Created a second real conversation, applied kernel profile `-9101`, sent real SSE chat, and verified `executor_engine=kernel`.

Observed output:

```text
Summary: 10 / 10 passed, 0 failed
AgentScope conversation ID: 327985792133001216
AgentScope run ID: 7vW6rTJ_MtDGoeE2QyWy
AgentScope chat bytes: 1882
Kernel conversation ID: 327985966565715968
Kernel run ID: fwc3p8K5CVnZJtRN1zCk
Kernel chat bytes: 1381
```

Snapshot evidence:

```json
{"runId":"7vW6rTJ_MtDGoeE2QyWy","roleCardId":"-9007","runProfileId":"-9104","executorEngine":"agentscope","runProfileName":"AgentScope 观测方案"}
{"runId":"fwc3p8K5CVnZJtRN1zCk","roleCardId":"-9001","runProfileId":"-9101","executorEngine":"kernel","runProfileName":"默认轻量方案"}
```

This closes the current baseline for AgentScope executor selection, persisted snapshot evidence, configured-off A2A endpoint behavior, and kernel-path failure isolation.

## Role Card Runtime Recheck

The user reported that the page did not appear to offer selectable role cards. I rechecked the running full Docker deployment through API, database, a real browser session, explicit chat, and run context snapshots.

API evidence:

```text
GET http://127.0.0.1:9090/api/role-cards
Status: 200
Code: 0
Count: 7
System cards: -9001 general assistant, -9002 requirement analyst, -9003 code developer, -9004 quality reviewer, -9005 knowledge writer, -9006 data analyst, -9007 AgentScope debugger
SystemCount: 7
EnabledCount: 0
```

Database evidence:

```sql
select id,name,enabled,asset_source,preset_key,published,approval_status,readonly
from sa_role_card
order by id;
```

The 7 SYSTEM preset cards exist with `published=1`, `approval_status=APPROVED`, and `readonly=1`; each currently has `enabled=0`.

Browser evidence from `http://127.0.0.1/chat`:

```text
Role card combobox opened and listed the 7 SYSTEM cards.
Selecting a card changed the combobox value.
```

Browser evidence from `http://127.0.0.1/admin/role-cards`:

```text
The page listed the same 7 SYSTEM cards.
Enable action count: 7
```

Added repeatable explicit chat smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

First real run found a defect:

```text
Summary: 4 / 5 passed, 1 failed
Failure: Snapshot role_card_id expected '-9003' but got ''
Conversation ID: 328154088333537280
Role card ID: -9003
Role card name: code developer
Chat content type: text/event-stream;charset=UTF-8
```

Database evidence from the failed run:

```text
t_run_context_snapshot.role_card_id was null
snapshot_json.roleCardId was null
executor_engine was kernel
```

Root cause:

- `SeahorseChatController` correctly passed `roleCardId=-9003` into `StreamChatCommand`.
- `StreamChatCommand.normalizeRoleCardId()` reused positive-ID normalization.
- SYSTEM preset role cards use negative IDs, so `-9003` was normalized to `null` before the chat kernel could resolve or snapshot it.

Repair:

- `StreamChatCommand.normalizeRoleCardId()` now treats only `0` as blank and preserves negative SYSTEM preset IDs.
- `StreamChatCommandTests` covers preservation of `-9003` and normalization of `0`.

Focused regression:

```powershell
mvnw.cmd -B -pl seahorse-agent-kernel -am "-Dtest=StreamChatCommandTests,KernelChatRoleCardTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"
```

Result:

```text
Tests run: 12
Failures: 0
Errors: 0
Build: SUCCESS
```

Backend package/deploy:

```powershell
mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result: build success; backend health returned `UP`.

Final explicit chat smoke result:

```text
Summary: 5 / 5 passed, 0 failed
Conversation ID: 328155377939410944
Role card ID: -9003
Role card name: code developer
Run ID: _fQ_IzFbXDS2kqID5LLS
Executor engine: kernel
Chat content type: text/event-stream;charset=UTF-8
Chat bytes: 399
```

Snapshot evidence:

```json
{"runId":"_fQ_IzFbXDS2kqID5LLS","roleCardId":"-9003","runProfileId":"","executorEngine":"kernel","roleCardName":"code developer"}
```

Conclusion: built-in role cards exist, are selectable in the real page, and explicit role-card chat now persists the selected SYSTEM role card into `t_run_context_snapshot`. The current runtime state has no SYSTEM role card set as the user's default active card, which explains why the chat input starts on the default role.
## Governance Error-State Real Smoke

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Fresh result on 2026-06-24: 8 / 8 passed, 0 failed.

Covered:

- Bad login returns structured HTTP 400 instead of an unshaped failure.
- Admin login works.
- Normal user `demo_user_001/demo123` login works.
- Admin governance APIs return normal envelopes.
- Empty tool search returns an empty page rather than a broken response.
- Unknown tool returns structured 404 `RESOURCE_NOT_FOUND`.
- Normal user gets structured permission errors for `/api/tools`, `/api/agents`, `/api/approvals`, and `/api/access-decisions`.
- Main backend MCP disabled state returns structured 409 `CONFLICT` / `Service not available`.

Observed output:

```text
Summary: 8 / 8 passed, 0 failed
Admin user ID: 2001523723396308993
Normal user ID: 323451851919118336
```

This closes the current API baseline for governance permission and structured error states. Governance visual no-data/backend-unavailable states remain a possible browser follow-up.

## Governance Page-State Real Browser Smoke

Added repeatable browser smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-governance-page-states-smoke.ps1 -BaseUrl http://127.0.0.1
```

The first real run exposed a page-state defect:

- Admin data state on `/admin/tools` passed with real tool rows.
- Empty-state search initially failed because the script targeted the global knowledge filter instead of the tool search input. The script now targets the real `搜索工具名称` control.
- Normal-user access proved the frontend route guard returns `demo_user_001` from `/admin/tools` to `/workspace` without calling the protected tools API.
- Simulated protected API 409 inside the admin page did not leave `权限不足` visible; the page fell through to `暂无工具`.

Root causes:

- `ToolCatalogPage` treated load failure and legitimate empty result the same in the table area after the transient toast disappeared.
- `getErrorMessage()` did not inspect Axios `response.data.message` before the generic `error.message`.

Repair:

- `frontend/src/pages/admin/tools/ToolCatalogPage.tsx` now keeps a persistent `toolsError` state and renders an inline error panel with a retry button.
- `frontend/src/utils/error.ts` now prefers backend `response.data.message` before falling back to the transport-level message.

Frontend build/deploy:

```powershell
npm run build
docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html/
```

Result:

```text
Vite build: success
Frontend container: updated static bundle
```

Final browser result:

```text
5 / 5 scenarios passed
admin data state: PASS
admin empty state: PASS
normal-user admin route guard: PASS
permission-denied API message visible: PASS, message=权限不足
backend-unavailable message visible: PASS
```

Artifacts:

```text
output/playwright/artifacts/governance-admin-tools-data-state.png
output/playwright/artifacts/governance-admin-tools-empty-state.png
output/playwright/artifacts/governance-normal-user-admin-route-guard.png
output/playwright/artifacts/governance-admin-tools-permission-denied-state.png
output/playwright/artifacts/governance-admin-tools-backend-unavailable-state.png
output/playwright/artifacts/governance-page-states-results.json
```

Related regressions after the frontend redeploy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Summary: 8 / 8 passed, 0 failed
Admin user ID: 2001523723396308993
Normal user ID: 323451851919118336
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_405444226
workspace conversationId=328168106678972416
chat SSE status=200 text/event-stream;charset=UTF-8
```

This closes the visual baseline for governance data, no-data, permission-denied, backend-unavailable, and normal-user route-guard states on the deployed tool catalog page.

## Full-Stack Regression Rerun

After the MCP stdio, AgentScope, governance, and role-card rechecks, I reran the main real-smoke suite against the running full Docker deployment.

Backend smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result: 20 checks, 0 failed.

Fresh evidence:

```text
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328023080586080256
Knowledge document upload/chunk smoke docId=328023081060036608
RAG SSE chat smoke conversationId=smoke-1782273996
RAG trace API smoke traceId=328023090597883904 conversationId=smoke-1782273996
Memory/profile smoke valueText=smoke-profile-1782273996 facts=7
Data export taskId=73
```

Page smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result: passed.

Fresh evidence:

```text
Checked routes: 15
Login redirected to: http://127.0.0.1/workspace
Readiness: mode=enterprise, overall=healthy, checks=13
Workspace taskId=task_098777264
Workspace conversationId=328023426473553920
Final URL: http://127.0.0.1/chat/328023426473553920
Chat SSE status: 200 text/event-stream;charset=UTF-8
Artifacts: D:\code\seahorse-agent\output\playwright\artifacts
```

RAG evaluation smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result: 7 / 7 passed, 0 failed.

Fresh evidence:

```text
KB ID: 328023481343438848
Document ID: 328023481699954688
Dataset ID: 328023495272722432
Cases evaluated: 2/2
recall@k=1.0
precision@k=0.2
MRR=1.0
NDCG=0.46927872602275655
emptyRecallRate=0.0
avgLatency=96.0ms
Strategy templates: vector_only, hybrid_rrf, hybrid_rerank
Cleanup deleted the document and KB.
```

## A2A / Nacos Temporary Pair Real Smoke

The main full-compose backend intentionally keeps A2A disabled, so a direct run of `scripts/agentscope-a2a-e2e.ps1` against `http://127.0.0.1:9090/a2a` would only prove the configured-off 404 path already covered by `scripts/e2e-agentscope-smoke.ps1`.

To verify the live A2A/Nacos path without reconfiguring the main backend or clearing volumes, I started one temporary A2A-enabled main backend on port `9094`, then ran the existing script so it could start its own temporary remote backend on port `9095`.

Temporary main backend:

```text
Container: seahorse-a2a-main-smoke
Image: seahorse-agent-backend:latest
URL: http://127.0.0.1:9094/a2a
Network: existing Docker network from seahorse-nacos
Nacos: seahorse-nacos:8848
PostgreSQL: seahorse-postgres:5432/seahorse
A2A enabled: true
Auth mode: shared-secret
```

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9094/a2a `
  -RemotePort 9095 `
  -MainAgentName seahorse-a2a-main-smoke `
  -SharedSecret seahorse-local-a2a-token `
  -BackendImage seahorse-agent-backend:latest `
  -NacosServer 127.0.0.1:8848 `
  -NacosContainerName seahorse-nacos
```

Fresh result on 2026-06-24: passed.

Observed output:

```text
E2E_AGENT=seahorse-e2e-1782274275
MAIN_CARD_OK=200
MAIN_POST_NO_AUTH=401
MAIN_POST_WRONG_TOKEN=401
MAIN_POST_AUTH=200
REMOTE_CARD_OK=200
REMOTE_POST_NO_AUTH=401
REMOTE_POST_WRONG_TOKEN=401
REMOTE_POST_AUTH=200
REMOTE_DIRECT_OK
NACOS_CONNECTOR_SMOKE_OK
E2E_RESULT=PASS
```

Maven live smoke inside the script:

```text
AgentScopeA2ALiveSmokeTest
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

Cleanup evidence:

```text
docker ps -a --format "{{.Names}}" | Select-String -Pattern "seahorse-a2a"
```

Result: no matching temporary containers remained.

This closes the current live baseline for A2A Agent Card exposure, shared-secret auth rejection/success, remote direct call, and Nacos connector discovery/call in a temporary full-Docker pair.

## OpenAPI Connector Real Docker/API/Page Smoke

The OpenAPI connector path initially failed in the running Docker backend with a structured service-unavailable response because the deployed bootstrap did not instantiate the OpenAPI parser adapter. I added the OpenAPI adapter to the executable bootstrap dependency graph, explicitly imported `OpenApiAdapterAutoConfiguration`, rebuilt, and redeployed the main backend container.

I then found a second real-page issue: the Docker frontend proxies `/api/connectors...` to backend `/connectors...`, while the controller only exposed `/api/connectors...`. The admin page therefore rendered an empty connector list even though the backend and database had imported connectors. I added no-prefix aliases for the OpenAPI connector endpoints and redeployed again.

Target deployment:

```text
Frontend: http://127.0.0.1
Backend: http://127.0.0.1:9090
Backend container: seahorse-backend
Frontend container: seahorse-frontend
Database container: seahorse-postgres
```

Relevant code repairs:

```text
seahorse-agent-bootstrap/pom.xml
seahorse-agent-bootstrap/src/main/java/com/miracle/ai/seahorse/agent/SeahorseAgentApplication.java
seahorse-agent-bootstrap/src/test/java/com/miracle/ai/seahorse/agent/SeahorseAgentBootstrapDependencyTests.java
seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseOpenApiConnectorController.java
seahorse-agent-adapter-web/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentControllerTests.java
frontend/src/services/openApiConnectorService.ts
frontend/src/pages/admin/integrations/OpenApiConnectorPage.tsx
```

Verification commands:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-web,seahorse-agent-bootstrap,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-adapter-openapi -am "-Dtest=SeahorseAgentControllerTests,SeahorseAgentBootstrapDependencyTests,SeahorseAgentRegistryAutoConfigurationTests,OpenApiSpecParserAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"
```

Result:

```text
BUILD SUCCESS
SeahorseAgentControllerTests: 17 tests, 0 failures
SeahorseAgentRegistryAutoConfigurationTests: 2 tests, 0 failures
SeahorseAgentBootstrapDependencyTests: 5 tests, 0 failures
```

Backend package/deploy:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result:

```text
BUILD SUCCESS
Backend health: UP
```

Frontend build/deploy:

```powershell
npm run build
docker cp frontend/dist/. seahorse-frontend:/usr/share/nginx/html/
docker exec seahorse-frontend nginx -s reload
```

Result:

```text
vite build: success
nginx reload: signal process started
```

Final alias-path API smoke against the redeployed backend:

```text
POST http://127.0.0.1:9090/connectors/openapi
GET  http://127.0.0.1:9090/connectors/{connectorId}/operations
POST http://127.0.0.1:9090/connectors/{connectorId}/operations/{getOperationId}/enable
POST http://127.0.0.1:9090/connectors/{connectorId}/operations/{deleteOperationId}/enable
```

Observed result:

```json
{
  "name": "Codex OpenAPI Alias Smoke 1782276608",
  "connectorId": "conn_935f85cd23eb8a7f",
  "importCode": "0",
  "operations": "DELETE:HIGH:DISABLED:True, GET:LOW:DISABLED:False",
  "enabledGet": "GET:ENABLED:openapi_b0172e7ec239f854",
  "deleteWithoutApprovalStatus": 409,
  "aliasListTotal": "2"
}
```

Database verification:

```sql
select c.connector_id, c.name, c.status, count(o.operation_id) as operation_count,
       string_agg(o.method || ':' || o.risk_level || ':' || o.status, ', ' order by o.method) as operations
from sa_connector c
join sa_connector_operation o on o.connector_id = c.connector_id
where c.connector_id = 'conn_935f85cd23eb8a7f'
group by c.connector_id, c.name, c.status;
```

Result:

```text
connector_id: conn_935f85cd23eb8a7f
name: Codex OpenAPI Alias Smoke 1782276608
status: IMPORTED
operation_count: 2
operations: DELETE:HIGH:DISABLED, GET:LOW:ENABLED
```

Real browser page evidence:

```text
Opened http://127.0.0.1/admin/integrations/connectors
The connector list renders imported connector data through the Docker frontend proxy.
Opened detail page for conn_6ac1c4e70cbf7746.
The detail page renders:
- DELETE /pets/{petId} deletePet HIGH disabled
- GET /pets listPets LOW enabled
```

Screenshot:

```text
output/playwright/openapi-connector-detail-current.png
```

Conclusion: OpenAPI connector import, parser adapter wiring, Docker proxy compatibility, operation listing, low-risk tool enablement, OpenAPI tool catalog exposure, high-risk approval enforcement, PostgreSQL persistence, and admin list/detail rendering now have real runtime evidence.

## Post-OpenAPI Redeploy Regression Rerun

After the OpenAPI backend/frontend redeploy, I reran the broad real-smoke suite against the same running full Docker deployment.

Backend smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328035074466803712
Knowledge document upload/chunk smoke docId=328035075200806912
RAG SSE chat smoke conversationId=smoke-1782276855
RAG trace API smoke traceId=328035094515576832 conversationId=smoke-1782276855
Memory/profile smoke valueText=smoke-profile-1782276855 facts=7
Data export taskId=74
```

Page smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_278830258
workspace conversationId=328035156234760192
chat SSE status=200 text/event-stream;charset=UTF-8
artifacts: D:\code\seahorse-agent\output\playwright\artifacts
```

RAG evaluation smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Passed: 7 / 7
Failed: 0 / 7
KB ID: 328035427115495424
Document ID: 328035427392319488
Dataset ID: 328035440721817600
Cases evaluated: 2/2
recall@k=1.0
precision@k=0.2
MRR=1.0
NDCG=0.46927872602275655
emptyRecallRate=0.0
avgLatency=72.0ms
Strategy templates: vector_only, hybrid_rrf, hybrid_rerank
Cleanup deleted the document and KB.
```

Conclusion: the OpenAPI redeploy did not regress the current full-Docker backend, page, chat, RAG trace, memory/profile, or RAG evaluation smoke baseline.

## Post-Role-Card Fix Regression Rerun

After fixing direct chat handling for SYSTEM preset role-card IDs and redeploying the backend, I reran the broad real-smoke suite against the same running full Docker deployment.

Backend smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328157253816709120
Knowledge document upload/chunk smoke docId=328157254227750912
RAG SSE chat smoke conversationId=smoke-1782305986
RAG trace API smoke traceId=328157259462242304 conversationId=smoke-1782305986
Memory/profile smoke valueText=smoke-profile-1782305986 facts=7
Data export taskId=75
```

Page smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_687199270
workspace conversationId=328157871755128832
chat SSE status=200 text/event-stream;charset=UTF-8
artifacts: D:\code\seahorse-agent\output\playwright\artifacts
```

RAG evaluation smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Passed: 7 / 7
Failed: 0 / 7
KB ID: 328158002218954752
Document ID: 328158004278358016
Dataset ID: 328158049794945024
Cases evaluated: 2/2
recall@k=1.0
precision@k=0.2
MRR=1.0
NDCG=0.46927872602275655
emptyRecallRate=0.0
avgLatency=4086.0ms
Strategy templates: vector_only, hybrid_rrf, hybrid_rerank
Cleanup deleted the document and KB.
```

Conclusion: the role-card fix redeploy did not regress the current full-Docker backend, page, chat, RAG trace, memory/profile, or RAG evaluation smoke baseline.

## Ingestion Pipeline Real Docker/API/DB/Page Smoke

The roadmap marked M2 ingestion governance and recoverable pipelines as implemented, but the real Docker runtime still needed API, PostgreSQL, retry, rollback, and page evidence.

Added repeatable API/DB smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-ingestion-pipeline-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Fresh result on 2026-06-24:

```text
Summary: 13 / 13 passed, 0 failed
Marker: CODX_INGESTION_PIPELINE_1782312876619
Success pipeline: 328186154588729344
Success task: 328186154773278720
Failed task: 328186157755428864
Retry task: 328186158007087104
Rollback task: 328186160221679616
Rollback doc: 328186160049713152
```

Covered:

- Login against the running Docker backend.
- Create a success ingestion pipeline.
- Execute a parser/chunker success task.
- Verify success task nodes through API.
- Verify task and node rows in PostgreSQL.
- Create a failing pipeline.
- Execute a task that fails at `indexer`.
- Verify failed node evidence and `INDEXER_FAILED`.
- Retry the failed task from node `3`.
- Verify retry metadata: `retryOfTaskId`, `retryFromNodeId`, `restoredNodeIds`, and `retryCount=1`.
- Create a temporary knowledge base/document through real knowledge APIs.
- Execute a rollback-target ingestion task.
- Roll back the task and verify compensation: the task is rolled back and `t_knowledge_document.deleted=1`.

Added repeatable real browser page smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-ingestion-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Fresh result:

```json
{
  "ok": true,
  "marker": "CODX_INGESTION_PAGE_1782312892003",
  "pipelineId": "328186219164233728",
  "taskId": "328186219227148288",
  "screenshot": "D:\\code\\seahorse-agent\\output\\playwright\\artifacts\\ingestion-page-CODX_INGESTION_PAGE_1782312892003.png"
}
```

The browser smoke seeds a real pipeline/task through the API, logs into the deployed frontend, opens `/admin/ingestion?tab=pipelines` and `/admin/ingestion?tab=tasks`, then verifies the seeded pipeline name and task ID are visible.

Defect found by real Docker smoke:

- Symptom: creating an ingestion task failed with PostgreSQL error `column "pipeline_id" is of type bigint but expression is of type character varying`.
- Root cause: `JdbcIngestionTaskRepositoryAdapter` wrote string IDs and raw JSON strings into BIGINT/JSONB columns.
- Repair: IDs are converted to numeric values before insert, and JSON fields are written through `CAST(? AS JSONB)`.
- Focused regression: `JdbcIngestionTaskRepositoryAdapterTests` now uses BIGINT IDs and JSONB columns closer to the real database schema.

Conclusion: ingestion pipeline creation, task execution, node evidence, controlled failure, retry-from-node metadata, rollback compensation, PostgreSQL persistence, and deployed admin-page rendering now have real full-Docker evidence.

## RAG Strategy Promotion Real Docker/API/Page/DB Smoke

The roadmap marked retrieval evaluation strategy comparison/promotion as implemented, but the real Docker runtime still needed page-click, PostgreSQL, and audit-event evidence.

Added repeatable browser smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-strategy-promotion-smoke.ps1 -BaseUrl http://127.0.0.1
```

The smoke seeds a real knowledge base, uploads and chunks a document, creates a retrieval evaluation dataset, runs strategy comparison, opens the deployed `/admin/rag-evaluation/{kbId}/{datasetId}` page, switches to the strategy comparison tab, clicks the promotion button, and verifies the resulting API/DB/audit state.

Fresh result on 2026-06-24:

```json
{
  "ok": true,
  "marker": "CODX_RAG_PROMOTION_1782315276726",
  "kbId": "328196222944178176",
  "docId": "328196223397163008",
  "datasetId": "328196250622390272",
  "comparisonId": "328196271665213440",
  "winner": "hybrid_rrf",
  "templateRow": "hybrid_rrf|1|1",
  "auditRow": "audit_328196287549042688|RETRIEVAL_STRATEGY_PROMOTED|RETRIEVAL_STRATEGY_TEMPLATE|328196222944178176:hybrid_rrf|...comparisonId 328196271665213440...",
  "screenshot": "D:\\code\\seahorse-agent\\output\\playwright\\artifacts\\rag-strategy-promotion-CODX_RAG_PROMOTION_1782315276726.png"
}
```

Covered:

- Login against the deployed Docker frontend/backend.
- Real knowledge base creation.
- Real document upload and chunking.
- Real retrieval evaluation dataset creation.
- Real strategy comparison with saved comparison history.
- Real browser navigation to the dataset detail page.
- Real promotion click from the deployed UI.
- API verification that `hybrid_rrf` is the recommended template.
- PostgreSQL verification in `t_retrieval_strategy_template`: `hybrid_rrf|1|1`.
- PostgreSQL verification in `sa_audit_event`: `RETRIEVAL_STRATEGY_PROMOTED` linked to `resource_id=328196222944178176:hybrid_rrf` and `comparisonId=328196271665213440`.

Defect 1 found by real Docker smoke:

- Symptom: strategy comparison returned a report, but the comparison list stayed empty in PostgreSQL-backed runtime.
- Root cause: `JdbcRetrievalEvaluationDatasetRepositoryAdapter` bound report JSON as a string for PostgreSQL `report_json JSONB`.
- Repair: the adapter now chooses a database-aware JSON placeholder: PostgreSQL uses `?::jsonb`, H2/test uses `?`.

Defect 2 found by real page promotion:

- Symptom: clicking promotion returned HTTP 500 from the backend.
- Root cause: `JdbcRetrievalStrategyTemplateRepositoryAdapter` bound template options as a string for PostgreSQL `options_json JSONB`.
- Repair: the strategy template adapter now uses the same database-aware JSON placeholder.

Defect 3 found during focused regression:

- Symptom: H2 focused tests read default retrieval options (`finalTopK=5`) after the first PostgreSQL-only cast repair.
- Root cause: H2 test columns were strings; the PostgreSQL cast double-encoded JSON into a quoted JSON string.
- Repair: H2/test writes use a plain `?` placeholder while PostgreSQL keeps `?::jsonb`.

Defect 4 found by real page promotion:

- Symptom: the deployed page sent only `{ strategyKey }` to the promotion API.
- Root cause: the backend expects a complete `RetrievalStrategyTemplatePayload`.
- Repair: `frontend/src/services/ragEvaluationService.ts` and `frontend/src/pages/admin/rag-evaluation/RetrievalDatasetDetailPage.tsx` now send `templateKey`, `displayName`, `description`, `options`, `sortOrder`, and `enabled`.

Focused regression:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"
```

Result:

```text
Tests run: 8
Failures: 0
Errors: 0
Build: SUCCESS
```

Backend package/deploy:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result:

```text
BUILD SUCCESS
Backend health: UP
```

Regression after redeploy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Passed: 7 / 7
Failed: 0 / 7
KB ID: 328196356683755520
Document ID: 328196357812023296
Dataset ID: 328196373037346816
Cases evaluated: 2/2
recall@k=1.0
precision@k=0.2
MRR=1.0
NDCG=0.46927872602275655
emptyRecallRate=0.0
avgLatency=436.5ms
Strategy templates: vector_only, hybrid_rrf, hybrid_rerank
Cleanup deleted the temporary KB/doc.
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Summary: 5 / 5 passed, 0 failed
Conversation ID: 328196357501644800
Role card ID: -9003
Role card name: code developer
Run ID: QSugBDZsdGKQChc0juHb
Executor engine: kernel
Chat content type: text/event-stream;charset=UTF-8
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_747979953
workspace conversationId=328196584254107648
chat SSE status=200 text/event-stream;charset=UTF-8
artifacts: D:\code\seahorse-agent\output\playwright\artifacts
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328197187269193728
Knowledge document upload/chunk smoke docId=328197187772510208
RAG SSE chat smoke conversationId=smoke-1782315506
RAG trace API smoke traceId=328197193464180736 conversationId=smoke-1782315506
Memory/profile smoke valueText=smoke-profile-1782315506 facts=7
Data export taskId=77
```

Conclusion: RAG comparison persistence, deployed-page promotion, recommended-template API/DB state, and audit-event linkage now have real full-Docker evidence.

## Memory Governance Real Docker/API/Page/DB Smoke

The roadmap/status report marked M3 memory quality and user-profile governance infrastructure as implemented, but the previous runtime evidence only covered broad memory/profile smoke. I added a repeatable real smoke for the conflict log, quality snapshot, deployed governance page, and DB resolution path.

Added repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-memory-governance-smoke.ps1 -BaseUrl http://127.0.0.1
```

Fresh result on 2026-06-24:

```json
{
  "ok": true,
  "marker": "CODX_MEMORY_GOVERNANCE_1782317341190",
  "userId": "2001523723396308993",
  "memoryIdA": "codxmgA1782317341241",
  "memoryIdB": "codxmgB1782317341241",
  "conflictId": "mem-conflict-328204881313034240",
  "conflictStatusBeforeResolve": "PENDING",
  "qualitySnapshotId": "mem-quality-328204881338200064",
  "resolvedRow": "mem-conflict-328204881313034240|RESOLVED|keep_a|system",
  "screenshot": "D:\\code\\seahorse-agent\\output\\playwright\\artifacts\\memory-governance-CODX_MEMORY_GOVERNANCE_1782317341190.png"
}
```

Covered:

- Login through the deployed frontend API proxy.
- Seed two real `t_short_term_memory` rows for admin user `2001523723396308993`.
- Use the same `metadata_json.semanticKey=profile:occupation` and different content to create a deterministic semantic-key conflict.
- Run real governance through `POST /api/memories/governance/run?userId=...&assessQuality=true`.
- Verify `GET /api/memories/conflicts?userId=...&status=PENDING` returns the generated conflict.
- Verify the old frontend filter `status=open` does not return the generated conflict.
- Verify a quality snapshot is generated.
- Open the deployed `/admin/memory-governance` page in a real browser.
- Verify the conflict tab renders both seeded memory IDs and `PENDING`.
- Resolve the conflict from the page.
- Verify PostgreSQL row in `t_memory_conflict_log`: `RESOLVED`, `resolution_action=keep_a`, `resolved_by=system`.
- Verify the quality tab renders backend snapshot metrics such as short-term and semantic memory counts.

Defects found by real Docker/page smoke:

- The frontend conflict panel queried `status=open`, but backend conflict rows use `resolution_status=PENDING`.
- The frontend conflict panel expected `conflictId`, `memoryIdA`, `memoryIdB`, and `status`, while the backend returns `id`, `memoryId1`, `memoryId2`, and `resolutionStatus`.
- The frontend resolve call sent `{ resolution, mergedContent }`, while the backend request record is `MemoryConflictResolveRequest(String action)`.
- The frontend quality panel queried without `userId`, so the backend defaulted to `system` instead of the logged-in admin user.
- The frontend quality panel expected flattened fields such as `snapshotId`, `totalMemories`, `highQualityCount`, and `averageQuality`, while the backend returns `{ id, userId, snapshot, createTime }`.

Repair:

- `frontend/src/services/memoryGovernanceService.ts` now types the backend response shape and sends `{ action }` for conflict resolution.
- `frontend/src/pages/admin/memory-governance/components/MemoryConflictPanel.tsx` now queries current-user `PENDING` conflicts and displays `resolutionStatus`, `severity`, `conflictType`, `memoryId1`, and `memoryId2`.
- `frontend/src/pages/admin/memory-governance/components/MemoryQualityPanel.tsx` now queries the current user's snapshots and renders metrics from the nested `snapshot` object.

Verification after repair:

```powershell
npm run build
```

Result: Vite production build succeeded.

```powershell
docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html/
docker exec seahorse-frontend nginx -s reload
```

Result: nginx reload signal accepted.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_208881984
workspace conversationId=328205035927662592
chat SSE status=200 text/event-stream;charset=UTF-8
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328204958135906304
Knowledge document upload/chunk smoke docId=328204958521782272
RAG SSE chat smoke conversationId=smoke-1782317359
RAG trace API smoke traceId=328204967807971328 conversationId=smoke-1782317359
Memory/profile smoke valueText=smoke-profile-1782317359 facts=7
Data export taskId=78
```

Conclusion: M3 memory conflict generation, quality snapshot creation, deployed governance-page visibility, page-triggered conflict resolution, and PostgreSQL resolution state now have real full-Docker evidence.

## D4 Trace Reference Cleanup

The status report still listed `docs/deployment/local-embedding-model-guide.md` as containing a stale `/admin/traces` API reference. I updated the guide so the embedding validation step now points only to the actual trace API/table evidence:

```text
用 `/rag/traces/runs` 或 `t_rag_trace_*` 验证 retrieval 节点。
```

Verification:

```powershell
rg -n "/admin/traces" docs\deployment\local-embedding-model-guide.md
```

Result:

```text
local guide stale trace reference: none
```

I also updated `docs/analysis/roadmap-completion-status-report.md` so D4 no longer lists the already-fixed local embedding guide reference as pending. Other `/admin/traces` mentions were left alone where they refer to the valid frontend page route rather than the API endpoint.

## Agent Rollout Promote Real Docker/API/Page/DB Smoke

The roadmap P2 bucket still called out promote rollout as an existing deployment capability needing real full-flow evidence. I added and ran a repeatable smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-agent-rollout-smoke.ps1 -BaseUrl http://127.0.0.1
```

The smoke covers two paths:

- API path: create a Canary rollout, promote without a production gate, and verify `FAILED` with `GATE_MISSING`.
- Page path: seed a passing production gate, open the deployed `/admin/agents/{agentId}/rollout` page, create Canary, promote to full rollout, then verify page, DB, and audit evidence.

First run exposed a real deployed-page defect:

```text
page.waitForFunction: Timeout 20000ms exceeded
```

Investigation showed:

- Direct backend `POST http://127.0.0.1:9090/api/agents/.../rollouts/canary` returned 200 and created `RUNNING`.
- Deployed frontend proxy `POST http://127.0.0.1/api/agents/.../rollouts/canary` returned 404.
- Direct backend unprefixed `POST http://127.0.0.1:9090/agents/.../rollouts/canary` returned 404 before backend redeploy.

Root cause:

- `frontend/nginx.conf` strips `/api` before proxying to the backend.
- The running backend jar exposed rollout endpoints only under `/api/agents/...`.
- The source already had the needed unprefixed aliases, but the running container still had the old jar.

Backend build and redeploy:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result:

```text
BUILD SUCCESS
Backend health: UP
```

Alias probes after redeploy:

```text
frontend proxy canary endpoint: 200 RUNNING
direct unprefixed backend canary endpoint: 200 RUNNING
```

Fresh rollout smoke result:

```json
{
  "ok": true,
  "marker": "CODX_AGENT_ROLLOUT_1782321569033",
  "failure": {
    "agentId": "rollout-fail-CODX_AGENT_ROLLOUT_1782321569033",
    "versionId": "version-fail-CODX_AGENT_ROLLOUT_1782321569033",
    "rolloutId": "avr_328222617137213440",
    "audit": "AGENT_ROLLOUT_FAILED|AGENT_ROLLOUT|avr_328222617137213440"
  },
  "success": {
    "agentId": "rollout-pass-CODX_AGENT_ROLLOUT_1782321569033",
    "versionId": "version-pass-CODX_AGENT_ROLLOUT_1782321569033",
    "rolloutId": "avr_328222695876882432",
    "gateReportId": "gate_1782321571731",
    "rolloutRow": "PROMOTED||gate_1782321571731",
    "startAudit": "AGENT_ROLLOUT_STARTED|AGENT_ROLLOUT|avr_328222695876882432",
    "promoteAudit": "AGENT_ROLLOUT_PROMOTED|AGENT_ROLLOUT|avr_328222695876882432"
  },
  "screenshot": "D:\\code\\seahorse-agent\\output\\playwright\\artifacts\\agent-rollout-CODX_AGENT_ROLLOUT_1782321569033.png"
}
```

Regression after backend redeploy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328222830312714240
Knowledge document upload/chunk smoke docId=328222834091782144
RAG SSE chat smoke conversationId=smoke-1782321618
RAG trace API smoke traceId=328222925850570752 conversationId=smoke-1782321618
Memory/profile smoke valueText=smoke-profile-1782321618 facts=7
Data export taskId=79
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_654201907
workspace conversationId=328222959727964160
chat SSE status=200 text/event-stream;charset=UTF-8
```

Conclusion: promote rollout now has real deployed-page, API, PostgreSQL, production-gate, and audit-event evidence in the current full Docker runtime.

## S3 Object Storage Switching Real Docker/API/DB/MinIO Smoke

The roadmap M5 adapter-switching bucket needed real evidence that the S3 object storage adapter can replace local storage in a full Docker runtime. I added:

```powershell
scripts\e2e-s3-storage-smoke.ps1
```

The smoke starts a temporary S3-enabled backend on the existing `seahorse-agent_default` Docker network, points it at the already running `seahorse-postgres` and `seahorse-minio`, and exercises the real conversation attachment API.

First runs exposed a real S3-mode wiring defect:

```text
POST /api/conversations/{conversationId}/attachments
409 Service not available
```

Runtime conditions showed:

```text
seahorseS3Client: matched
seahorseS3ObjectStorageAdapter: matched
seahorseJdbcConversationAttachmentRepositoryAdapter: matched
seahorseConversationAttachmentInboundPort: did not find ObjectStoragePort during condition evaluation
seahorseConversationAttachmentContextAssembler: did not find ObjectStoragePort during condition evaluation
```

Root cause:

- The S3 storage adapter bean was registered, but method-level `@ConditionalOnBean(ObjectStoragePort.class)` on services that depend on storage evaluated before the S3 object storage bean was visible.
- Local storage did not reveal this because `SeahorseAgentStorageAdapterAutoConfiguration` provides the local object storage bean earlier.

Repair:

- `SeahorseAgentKernelOpsAutoConfiguration` now runs after storage/S3 configuration and gates the attachment inbound service on the attachment repository only, letting `ObjectStoragePort` resolve at bean creation time.
- `SeahorseAgentKernelChatAutoConfiguration` now runs after S3 storage configuration and applies the same pattern for the attachment context assembler.
- `SeahorseAgentKernelDocumentRefreshAutoConfiguration` now explicitly runs after S3 storage configuration.

Build:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
```

Result:

```text
BUILD SUCCESS
```

Fresh S3 smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-s3-storage-smoke.ps1
```

Result:

```text
Summary: 10 / 10 passed, 0 failed
Marker: CODX_S3_STORAGE_20260625021150519
Conversation ID: 328235789327970304
Attachment ID: attachment-328235796567339008
Storage ref: s3://conversation-attachments/7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp
S3 bucket: conversation-attachments
S3 key: 7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp
```

DB evidence before delete:

```text
attachment-328235796567339008|328235789327970304|2001523723396308993|s3://conversation-attachments/7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp|0
```

MinIO evidence before delete:

```text
Name      : 7faa8e7a7f3e4ba19ed8f6304dc30eb6.tmp
Size      : 53 B
Metadata  :
  Content-Type: text/plain
```

Delete evidence:

```text
deleted=1; minio stat exit=1
```

Conclusion: S3 object storage switching now has real temporary-backend, API, PostgreSQL, and MinIO upload/list/delete evidence against the current full Docker stack.

Main backend redeploy after S3 repair:

```powershell
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
```

Result:

```text
Backend health: UP
```

Backend regression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328237568778203136
Knowledge document upload/chunk smoke docId=328237574780252160
RAG SSE chat smoke conversationId=smoke-1782325131
RAG trace API smoke traceId=328237607990751232 conversationId=smoke-1782325131
Memory/profile smoke valueText=smoke-profile-1782325131 facts=7
Data export taskId=80
```

Page regression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

First run after redeploy saw transient navigation-cancelled `net::ERR_ABORTED` requests, with no console errors, page errors, bad responses, or empty pages. A fresh rerun passed:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_419819737
workspace conversationId=328238006193778688
chat SSE status=200 text/event-stream;charset=UTF-8
```

## 2026-06-25 Pulsar MQ consume-loop real evidence

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-pulsar-mq-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Summary: 9 / 9 passed, 0 failed
Marker: CODX_PULSAR_MQ_20260625023439715
Knowledge base ID: 328241584501321728
Document ID: 328241585365348352
Pulsar topic: persistent://seahorse-agent/ai/knowledge-document-chunk
Pulsar subscription: seahorse-document-chunk-consumer
Pulsar before msgIn/msgOut: 27/27
Pulsar after msgIn/msgOut: 28/28
```

Runtime evidence:

```text
Backend env:
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar
SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650

PostgreSQL:
success|1|1|1

Pulsar:
{"msgInCounter":28,"msgOutCounter":28,"msgBacklog":0,"unackedMessages":0,"lastAckedTimestamp":1782326095967}

Backend log:
Document chunk processing completed: docId=328241585365348352
```

Conclusion: the Pulsar knowledge-document chunk loop now has real full-Docker evidence for publish, consume, ack, DB materialization, and backend processing logs. The first run found only a smoke-script log-reading issue caused by an existing sa-token deprecation warning on stderr; the script was repaired and rerun successfully.

## 2026-06-25 Memory profile facts source-tracing real evidence

The status report still listed the M3 profile detail/source-tracing slice as partially complete. I verified the root cause and closed the runtime gap against the running full Docker deployment.

Runtime issue found:

- `t_user_profile_fact` stores source evidence in `source_ids` JSONB.
- The `ProfileFact` API record did not include source ids, so `JdbcProfileMemoryRepositoryAdapter.mapFact` dropped `source_ids` on read.
- The deployed memory-governance operations panel requested `/memories/profile-facts` without a user id, so it fell back to the backend `system` default instead of the logged-in admin user.

Repairs:

- `ProfileFact` now carries `sourceIds`.
- `JdbcProfileMemoryRepositoryAdapter` parses `source_ids` into `sourceIds`, including the H2/test JSON-string shape and the PostgreSQL JSONB-array shape.
- `MemoryOperationsPanel` passes the logged-in user id for operations/profile-facts/corrections views.
- Added repeatable smoke scripts:
  - `scripts/e2e-memory-profile-facts-smoke.mjs`
  - `scripts/e2e-memory-profile-facts-smoke.ps1`

Local verification:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests#shouldUpsertProfileFactAsStrongFactSource" "-Dsurefire.failIfNoSpecifiedTests=false" test "-Dspotless.check.skip=true"
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend verification:

```powershell
cd frontend
npm run build
```

Result:

```text
vite v5.4.21 building for production...
5648 modules transformed.
built in 48.21s
```

Runtime redeploy:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-bootstrap -am package "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"
docker cp seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar seahorse-backend:/app/app.jar
docker restart seahorse-backend
docker cp frontend\dist\. seahorse-frontend:/usr/share/nginx/html
docker exec seahorse-frontend nginx -s reload
```

Backend health after restart:

```text
UP
```

Full-Docker source-tracing smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-memory-profile-facts-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```json
{
  "ok": true,
  "marker": "CODX_PROFILE_SOURCE_1782328814663",
  "userId": "2001523723396308993",
  "factId": "1782328814817350",
  "slotKey": "codex.profile_source.1782328814817",
  "sourceIds": [
    "memory-snapshot-1782328814817350",
    "conversation-message-1782328814817350"
  ],
  "apiSourceIds": [
    "memory-snapshot-1782328814817350",
    "conversation-message-1782328814817350"
  ],
  "generationId": "codex.profile_source.1782328814817:generation",
  "confidenceLevel": 0.923,
  "version": "3",
  "accessCount": 7
}
```

PostgreSQL evidence:

```text
1782328814817350|codex.profile_source.1782328814817|CODX_PROFILE_SOURCE_1782328814663|0.923|explicit_user_memory|["memory-snapshot-1782328814817350", "conversation-message-1782328814817350"]|codex.profile_source.1782328814817:generation|3|7
```

Browser evidence:

```text
output/playwright/artifacts/memory-profile-facts-CODX_PROFILE_SOURCE_1782328814663.png
```

Post-redeploy backend regression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090
```

Result:

```text
Backend E2E summary: 20 checks, 0 failed
Health: UP
Product mode: ENTERPRISE
Knowledge base CRUD smoke kbId=328253993043521536
Knowledge document upload/chunk smoke docId=328253993861410816
RAG SSE chat smoke conversationId=smoke-1782329050
RAG trace API smoke traceId=328254004200370176 conversationId=smoke-1782329050
Memory/profile smoke valueText=smoke-profile-1782329050 facts=9
Data export taskId=81
```

Post-redeploy page regression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1
```

Result:

```text
checkedRoutes: 15
login currentUrl: http://127.0.0.1/workspace
readiness: mode=enterprise, overall=healthy, checks=13
workspace taskId=task_239464495
workspace conversationId=328254407939878912
chat SSE status=200 text/event-stream;charset=UTF-8
```

Conclusion: M3 profile fact detail/source tracing now has real full-Docker PostgreSQL, API, and deployed-page evidence. The browser flow opens `/admin/memory-governance`, enters the operations/profile-facts view, renders the seeded fact row, expands it, and verifies the source id is visible.
