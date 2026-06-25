# Completion Audit

Date: 2026-06-25

## Scope

Objective: use `docs/roadmap/architecture-roadmap-and-vision.md` and
`docs/analysis/roadmap-completion-status-report.md` to run real verification
for all developed-but-not-yet-verified capabilities.

This audit treats completion as unproven until each roadmap "real Test Case
gate" item has current runtime evidence. Future/productization roadmap items
are tracked separately and are not counted as developed-but-unverified current
baseline work.

## Current Rerun Evidence

All commands below were run against the local full Docker deployment on
2026-06-25.

| Area | Fresh command | Result |
|---|---|---|
| Full backend baseline | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 20 checks, 0 failed; health `UP`; full compose runtime with Milvus/Redis/Pulsar; RAG trace `328258206536462336`; memory facts `9`; export task `82` |
| Full page baseline | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-page-smoke.ps1 -BaseUrl http://127.0.0.1` | 15 routes; enterprise readiness healthy; workspace task `task_097892061`; chat SSE `200 text/event-stream` |
| Role cards | `scripts\e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 5 / 5 passed; role card `-9003`; run `b66DXW_pV5I7OO6R7Yq_`; snapshot role card name `代码开发助手` |
| Message tree | `scripts\e2e-message-tree-branch-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 7 / 7 passed; fork message `328258560741240832`; DB branch state verified |
| Run profile inheritance | `scripts\e2e-run-profile-inheritance-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 7 / 7 passed; profile `-9105`; role card `-9004`; snapshot `explicitToolAllowlist=true` |
| Run experiments | `scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 9 / 9 passed; experiment `328258758070661120`; trial score/fork/snapshots verified |
| AgentScope executor and disabled A2A isolation | `scripts\e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 10 / 10 passed; AgentScope run `6gLFjQVYRjIbgxq_CA72`; kernel fallback run `U0Hed3NKZwUGC48DJHhc`; main A2A endpoints disabled with 404 |
| A2A/Nacos live path | temporary A2A-enabled main container on `9094` plus `scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9094/a2a -MainAgentName <unique> -RemotePort 9092` | `MAIN_CARD_OK=200`; auth failures `401`; auth success `200`; `REMOTE_DIRECT_OK`; `AgentScopeA2ALiveSmokeTest` 1 test, 0 failures; `NACOS_CONNECTOR_SMOKE_OK`; `E2E_RESULT=PASS` |
| Governance API/error states | `scripts\e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 8 / 8 passed; bad login, permission errors, not-found, empty state, MCP disabled 409 |
| Governance browser states | `scripts\e2e-governance-page-states-smoke.ps1 -BaseUrl http://127.0.0.1` | 5 browser scenarios passed: data, empty, normal-user guard, permission-denied inline message, backend-unavailable inline message |
| RAG evaluation | `scripts\e2e-rag-evaluation-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 7 / 7 passed; 2 / 2 cases; `recall@k=1.0`; `emptyRecallRate=0.0` |
| RAG strategy promotion | `scripts\e2e-rag-strategy-promotion-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; comparison `328259831015567360`; winner `hybrid_rrf`; DB template row `hybrid_rrf|1|1`; audit linked |
| Ingestion pipeline | `scripts\e2e-ingestion-pipeline-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 13 / 13 passed; success, failed retry, and rollback compensation paths verified |
| Ingestion page | `scripts\e2e-ingestion-page-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; pipeline `328259732436840448`; task `328259732604612608`; browser screenshot saved |
| Memory governance | `scripts\e2e-memory-governance-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; conflict `mem-conflict-328259642443853824`; page resolved to `RESOLVED|keep_a|system` |
| Memory profile facts | `scripts\e2e-memory-profile-facts-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; fact `1782330418865713`; API and page showed `sourceIds` |
| MCP stdio | `scripts\e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093` | 8 / 8 passed; temporary backend; `local-echo`; tool catalog `echo` provider `MCP` |
| MCP HTTP | `scripts\e2e-mcp-http-smoke.ps1 -BaseUrl http://127.0.0.1:9096` | 12 / 12 passed; `http-echo READY`; `broken-http FAILED`; catalog `http.echo` |
| OpenAPI connector | `scripts\e2e-openapi-connector-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; connector `conn_f6446f1305a1dd63`; low-risk GET enabled as `openapi_7b29de89c8c2f9be`; high-risk DELETE returned 409; DB row confirmed GET enabled and DELETE disabled; browser list/detail rendered |
| Agent rollout promotion | `scripts\e2e-agent-rollout-smoke.ps1 -BaseUrl http://127.0.0.1` | passed; missing-gate failure audit and successful promoted rollout audit verified |
| S3 object storage switching | `scripts\e2e-s3-storage-smoke.ps1` | 10 / 10 passed; S3 storage ref `s3://conversation-attachments/72604daa37de4887a94cabc101559734.tmp`; DB soft delete and MinIO removal verified |
| Pulsar consume loop | `scripts\e2e-pulsar-mq-smoke.ps1 -BaseUrl http://127.0.0.1:9090` | 9 / 9 passed; Pulsar counters advanced `32/32` to `33/33`; backlog/unacked `0`; backend log matched document `328260875795722240` |
| Smoke contracts | `scripts\verify-smoke-contracts.ps1` | passed for backend smoke, RAG evaluation smoke, and frontend Dockerfile contract |

## Script Stability Fixes From This Audit

