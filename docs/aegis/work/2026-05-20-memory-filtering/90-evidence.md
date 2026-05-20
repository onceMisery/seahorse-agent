# EvidenceBundleDraft

## Baseline Evidence

Local Docker query before the fix:

```sql
SELECT user_id, conversation_id, role, content, create_time
FROM t_message
ORDER BY create_time DESC
LIMIT 20;
```

Observed: relevant chat messages were stored with `user_id = default`.

Local Docker query before the fix:

```sql
SELECT user_id, conversation_id, memory_type, content, create_time
FROM t_short_term_memory
WHERE deleted = 0
ORDER BY create_time DESC
LIMIT 20;
```

Observed: no student-profile memory was written for the reported interaction.

## Root Cause Evidence

- Web adapters defaulted to `userId = default` when requests did not explicitly pass `userId`.
- Memory repository beans could be skipped because `ObjectMapper` was used as an early `@ConditionalOnBean` requirement.
- PostgreSQL rejected raw varchar values for JSON/JSONB columns after repositories were enabled.
- The first extractor did not normalize `我 是一名学生，很高兴认识你` into a trusted profile memory.
- Generic fallback answering did not share the same memory prompt formatting path as RAG prompt assembly.

## Target Regression Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelChatInboundServiceTests,DefaultMemoryEnginePortTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `KernelChatInboundServiceTests`: 6 tests run, 0 failures, 0 errors.
- `DefaultMemoryEnginePortTests`: 16 tests run, 0 failures, 0 errors.
- Total: 22 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Covered:

- generic fallback prompt includes loaded memory context.
- `我 是一名学生，很高兴认识你` is normalized and captured as `PROFILE`.
- preference-style user memory still captures.
- question/noise rejection remains in place.
- low-value personal expressions and sensitive explicit memories are rejected.
- memory layer read failures degrade without breaking response flow.

## Phase 2 Capture Policy Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryCapturePolicyTests,DefaultMemoryEnginePortTests,KernelChatInboundServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `KernelChatInboundServiceTests`: 6 tests run, 0 failures, 0 errors.
- `DefaultMemoryEnginePortTests`: 17 tests run, 0 failures, 0 errors.
- `MemoryCapturePolicyTests`: 4 tests run, 0 failures, 0 errors.
- Total: 27 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Covered:

- write-time memory capture uses an explainable candidate extractor and value assessor.
- extraction records signals such as `profile_statement`, `normalized_chinese_whitespace`, and `trimmed_social_tail`.
- sensitive explicit memory is rejected with `sensitive_credential`.
- explicit important preference memory receives high importance/confidence.
- `DefaultMemoryEnginePort` stores policy metadata: `capturePolicyVersion`, `valueScore`, `riskScore`, `captureSignals`, and `captureReasons`.

Additional compile check:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am test-compile "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed: all 78 test sources compiled after removing duplicate test methods restored from stash.

## JDBC Repository Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed:

- `JdbcMemoryRepositoryAdapterTests`: 3 tests run, 0 failures, 0 errors.
- Build result: `BUILD SUCCESS`.

Covered:

- short-term, long-term, and semantic memory repository save/read behavior.
- memory quality snapshot and conflict repository behavior.
- expired/decayed short-term memory scan and deletion.

## Phase 3/4 RED Evidence

