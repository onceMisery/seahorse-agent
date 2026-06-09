# Core E2E verification and repair - Checkpoint

- Task ID: 2026-06-07-e2e-core-flow-verification
- Current todo: complete core E2E verification, record residual Docker redeploy blocker.
- Active slice: verification closure.
- Completed todos:
  - Reproduced ingestion pipeline create failure in local E2E.
  - Fixed JDBC pipeline id and node id binding for BIGINT columns.
  - Fixed JDBC node JSON persistence with explicit JSONB casts.
  - Added regression coverage for JDBC pipeline JSONB insert SQL.
  - Fixed blank pipeline document chunking by using a built-in default parser -> chunker -> indexer pipeline.
  - Added local E2E isolation for direct MQ by disabling reliable outbox through configuration.
  - Fixed PowerShell E2E record list array handling under strict mode.
  - Rebuilt backend jar and ran local core E2E against port 18090.
  - Ran relevant backend tests and frontend production build.
  - Extended `.docker-codex/run-local-e2e.ps1` with `-UseExistingBackend -BaseUrl ...` to run the same API E2E against an already-running backend without starting or killing a local jar process.
  - Ran existing Docker backend E2E against `http://127.0.0.1:9090`; auth and skill/agent flows passed, but pipeline creation and knowledge-base creation failed on stale-container behavior.
  - Confirmed frontend container serves the SPA, static assets, and `/api/auth/login` reverse proxy; Chrome headless DOM dump rendered the login page.
  - Added mock `StreamingChatModelPort` auto-configuration for `seahorse-agent.adapters.ai.type=mock`, including deterministic streaming diagnostics for selected skill context.
  - Extended local E2E with `chat-agent-selected-skill-sse`, asserting SSE `run_started`, `step_started`, `done`, persisted agent step, and mock response `skill=<skillName> tools=1`.
  - Reran local jar E2E successfully after the skill/chat extension: explicit pipeline document `chunkCount=4`, blank pipeline/default document `chunkCount=1`, chat agent run `SUCCEEDED` with one persisted `MODEL_TURN` step.
  - Reran existing Docker backend E2E after the skill/chat extension; Docker backend still shows stale behavior: pipeline 500, MinIO bucket underscore error, and no `run_started` for chat agent SSE.
- Evidence refs:
  - `.docker-codex/local-e2e-summary.json`
  - `.docker-codex/docker-e2e-summary.json`
  - `.docker-codex/local-e2e-backend.out.log`
  - Maven and npm command outputs from this session.
- Blocked on:
  - Docker backend/frontend redeploy: `docker ps` and `docker compose -f docker-compose.full.yml build backend frontend` both fail with `permission denied while trying to connect to the docker API at npipe:////./pipe/docker_engine`.
- Next step:
  - If Docker daemon permission is restored, run `docker compose -f docker-compose.full.yml build backend frontend`, then `docker compose -f docker-compose.full.yml up -d --no-deps --force-recreate backend frontend`, then rerun `.docker-codex\run-local-e2e.ps1 -UseExistingBackend -BaseUrl http://127.0.0.1:9090`.

## ResumeStateHint

Resume from the passed E2E summary first. Do not re-debug the earlier JSONB failure unless a fresh run reproduces it.

## DriftCheckDraft

- Original intent served: yes, core knowledge import/chunking, skill binding, and agent run flows were tested and repaired.
- Stop condition served: E2E success evidence exists for the rebuilt local jar, including chat/agent selected skill streaming; Docker redeploy remains an environmental blocker and the currently running Docker backend is confirmed stale.
- Compatibility boundary: changes stayed in JDBC repository binding, knowledge chunk pipeline fallback, MQ local E2E isolation, mock AI E2E support, and E2E script/config.
- New owner/fallback: built-in default knowledge pipeline is now the owner for blank pipeline document chunking; mock AI type now provides a deterministic streaming chat model for local E2E only.
- Retirement track: local E2E direct MQ disables outbox only through `.docker-codex/application-codex-e2e.properties`; production default remains outbox-enabled.
- Decision: continue only if Docker daemon permission returns; otherwise report verified E2E with Docker redeploy blocked.