- `scripts/e2e-memory-governance-smoke.mjs`
  - Failure found: previous failed smoke rows with id prefix `codxmg` remained active and caused later governance runs to reprocess stale `profile:occupation` memories, hitting `uk_semantic_memory`.
  - Repair: the smoke now marks only prior `codxmg%` short-term memories for the same user as deleted before seeding the next run, and uses a marker-specific semantic key for the new conflict pair.
  - Verification: rerun passed with conflict `mem-conflict-328259642443853824`.
- `scripts/e2e-governance-page-states-smoke.mjs`
  - Failure found: all browser scenarios passed, but one navigation-cancelled `net::ERR_ABORTED` request was treated as a hard failure.
  - Repair: the script still records failed requests, but only non-`net::ERR_ABORTED` request failures block the smoke.
  - Verification: rerun passed all 5 scenarios.
- `scripts/e2e-openapi-connector-smoke.ps1` and `.mjs`
  - Added repeatable OpenAPI connector real smoke covering import, operation risk classification, low-risk enablement, high-risk 409, tool catalog exposure, PostgreSQL state, and real browser list/detail rendering.
  - First run found a script assumption that tool keyword search would contain the marker; repaired to match tool catalog rows by `ownerTeam=connector:{connectorId}`.
  - Verification: rerun passed with connector `conn_f6446f1305a1dd63`.

## Requirement Audit

| Roadmap gate | Required real evidence | Current conclusion |
|---|---|---|
| Message tree and branch conversation | login, real chat, fork, branch switch, refresh-style cursor restore, parent/path DB fields | Proven by `e2e-message-tree-branch-smoke.ps1` and backend/page baseline |
| Role cards | system presets, create/edit/disable baseline, apply to chat/Agent run, prompt/approval/snapshot traceability | Current developed baseline proven by API/DB/browser evidence plus `e2e-role-card-chat-smoke.ps1` and run-profile snapshot inheritance. SYSTEM cards are selectable; `enabled=0` means no default active system card |
| Run profiles | bind role card/executor/model/memory/safety/tool allowlist and inherit in `/chat` | Proven by `e2e-run-profile-inheritance-smoke.ps1` with `t_run_context_snapshot` evidence |
| Run experiments | same conversation/base message, multiple profiles, trial execution, scoring, cost/trace/fork | Proven by `e2e-run-experiment-smoke.ps1` with trial rows, scoring, output messages, and snapshots |
| AgentScope / Nacos A2A | local Nacos/AgentScope config, AgentScope chat, A2A registration/call, trace/failure isolation/off switch | Proven by `e2e-agentscope-smoke.ps1` for executor and off-switch isolation, plus temporary A2A-enabled main/remote run of `agentscope-a2a-e2e.ps1` for Agent Card/auth/remote/Nacos live path |
| MCP stdio / HTTP tools | local stdio and HTTP MCP discovery, parameter extraction/call, failed-server containment, audit/catalog surface | Proven by `e2e-mcp-stdio-smoke.ps1` and `e2e-mcp-http-smoke.ps1` |
| OpenAPI / A2A / built-in tool catalog | import connector/tool catalog binding or controlled failure, credential/high-risk handling, UI and audit evidence | Proven by new `e2e-openapi-connector-smoke.ps1`, A2A live smoke, MCP catalog smokes, governance smokes |
| Governance admin pages | real page access with data, empty, backend unavailable, insufficient permission states; key actions auditable | Proven by `e2e-governance-error-states-smoke.ps1`, `e2e-governance-page-states-smoke.ps1`, rollout/RAG/OpenAPI/memory page smokes |
| Docker/local validation chain | full deployment smoke for login, chat, role cards, run profiles, message tree, AgentScope optional path, MCP stdio | Proven by current full Docker `e2e-backend-smoke.ps1`, `e2e-page-smoke.ps1`, and the feature-specific smokes above |

## Additional Developed Baseline Audit

| Area from status report/P2 bucket | Current evidence | Conclusion |
|---|---|---|
| RAG evaluation and strategy promotion | RAG eval 7 / 7; strategy promotion browser/API/DB/audit smoke | Proven |
| Ingestion governance | pipeline execution/failure/retry/rollback smoke and ingestion page smoke | Proven |
| Memory quality/profile governance | conflict generation/page resolution/quality snapshot and profile source tracing smoke | Proven for current developed governance baseline |
| Agent rollout promotion | missing-gate failure and successful promotion browser/API/DB/audit smoke | Proven |
| S3 adapter switching | temporary S3 backend, PostgreSQL, MinIO upload/list/delete smoke | Proven |
| Pulsar consume loop | main backend, Pulsar counters, PostgreSQL materialization, backend log smoke | Proven |

## Future / Not Current Developed-But-Unverified Scope

The following roadmap items remain future/productization/design-debt items, not
current developed-but-unverified features for this objective:

- Interactive chat-time memory conflict prompt flow from
  `docs/design/interactive-memory-conflict-resolution.md`.
- AgentScope production hardening beyond current executor/A2A baseline:
  Studio/OTEL direct trace integration, production-grade deregistration, and
  long real-model equivalence gates.
- Unified Tool Gateway, unified GateResult, Sandbox Runtime productionization,
  Agent Workbench, MCP/Profile marketplaces, and self-learning loops.

These should stay on the roadmap until implemented and then receive their own
real test gates.

## Audit Decision

Current evidence proves that every currently developed near-term capability
called out by the two source documents has a repeatable real verification path
or fresh runtime evidence. No remaining P0/P1/P2 real-verification gap was found
for the current developed baseline. Future/productization items remain explicitly
out of this completion claim.