Commands:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryGovernanceServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"
```

Exit status: `1`.

Observed:

- `JdbcMemoryRepositoryAdapterTests` failed to compile because `JdbcMemoryQualitySnapshotRepositoryAdapter.save(MemoryQualitySnapshot)` did not exist.
- `JdbcMemoryRepositoryAdapterTests` failed to compile because `JdbcMemoryConflictLogRepositoryAdapter.save(MemoryConflictRecord)` did not exist.

Interpretation: the new tests correctly exposed that quality snapshots and conflict records were read/resolve-only and governance could not close the persistence loop.

## Phase 3/4 Governance Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryGovernanceServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `KernelMemoryGovernanceServiceTests`: 4 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Covered:

- governance still promotes important short-term memory.
- decay still uses the maintenance port and honors dry-run mode.
- `assessQuality=true` persists a quality snapshot with `governancePolicyVersion = memory-governance-v1`.
- same-type memories with the same explicit `semanticKey` and different content create a `SEMANTIC_KEY_CONFLICT` record with `PENDING` status.

## Phase 3/4 JDBC Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed:

- `JdbcMemoryRepositoryAdapterTests`: 3 tests run, 0 failures, 0 errors.
- Build result: `BUILD SUCCESS`.

Covered:

- `MemoryQualitySnapshotRepositoryPort.save(...)` writes `t_memory_quality_snapshot` and `listByUser(...)` reads parsed JSON.
- `MemoryConflictLogRepositoryPort.save(...)` writes `t_memory_conflict_log` using real columns `memory_id_1` and `memory_id_2`.
- `resolve(...)` marks the saved conflict as `RESOLVED`.
- H2 PostgreSQL-mode JSON string wrapping is handled by `JdbcMemorySupport.parseJson(...)`.

## Phase 3/4 Regression Tests

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryCapturePolicyTests,DefaultMemoryEnginePortTests,KernelChatInboundServiceTests,KernelMemoryGovernanceServiceTests,SeahorseWebApiContractTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `SeahorseWebApiContractTests`: 18 tests run, 0 failures, 0 errors.
- `KernelChatInboundServiceTests`: 6 tests run, 0 failures, 0 errors.
- `DefaultMemoryEnginePortTests`: 17 tests run, 0 failures, 0 errors.
- `KernelMemoryGovernanceServiceTests`: 4 tests run, 0 failures, 0 errors.
- `MemoryCapturePolicyTests`: 4 tests run, 0 failures, 0 errors.
- Total: 49 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Additional compile/package command:

```powershell
./mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed: starter and upstream modules packaged successfully. An earlier parallel run of the test command failed while another Maven process was simultaneously writing target directories; the same command passed when rerun serially.

## Docker Build and Restart

Commands:

```powershell
docker compose -f docker-compose.full.yml build backend
docker compose -f docker-compose.full.yml up -d --no-deps backend
```

Exit status: `0` for both commands.

Observed:

- Docker build packaged `seahorse-agent-bootstrap`.
- Reactor result inside image build: `BUILD SUCCESS`.
- `seahorse-backend` was recreated and started on port `9090`.

## Docker E2E: Authentication

Command shape:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/auth/login' `
  -ContentType 'application/json; charset=utf-8' `
  -Body '{"username":"admin","password":"admin"}'
```

Observed:

- `code = 0`
- `data.userId = 2001523723396308993`
- `data.role = admin`
- cookie `Authorization=<token>` was returned.

Interpretation: current local `admin` login maps to business `userId = 2001523723396308993`; runtime correctness is measured against this value rather than the literal username.

## Docker E2E: Memory Write

Request:

```text
GET /rag/v3/chat?question=我%20是一名学生，很高兴认识你&conversationId=codex-memory-e2e-1779213886&chatMode=RAG
```

Observed:

- HTTP status `200`.
- Response stream completed with `event:done`.

Database verification:

```sql
SELECT user_id, conversation_id, memory_type, content, create_time
FROM t_short_term_memory
WHERE deleted = 0
  AND conversation_id = 'codex-memory-e2e-1779213886'
ORDER BY create_time DESC
LIMIT 5;
```

Observed row:

```text
user_id              = 2001523723396308993
conversation_id      = codex-memory-e2e-1779213886
memory_type          = PROFILE
content              = 我是一名学生
create_time          = 2026-05-19 18:04:54.147993 UTC
```

Management API verification with login cookie:

```text
GET /memories?userId=2001523723396308993&layer=short_term&limit=5
```

Observed:

- `code = 0`
- latest record `type = PROFILE`
- latest record `content = 我是一名学生`
- metadata contains `conversationId = codex-memory-e2e-1779213886`, `source = chat_memory_capture`, and `capturePolicy = explicit_user_memory`.

