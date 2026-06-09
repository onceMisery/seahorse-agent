# Core E2E verification and repair - Evidence

## Commands

- `mvn -o -nsu -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcPipelineDefinitionRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: pass, 3 tests.
  - Coverage: pipeline create/query/page/update/delete contract, BIGINT-compatible ids, SQL guard for JSONB node fields.

- `mvn -o -nsu -pl seahorse-agent-spring-boot-starter -am "-Dtest=SeahorseAgentMqAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: pass, 2 tests.
  - Coverage: direct MQ reliable publish uses outbox by default, and bypasses outbox when `seahorse-agent.adapters.mq.reliable-outbox-enabled=false`.

- `mvn -o -nsu -pl seahorse-agent-spring-boot-starter -am "-Dtest=SeahorseAgentAiAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: pass, 3 tests.
  - Coverage: mock embedding dimension follows vector dimension, mock streaming model bean is registered, and mock streaming diagnostics expose selected skill context/tool count for E2E.

- `mvn -o -nsu -pl seahorse-agent-bootstrap -am -DskipTests package`
  - Result: pass.
  - Coverage: backend compile/package and `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` generation.

- `powershell -ExecutionPolicy Bypass -File .docker-codex\run-local-e2e.ps1`
  - Result: pass, `E2E_PASSED`.
  - Summary: `.docker-codex/local-e2e-summary.json`.
  - Latest rerun after selected-skill SSE support: pass, `E2E_PASSED`.
  - Passed checks:
    - `auth-login-admin`
    - `ingestion-pipeline-create`
    - `knowledge-base-create`
    - `knowledge-explicit-pipeline-upload-chunk`
    - `knowledge-blank-pipeline-upload-chunk`
    - `skill-create-enable-bind-snapshot`
    - `agent-publish-run-with-skill-snapshot`
    - `chat-agent-selected-skill-sse`

- `powershell -ExecutionPolicy Bypass -File .docker-codex\run-local-e2e.ps1 -UseExistingBackend -BaseUrl http://127.0.0.1:9090`
  - Result: fail against the currently running Docker backend.
  - Summary: `.docker-codex/docker-e2e-summary.json`.
  - Passed checks:
    - `auth-login-admin`
    - `skill-create-enable-bind-snapshot`
    - `agent-publish-run-with-skill-snapshot`
  - Failed checks:
    - `ingestion-pipeline-create`: HTTP 500.
    - `knowledge-base-create`: HTTP 400, `Bucket name should not contain '_'`.
    - Knowledge document upload/chunk checks were skipped by missing `kbId`.
    - `chat-agent-selected-skill-sse`: missing `run_started`, consistent with stale Docker backend lacking the mock streaming/chat-agent fixes.
  - Docker log root-cause evidence for pipeline failure: PostgreSQL rejected string binding for BIGINT pipeline id, `ERROR: column "id" is of type bigint but expression is of type character varying`.
  - Docker log stale-code evidence for blank pipeline events: current container still throws `入库流水线不存在：` from `KernelKnowledgeDocumentChunkHandler`.

- `docker compose -f docker-compose.full.yml build backend frontend` with `DOCKER_CONFIG=D:\code\seahorse-agent\.docker-codex\docker-config`
  - Result: fail.
  - Error: `permission denied while trying to connect to the docker API at npipe:////./pipe/docker_engine`.
  - Follow-up read-only check `docker ps` with the same config also failed with the same pipe permission error.

- `mvn -o -nsu -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=JdbcPipelineDefinitionRepositoryAdapterTests,SeahorseAgentAiAdapterAutoConfigurationTests,SeahorseAgentMqAdapterAutoConfigurationTests,KernelKnowledgeBaseServiceTests,KernelKnowledgeDocumentServiceTests,KernelChatSkillSelectionTests,ChatSelectedSkillResolverTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: pass.
  - Test counts observed: kernel skill selection/resolver 19, JDBC pipeline 3, starter AI/MQ 5, knowledge services 7; no failures.

- `mvn -o -nsu -pl seahorse-agent-tests -am "-Dtest=KernelKnowledgeBaseServiceTests,KernelKnowledgeDocumentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: pass, 7 tests.
  - Coverage: knowledge base service and knowledge document chunk handler including blank pipeline fallback.

- `npm.cmd run build` in `frontend`
  - Result: pass.
  - Notes: Vite reported existing bundle-size/dynamic-import warnings and Browserslist staleness warning.

- Frontend deployed-container smoke:
  - `Invoke-WebRequest http://127.0.0.1/`: pass, returned SPA HTML referencing `/assets/index-jFkQ0s2g.js` and `/assets/index-Des5VDjR.css`.
  - `Invoke-WebRequest` for JS, CSS, and `seahorse-logo.png`: pass, each returned HTTP 200.
  - `Invoke-RestMethod POST http://127.0.0.1/api/auth/login`: pass, Nginx `/api/` reverse proxy reached backend and returned `code=0` with admin token.
  - Chrome headless `--dump-dom http://127.0.0.1/`: pass, React rendered the login page with `SEAHORSE`, `身份验证`, username/password inputs, and `进入系统`.
  - Chrome headless screenshot file output did not materialize in this environment, so no screenshot artifact is claimed.

## E2E Summary

- Local rebuilt jar summary:
  - Pipeline id: `322116503673278464`
  - Knowledge base id: `322116518567251968`
  - Explicit pipeline document: `322116522237267968`, status `success`, `chunkCount=4`, `persistedChunks=4`
  - Blank pipeline document: `322116528591638528`, status `success`, `chunkCount=1`, `persistedChunks=1`
  - Skill: `codex-e2e-skill-20260607205433`
  - Agent: `codex-e2e-agent-20260607205433`
  - API-created run id: `run_322116533364756480`, status initially `RUNNING`
  - Chat agent run id: `run_322116535558377472`, status `SUCCEEDED`, persisted steps `1`

- Existing Docker backend summary:
  - Skill: `codex-e2e-skill-20260607210015`
  - Agent: `codex-e2e-agent-20260607210015`
  - Run id: `run_322117581154205696`, status `RUNNING`
  - Pipeline id, knowledge base id, and document ids were not produced because Docker backend remains stale and failed earlier checks.
  - Chat run id was not produced because the stale Docker backend SSE path did not emit `run_started`.

## Log Evidence

- `.docker-codex/local-e2e-backend.out.log` shows explicit pipeline chunk event received and successfully processed.
- The same log shows blank pipeline event with empty `pipelineId`, then `pipelineId is empty, using built-in default knowledge document pipeline`, then successful processing with `default-knowledge-document`.
- `.docker-codex/local-e2e-backend.err.log` was empty in the passing run.

## Residual Blocker

- Docker redeploy attempt:
  - `powershell -ExecutionPolicy Bypass -File .\redeploy.ps1 all full`
  - First failed because Docker tried to read `C:\Users\miracle\.docker\config.json` and emitted an access warning treated as an error.
  - Retried with `DOCKER_CONFIG=D:\code\seahorse-agent\.docker-codex\docker-config`.
  - Failed with `permission denied while trying to connect to the docker API at npipe:////./pipe/docker_engine`.
  - A later `docker ps` with the same config also failed with the same pipe permission error.

## Evidence Boundary

- Verified with local backend jar on port `18090`, not with refreshed Docker backend on port `9090`.
- Docker containers were not recreated after the permission failure.
- The currently running Docker backend on port `9090` is confirmed stale by E2E failure and Docker log stack traces.
