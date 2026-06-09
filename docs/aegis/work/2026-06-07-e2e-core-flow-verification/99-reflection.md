# Core E2E verification and repair - Reflection

## Repair Track

- JDBC pipeline repository now binds pipeline/node identifiers as numeric values to match PostgreSQL BIGINT schema.
- JDBC pipeline node JSON fields now use explicit `CAST(? AS JSONB)` to match PostgreSQL JSONB columns.
- Blank pipeline document chunk events now use a built-in parser -> chunker -> indexer pipeline instead of assuming seeded database pipeline id `1`.
- Knowledge storage now derives S3/MinIO-safe bucket names from vector collection names, so vector collection names can keep underscores while object storage bucket names avoid underscores.
- Local E2E can disable reliable outbox for direct MQ so document chunk events are handled in the current process and are not claimed by another running backend against the same database.
- E2E script now preserves single-record page results as arrays under PowerShell strict mode.
- E2E script can now run against an existing backend with `-UseExistingBackend -BaseUrl ...` without starting or killing a local backend process.

## Retirement Track

- Production default remains reliable outbox enabled.
- `.docker-codex/application-codex-e2e.properties` is the only place that disables reliable outbox and outbox relay for local E2E isolation.
- The earlier dependency on seeded pipeline id `1` for blank document pipeline processing is no longer on the main path.

## Residual Risk

- Docker backend/frontend containers were not successfully redeployed because Docker daemon pipe access was denied in this session.
- Passing E2E used the freshly packaged local backend jar on port `18090`; the existing Docker backend on port `9090` may still be stale until redeploy succeeds.
- Existing Docker backend on port `9090` is confirmed stale: pipeline creation still fails on BIGINT string binding, knowledge-base creation still uses an invalid underscore bucket name, and blank pipeline events still fail with missing ingestion pipeline.
- Frontend build passed, deployed frontend serves SPA/static assets, Nginx `/api` proxy login works, and Chrome headless DOM rendered the login page. Full browser interaction beyond login-page render was not completed because Docker backend remains stale and Playwright package acquisition was blocked.

## Decision

- Core E2E verification and repair for rebuilt local code: complete with evidence.
- Docker redeploy and Docker-backend acceptance: blocked by local Docker Engine pipe permission; retry redeploy when permission is restored.
