# System Preset Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add system-owned preset role cards and run plans that are visible to users, traceable by stable preset keys, read-only by default, and safe to upgrade.

**Architecture:** Extend role-card and run-profile records with asset metadata (`asset_source`, `preset_key`, `preset_version`, `readonly`). Repository and service layers preserve the metadata, SQL migrations add Chinese comments, and frontend admin pages display system presets as read-only assets that can still be activated/applied.

**Tech Stack:** Java 21, Spring Boot, JDBC, PostgreSQL/H2 tests, Lombok, React/TypeScript, Vitest.

---

### Task 1: Asset Metadata Schema And Repository Round Trip

**Files:**
- Modify: `resources/database/migrations/V44__system_preset_assets.sql`
- Modify: `resources/database/seahorse_init.sql`
- Modify: `seahorse-agent-adapter-repository-jdbc/src/main/resources/META-INF/seahorse-agent/sql/agent-registry-run-store-postgresql.sql`
- Modify: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/rolecard/RoleCardRecord.java`
- Modify: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/runprofile/RunProfileRecord.java`
- Modify: `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcRoleCardRepositoryAdapter.java`
- Modify: `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcRunProfileRepositoryAdapter.java`
- Test: `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcRoleCardRepositoryAdapterTests.java`
- Test: `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcRunProfileRepositoryAdapterTests.java`

- [ ] Write failing repository tests for asset metadata round trip.
- [ ] Run the two JDBC repository tests and verify failure is caused by missing metadata fields.
- [ ] Add migration/init SQL columns with Chinese comments.
- [ ] Add Lombok-backed fields to record classes and map them in JDBC.
- [ ] Run the two JDBC repository tests and commit.

### Task 2: Readonly Enforcement In Services

**Files:**
- Modify: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/rolecard/KernelRoleCardService.java`
- Modify: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/runprofile/KernelRunProfileService.java`
- Test: `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/rolecard/KernelRoleCardServiceTests.java`
- Test: `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/runprofile/KernelRunProfileServiceTests.java`

- [ ] Write failing tests proving readonly presets cannot be updated or deleted.
- [ ] Implement minimal readonly checks while allowing activation and conversation application.
- [ ] Run kernel service tests and commit.

### Task 3: Built-In Seed Data

**Files:**
- Modify: `resources/database/migrations/V44__system_preset_assets.sql`
- Modify: `resources/database/seahorse_init.sql`
- Modify: `seahorse-agent-adapter-repository-jdbc/src/main/resources/META-INF/seahorse-agent/sql/agent-registry-run-store-postgresql.sql`
- Test: `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcApiXInspiredSchemaAlignmentTests.java`

- [ ] Add idempotent `INSERT ... ON CONFLICT` seed rows for seven role cards and five run plans.
- [ ] Use stable negative IDs or reserved high IDs so run plans can reference preset role cards safely.
- [ ] Assert SQL contains preset keys, asset-source comments, and system seed rows.
- [ ] Run schema alignment tests and commit.

### Task 4: Frontend Readonly Display

**Files:**
- Modify: `frontend/src/services/roleCardService.ts`
- Modify: `frontend/src/services/runProfileService.ts`
- Modify: `frontend/src/pages/admin/role-cards/RoleCardPage.tsx`
- Modify: `frontend/src/pages/admin/run-profiles/RunProfilePage.tsx`
- Test: `frontend/src/pages/admin/role-cards/RoleCardPage.test.tsx`
- Test: `frontend/src/pages/admin/run-profiles/RunProfilePage.test.tsx`

- [ ] Write failing frontend tests for system badges and disabled edit/delete controls.
- [ ] Add metadata fields to service types.
- [ ] Display “系统预设” and disable edit/delete for readonly assets.
- [ ] Run targeted Vitest tests and commit.

