# Tool Result Spill Production Acceptance Plan

**Goal:** Close P0-A by hardening oversized tool-result metadata and proving the complete governed spill/read path against the real full-Docker stack.

**Architecture:** Keep `LocalToolGatewayPort` as the post-redaction spill boundary, `AgentArtifactRepositoryPort` plus `ObjectStoragePort` as the only persistence owner, and `ToolResultReadToolPortAdapter` as the only scoped read path. Add metadata only; do not add a table, protocol, storage implementation, or fallback owner.

**Tech Stack:** Java 17, Spring Boot, Jackson, JUnit 5, PowerShell, PostgreSQL, Docker Compose, local object storage adapter.

**Baseline/Authority Refs:** `docs/analysis/vcp-production-runtime-adoption-analysis.md`, `KernelToolResultSpillService`, `ToolResultReadToolPortAdapter`, `LocalToolGatewayPort`, and `docker-compose.full.yml`.

**Compatibility Boundary:** Existing pointer fields and persisted `AgentArtifact` fields remain unchanged. New `contentSha256` and `contentType` fields are additive. Text remains UTF-8, the spill threshold remains character-based, and `read_tool_result` keeps its run/tenant/user scope and current error contract.

**Verification:** Focused Maven tests after implementation, followed by a real authenticated tool invocation through the deployed backend, PostgreSQL/object inspection, artifact download, ranged read, scope-denial checks, audit leakage checks, and cleanup.

**ArchitectureReviewRequired:** yes

## Plan Basis

### Facts

- Spill currently runs only after successful tool execution and output redaction.
- The default threshold is 8192 characters; preview is 800 characters; a ranged read is capped at 4096 characters.
- The current full-Docker backend selects `SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local`, mounted at `/app/seahorse-agent-storage`; MinIO is present but is not the active `ObjectStoragePort` owner.
- `sandbox_python` cannot trigger Spill because its stdout is reduced to a 512-character execution summary.
- `web_fetch` can return up to 20000 characters and can fetch the public repository README, which is larger than the Spill threshold.
- A real persisted Agent Run can be created through `POST /api/agents/{agentId}/runs` without invoking a model.

### Assumptions To Verify At Runtime

- The backend container can reach `raw.githubusercontent.com` in the deployed environment.
- The seeded agent definition/version and the non-admin smoke user are available.
- Tool invocation audit exposes only bounded summaries and does not include `storageRef` or full fetched content.

### Ripple Signal Triage

- Owner scope: unchanged.
- Contract scope: additive pointer/provenance JSON fields only.
- Downstream scope: JSON consumers must tolerate additions; no typed DTO change is required.
- Verification scope: kernel service, read adapter, Gateway audit, JDBC artifact row, object storage, authenticated APIs, and scope isolation.

## File Map

- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/KernelToolResultSpillService.java`: centralize MIME, calculate SHA-256 once, and write integrity metadata to pointer and provenance.
- Create `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/KernelToolResultSpillServiceTests.java`: focused spill, UTF-8 byte/hash, provenance, and fail-closed cleanup coverage.
- Create `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/ToolResultReadToolPortAdapterTests.java`: bounded read and run/tenant/user denial coverage.
- Create `scripts/e2e-tool-result-spill-smoke.ps1`: real full-Docker production acceptance path and cleanup.
- Modify `docs/analysis/vcp-production-runtime-adoption-analysis.md`: replace the inaccurate MinIO-specific acceptance wording with the active `ObjectStoragePort` deployment evidence.

## Tasks

### Task 1: Add Integrity Metadata

**Why:** A pointer must identify the exact redacted UTF-8 object so operators can prove that the downloaded content is complete and unchanged.

**Impact/Compatibility:** Add `contentSha256` and `contentType` to pointer JSON; add `contentBytes`, `contentSha256`, and `contentType` to provenance JSON. Existing fields and storage behavior remain stable.

1. Add a single `text/plain; charset=utf-8` constant and a lowercase SHA-256 hex helper.
2. Compute UTF-8 bytes and SHA-256 once before upload.
3. Reuse the MIME constant for upload and `AgentArtifact`.
4. Pass character count, byte count, hash, and MIME to pointer/provenance serialization.
5. Run `mvn -pl seahorse-agent-kernel -am -DskipTests package` and expect `BUILD SUCCESS`.

### Task 2: Add Focused Regression Tests

**Why:** The production E2E proves wiring; focused tests preserve exact metadata and authorization edge cases cheaply.

**Impact/Compatibility:** Test-only additions; no mock replaces the real E2E acceptance gate.

1. Cover below-threshold pass-through and oversized spill.
2. Assert UTF-8 character/byte counts, SHA-256, MIME, preview, saved provenance, and stored bytes.
3. Cover persistence failure with best-effort object cleanup and fail-closed result.
4. Cover bounded reads plus wrong run, tenant, user, and provenance denials.
5. Run `mvn -pl seahorse-agent-kernel -Dtest=KernelToolResultSpillServiceTests,ToolResultReadToolPortAdapterTests test` and expect zero failures.

### Task 3: Add Real Full-Docker E2E

**Why:** Spill is not production-accepted until the deployed Gateway, repository, object storage, API, audit, and authorization path are exercised together.

**Impact/Compatibility:** Adds a smoke script only. It uses existing APIs and removes its artifact object/row, tool audits, run, and conversation fixtures.

1. Authenticate admin and non-admin users and query the real tool catalog.
2. Select a seeded agent/version, create a conversation, and start a persisted API-triggered run.
3. Invoke `web_fetch` against the public repository README with `maxChars=20000`; assert a `tool_result_spill` pointer rather than full content.
4. Assert pointer metadata, `sa_agent_artifact`, Agent Artifact APIs, active local object reference, exact downloaded SHA-256, and content length.
5. Call `read_tool_result` for a middle/end range and compare it with the downloaded content.
6. Prove wrong-run and wrong-user denial through the real Gateway; temporarily alter only the smoke artifact tenant to prove wrong-tenant denial, then restore it.
7. Assert REST and PostgreSQL tool audits contain bounded summaries and no storage reference or full-result sample.
8. Delete the exact local object after validating its normalized `local://agent-artifacts/` reference, then remove marker-owned database fixtures in dependency order.

### Task 4: Deploy, Verify, Review, Commit

**Why:** Acceptance must use the same built artifact and Docker services that operators run.

**Impact/Compatibility:** Recreates the backend container only; persistent service volumes remain intact.

1. Build through proxy `192.168.1.9:7890` using the repository's existing Docker/Maven path.
2. Recreate the backend and wait for `/actuator/health` to become `UP` (the known Pulsar health issue is recorded separately if it remains unrelated).
3. Run `powershell -ExecutionPolicy Bypass -File scripts/e2e-tool-result-spill-smoke.ps1` and require every step to pass.
4. Run `git diff --check`, inspect the exact diff, and perform a P1/P2 review for data leakage, scope bypass, cleanup safety, and contract regressions.
5. Stage only this slice and create a Chinese commit.

## Risks And Rollback

- External GitHub availability can make the E2E inconclusive. The script must fail explicitly before claiming Spill acceptance; it must not synthesize a successful result.
- SHA-256 adds one bounded digest pass over an already materialized result; no additional full-size copy beyond the existing UTF-8 byte array is introduced.
- Direct E2E cleanup is restricted to the exact returned artifact ID, validated local object prefix, marker-owned run/conversation, and matching tool audit rows.
- Rollback is a single commit revert because there is no schema migration or persisted contract removal.

## Retirement

- The proposed `sandbox_python` large-stdout E2E path is retired because runtime summary truncation makes it incapable of covering Spill.
- MinIO-specific acceptance language is retired for the current full-Docker profile; acceptance follows the configured `ObjectStoragePort` implementation.
- No existing owner, fallback, or compatibility adapter is added or retained by this slice.