## Docker E2E: Cross-Session Recall

Request:

```text
GET /rag/v3/chat?question=我的职业是什么？&conversationId=codex-memory-read-1779213922&chatMode=RAG
```

Observed response stream:

```text
根据我们的对话历史记录，您提到自己是一名学生。
```

Database verification:

```sql
SELECT user_id, conversation_id, role, content, create_time
FROM t_message
WHERE conversation_id = 'codex-memory-read-1779213922'
ORDER BY create_time ASC;
```

Observed rows:

```text
user_id              = 2001523723396308993
conversation_id      = codex-memory-read-1779213922
role                 = user
content              = 我的职业是什么？

user_id              = 2001523723396308993
conversation_id      = codex-memory-read-1779213922
role                 = assistant
content              = 根据我们的对话历史记录，您提到自己是一名学生。
```

## Docker E2E: Governance Snapshot and Conflict

Commands:

```powershell
docker compose -f docker-compose.full.yml build backend
docker compose -f docker-compose.full.yml up -d --no-deps backend
```

Exit status: `0` for both commands.

Runtime setup:

- Logged in with `admin/admin`.
- Observed `userId = 2001523723396308993`.
- Inserted two short-term `PROFILE` memories for run id `cdxgov113633`, both with `semanticKey = profile:occupation` and different content.
- Called `POST /memories/governance/run?userId=2001523723396308993&reason=cdxgov113633&assessQuality=true`.

Observed API result:

```json
{
  "governanceCode": "0",
  "promoted": 2,
  "errors": []
}
```

Database verification:

```text
latestSnapshot = 2001523723396308993|memory-governance-v1|2
latestConflict = 2001523723396308993|cdxgov113633a|cdxgov113633b|SEMANTIC_KEY_CONFLICT|HIGH|PENDING
```

Management API verification:

```text
GET /memories/conflicts?userId=2001523723396308993&status=PENDING&limit=5
```

Observed:

- `code = 0`
- pending conflict list included one record for the governance run sample.

## Final Fresh Verification

Timestamp: `2026-05-20T12:31-12:34+08:00`.

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryCapturePolicyTests,DefaultMemoryEnginePortTests,KernelChatInboundServiceTests,KernelMemoryGovernanceServiceTests,SeahorseWebApiContractTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `SeahorseWebApiContractTests`: 18 tests run, 0 failures, 0 errors.
- `KernelChatInboundServiceTests`: 6 tests run, 0 failures, 0 errors.
- `DefaultMemoryEnginePortTests`: 17 tests run, 0 failures, 0 errors.
- `KernelMemoryGovernanceServiceTests`: 4 tests run, 0 failures, 0 errors.
- `MemoryCapturePolicyTests`: 4 tests run, 0 failures, 0 errors.
- Total: 49 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit status: `0`.

Observed:

- `JdbcMemoryRepositoryAdapterTests`: 3 tests run, 0 failures, 0 errors.
- Reactor result: `BUILD SUCCESS`.

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed:

- `JdbcMemoryRepositoryAdapterTests`: 3 tests run, 0 failures, 0 errors.
- Build result: `BUILD SUCCESS`.
- A preceding run of this exact command failed after a local edit temporarily restored `@Override` on `save(...)`; the failure showed the standalone module command could resolve an older locally installed kernel interface until the reactor refreshed it. The adapter source was restored to the compatible shape and the command then passed.

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"
```

Exit status: `0`.

Observed:

- `seahorse-agent-spring-boot-starter` and upstream modules packaged successfully.
- Reactor result: `BUILD SUCCESS`.

## Not Covered

- Full repository test suite was not run.
- Full knowledge-base candidate review queue remains out of scope for this slice per `10-intent.md`.
- Metric dashboard UI was not built; persisted governance metrics are exposed through `t_memory_quality_snapshot` and `/memories/quality-snapshots`.
- LLM-based generalized memory extraction remains out of scope.
