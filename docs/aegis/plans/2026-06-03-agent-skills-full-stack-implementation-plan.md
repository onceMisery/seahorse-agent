# Agent Skills Full Stack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aegis:subagent-driven-development` or `aegis:executing-plans` to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Introduce a complete Skill system that lets Seahorse Agent manage reusable prompt-method capabilities, bind them to Agent versions, inject them into runtime safely, and operate them through the admin frontend.

**Architecture:** Skills become a first-class Agent Infra subsystem, not only an admin CRUD page. The canonical owners are kernel skill domain objects, immutable skill revisions, Agent-version skill snapshots, and a runtime composer that injects only the selected versioned skill context into `AgentLoopRequest`.

**Tech Stack:** Java 17, Spring Boot, JDBC/PostgreSQL, JSONB, React 18, TypeScript, Vite, Zustand, Axios, Vitest, Testing Library, Maven.

**Baseline/Authority Refs:**
- `docs/DEERFLOW-SKILL-ANALYSIS.md`
- `docs/aegis/plans/2026-06-02-frontend-backend-gap-remediation-plan.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/definition/AgentVersion.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/AgentLoopRequest.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/KernelAgentLoop.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/registry/KernelAgentDefinitionService.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentDefinitionController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseToolCatalogController.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentDefinitionRepositoryAdapter.java`
- `resources/database/seahorse_init.sql`
- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/services/agentDefinitionService.ts`
- `frontend/src/pages/admin/agents/AgentEditorPage.tsx`

**Compatibility Boundary:**
- Do not change existing Agent definitions, Agent runs, tool binding behavior, or ToolCatalog policy semantics.
- Global Skill enablement must not mutate already published Agent behavior.
- Runtime must use only the skill revision snapshot bound to the Agent version.
- Skill-declared tools are advisory dependency metadata and never expand `allowedToolIds`.
- PUBLIC skills are read-only through the API. CUSTOM skills can be edited, deleted, and rolled back.
- Custom skill edits always create immutable revisions. Runtime never reads mutable draft content.
- Existing `toolSetJson`, `modelConfigJson`, `memoryConfigJson`, and `guardrailConfigJson` keep their current meaning.
- API responses must not expose secrets, raw scanner internals that include sensitive content, or hidden installation paths.
- Frontend access must be controlled by backend `/api/features`, not build-time assumptions.

**Verification:**
- Backend focused tests:
  ```powershell
  .\mvnw.cmd -pl seahorse-agent-kernel -am -DskipTests test-compile
  .\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am test -Dtest=JdbcAgentSkillRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests
  .\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseSkillControllerTests,SeahorseAgentDefinitionControllerTests,SeahorseFeatureControllerTests
  .\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=*Skill*
  ```
- Frontend focused tests:
  ```powershell
  cd frontend
  npm test -- skill agentDefinition featureService frontendCapabilityContracts
  npm run build
  ```
- Contract and deployment checks:
  ```powershell
  cd frontend
  npm test -- frontendCapabilityContracts
  cd ..
  .\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
  docker compose -f docker-compose.full.yml config
  ```

---

## Plan Basis

### Facts

- `AgentVersion` currently snapshots instructions, tools, model config, memory config, and guardrail config, but has no Skill snapshot.
- `KernelAgentLoop` builds runtime messages from `AgentLoopRequest.history()`, `ContextPack`, `MemoryContext`, and the user question.
- Tool execution is constrained by `allowedToolIds` and Tool Gateway policy. Skill must not bypass that boundary.
- Frontend already has Agent management, ToolCatalog management, Plugin management, route guards, and backend feature-state loading.
- The gap remediation plan contains duplicated Skill sections. Those sections should be treated as reference material, not as the implementation owner.

### Assumptions

- The full Skill system is a new Agent Infra capability and deserves an independent feature flag named `SKILL_MANAGEMENT`.
- Runtime Skill behavior should be deterministic per published Agent version.
- The first production implementation should support metadata injection and full-body runtime injection. Progressive load through a `load_skill` tool is included as a later task after the immutable revision model exists.
- Built-in Skill content should live in repository resources and be imported idempotently at startup.

### First-Principles Decision Review

**First Principle:** Skills must reliably change Agent runtime behavior through versioned prompt-method context.

**Non-negotiables:** Version determinism, immutable revisions, no tool-permission expansion, audited edits, feature-gated UI, and no secret exposure.

**Assumptions to Drop:** A global enabled flag is enough, a CRUD page is sufficient, and Skill `allowedTools` can grant tool access.

**Smallest Sufficient Path:** Create Skill revisions, bind selected revisions to Agent versions, compose bounded Skill runtime context, and expose management/binding UI.

**Escalation Signal:** If implementation needs Skill to execute arbitrary code or install executable resources, open a separate security ADR before shipping.

### Owner / Retirement Matrix

- New canonical owner for Skill metadata: `AgentSkill` and `AgentSkillRevision`.
- New canonical owner for published Agent runtime Skill behavior: `AgentVersion.skillSetJson`.
- New canonical owner for prompt injection: `SkillRuntimeComposer`.
- Old owner to avoid: global enablement state as runtime truth.
- Compatibility carrier: `AgentVersion.EMPTY_JSON_OBJECT` for missing `skillSetJson` on existing versions.
- Retirement trigger: once all published versions include `skillSetJson`, compatibility default remains harmless and can stay as a stable empty snapshot.

---

## File Map

### Backend Domain and Ports

- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkill.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkillRevision.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkillBinding.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkillCategory.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkillStatus.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/AgentSkillSource.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/SkillScanDecision.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/SkillScanResult.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/SkillRuntimeBlock.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/skill/SkillSetSnapshot.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/skill/AgentSkillManagementInboundPort.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/skill/AgentSkillBindingInboundPort.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentSkillRepositoryPort.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/definition/AgentVersion.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentVersionPublishCommand.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/AgentLoopRequest.java`

### Backend Application Services

- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillMarkdownParser.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillSecurityScanner.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/KernelAgentSkillManagementService.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/KernelAgentSkillBindingService.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillRuntimeComposer.java`
- Create `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillSetJsonSupport.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/registry/KernelAgentDefinitionService.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/KernelAgentLoop.java`

### Backend JDBC and Bootstrap

- Create `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentSkillRepositoryAdapter.java`
- Modify `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentDefinitionRepositoryAdapter.java`
- Modify `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcChatSchemaUpgrade.java`
- Modify `resources/database/seahorse_init.sql`
- Create `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentSkillAutoConfiguration.java`
- Add classpath resources under `seahorse-agent-bootstrap/src/main/resources/skills/public/`.

### Backend Web

- Create `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSkillController.java`
- Create request records in `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/`:
  - `SkillCreateRequest.java`
  - `SkillUpdateRequest.java`
  - `SkillEnableRequest.java`
  - `SkillInstallRequest.java`
  - `SkillRollbackRequest.java`
  - `AgentSkillBindingReplaceRequest.java`
- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AgentVersionPublishRequest.java`
- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentDefinitionController.java`

### Frontend

- Create `frontend/src/services/skillService.ts`
- Create `frontend/src/pages/admin/skills/SkillManagementPage.tsx`
- Create `frontend/src/pages/admin/skills/components/SkillDetailDrawer.tsx`
- Create `frontend/src/pages/admin/skills/components/SkillEditorDialog.tsx`
- Create `frontend/src/pages/admin/skills/components/SkillHistoryDialog.tsx`
- Create `frontend/src/pages/admin/skills/components/SkillInstallDialog.tsx`
- Create `frontend/src/pages/admin/skills/components/SkillWizardDialog.tsx`
- Create `frontend/src/pages/admin/agents/components/AgentSkillBindingPanel.tsx`
- Modify `frontend/src/config/productMode.ts`
- Modify `frontend/src/router.tsx`
- Modify `frontend/src/pages/admin/AdminLayout.tsx`
- Modify `frontend/src/services/agentDefinitionService.ts`
- Modify `frontend/src/pages/admin/agents/AgentEditorPage.tsx`
- Modify `frontend/src/pages/admin/agents/components/AgentPublishDialog.tsx`
- Modify `frontend/src/services/frontendCapabilityContracts.test.ts`

### Tests

- Create backend tests:
  - `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillMarkdownParserTests.java`
  - `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillSecurityScannerTests.java`
  - `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillRuntimeComposerTests.java`
  - `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentSkillRepositoryAdapterTests.java`
  - `seahorse-agent-adapter-web/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSkillControllerTests.java`
  - `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/SkillRuntimeIntegrationTests.java`
- Create frontend tests:
  - `frontend/src/services/skillService.test.ts`
  - `frontend/src/pages/admin/skills/SkillManagementPage.test.tsx`
  - `frontend/src/pages/admin/agents/components/AgentSkillBindingPanel.test.tsx`

---

## Data Contracts

### Skill Markdown Format

```markdown
---
name: deep-research
description: Use this skill when the agent must perform structured web research with source validation.
license: Apache-2.0
allowed_tools:
  - web_search
  - web_fetch
tags:
  - research
  - citation
---

# Deep Research

## When To Use

Use this when the user asks for current, sourced, or comparative research.

## Method

1. Map the topic.
2. Gather diverse sources.
3. Verify claims.
4. Synthesize with citations.
```

### Skill Summary Response

```json
{
  "name": "deep-research",
  "description": "Use this skill when the agent must perform structured web research with source validation.",
  "category": "PUBLIC",
  "source": "BUILT_IN",
  "status": "ACTIVE",
  "enabled": true,
  "latestRevisionId": "skillrev_deep_research_1",
  "latestContentHash": "sha256:...",
  "allowedTools": ["web_search", "web_fetch"],
  "tags": ["research", "citation"],
  "createdAt": "2026-06-03T00:00:00Z",
  "updatedAt": "2026-06-03T00:00:00Z"
}
```

### Agent Version Skill Snapshot

Store in `AgentVersion.skillSetJson`:

```json
{
  "version": 1,
  "mode": "BOUND_REVISIONS",
  "skills": [
    {
      "name": "deep-research",
      "revisionId": "skillrev_deep_research_1",
      "contentHash": "sha256:...",
      "description": "Use this skill when the agent must perform structured web research with source validation.",
      "category": "PUBLIC",
      "injectMode": "METADATA_AND_BODY",
      "allowedTools": ["web_search", "web_fetch"]
    }
  ]
}
```

### Runtime Prompt Section

Injected before the user question and after memory/context weaving:

```text
<skills>
The following skills are selected for this Agent version. Use them only when relevant.

<skill name="deep-research" revision="skillrev_deep_research_1">
Description: Use this skill when the agent must perform structured web research with source validation.
Advisory tools: web_search, web_fetch
Instructions:
...
</skill>
</skills>
```

---

## API Contract

### Skill Management

| Method | Path | Feature | Purpose |
| --- | --- | --- | --- |
| GET | `/api/skills` | `SKILL_MANAGEMENT` | Page skills |
| GET | `/api/skills/{name}` | `SKILL_MANAGEMENT` | Get metadata and latest safe detail |
| POST | `/api/skills/custom` | `SKILL_MANAGEMENT` | Create CUSTOM skill |
| PUT | `/api/skills/custom/{name}` | `SKILL_MANAGEMENT` | Edit CUSTOM skill and create revision |
| POST | `/api/skills/{name}/enable` | `SKILL_MANAGEMENT` | Enable skill for tenant |
| POST | `/api/skills/{name}/disable` | `SKILL_MANAGEMENT` | Disable skill for tenant |
| DELETE | `/api/skills/custom/{name}` | `SKILL_MANAGEMENT` | Soft delete CUSTOM skill |
| GET | `/api/skills/custom/{name}/history` | `SKILL_MANAGEMENT` | List revisions |
| POST | `/api/skills/custom/{name}/rollback` | `SKILL_MANAGEMENT` | Create new revision from old revision |
| POST | `/api/skills/install` | `SKILL_MANAGEMENT` | Install `.skill` archive |

### Agent Binding

| Method | Path | Feature | Purpose |
| --- | --- | --- | --- |
| GET | `/api/agents/{agentId}/skills` | `AGENT_DEFINITION_MANAGEMENT` and `SKILL_MANAGEMENT` | Read draft skill bindings |
| PUT | `/api/agents/{agentId}/skills` | `AGENT_DEFINITION_MANAGEMENT` and `SKILL_MANAGEMENT` | Replace draft skill bindings |
| POST | `/api/agents/{agentId}/publish` | `AGENT_DEFINITION_MANAGEMENT` | Publish version with `skillSetJson` |

---

## Implementation Tasks

### Task 1: Add Skill Feature Gate and API Contract Baseline

**Files:**
- Modify: `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- Modify: `frontend/src/config/productMode.ts`
- Modify: `frontend/src/services/frontendCapabilityContracts.test.ts`
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`

**Why:** The admin surface must degrade from backend capability truth before any API or page appears.

**Impact/Compatibility:** Adds `SKILL_MANAGEMENT` without changing existing feature flags.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
cd frontend
npm test -- featureService frontendCapabilityContracts
```

- [ ] Write RED tests proving `/admin/skills` is absent when `SKILL_MANAGEMENT` is disabled and visible when enabled.
- [ ] Add `SKILL_MANAGEMENT` to backend `AdvancedFeature`.
- [ ] Add `SKILL_MANAGEMENT` to frontend product mode constants and route/menu guards.
- [ ] Verify RED becomes GREEN with the focused backend and frontend commands above.
- [ ] Commit: `feat: add skill management feature gate`

### Task 2: Create Skill Domain Model, Parser, and Security Scanner

**Files:**
- Create all files listed in Backend Domain and Ports for the skill domain.
- Create `SkillMarkdownParser.java`
- Create `SkillSecurityScanner.java`
- Test: `SkillMarkdownParserTests.java`
- Test: `SkillSecurityScannerTests.java`

**Why:** Skill content must have a stable, validated structure before it can be stored, displayed, or injected.

**Impact/Compatibility:** New domain only. Existing Agent code is unchanged.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=SkillMarkdownParserTests,SkillSecurityScannerTests
```

- [ ] Write parser RED tests for valid frontmatter, missing name, missing description, duplicate `allowed_tools`, and body extraction.
- [ ] Write scanner RED tests for archive-size limits, content-size limits, forbidden path markers, secret-like values, and dangerous command phrases.
- [ ] Implement immutable records and enums under `domain/agent/skill`.
- [ ] Implement parser with explicit validation errors and normalized skill names.
- [ ] Implement scanner returning `SkillScanResult` with `ALLOW`, `WARN`, or `BLOCK`.
- [ ] Verify focused kernel tests pass.
- [ ] Commit: `feat: add agent skill domain validation`

### Task 3: Add JDBC Schema and Repository Adapter

**Files:**
- Modify: `resources/database/seahorse_init.sql`
- Modify: `JdbcChatSchemaUpgrade.java`
- Create: `JdbcAgentSkillRepositoryAdapter.java`
- Create: `AgentSkillRepositoryPort.java`
- Test: `JdbcAgentSkillRepositoryAdapterTests.java`
- Modify: `JdbcInitSqlSchemaAlignmentTests.java`

**Why:** Skill revisions and enablement must be durable, queryable, and compatible with existing Docker volumes.

**Impact/Compatibility:** Adds new tables and one optional column to `sa_agent_version`.

**Schema:**

```sql
CREATE TABLE IF NOT EXISTS sa_agent_skill (
  pk_id BIGSERIAL PRIMARY KEY,
  skill_name VARCHAR(128) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  category VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  latest_revision_id VARCHAR(128),
  description TEXT NOT NULL,
  tags_json JSONB NOT NULL DEFAULT '[]',
  allowed_tools_json JSONB NOT NULL DEFAULT '[]',
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sa_agent_skill_revision (
  pk_id BIGSERIAL PRIMARY KEY,
  revision_id VARCHAR(128) NOT NULL UNIQUE,
  skill_name VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  revision_no BIGINT NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  frontmatter_json JSONB NOT NULL,
  scan_decision VARCHAR(32) NOT NULL,
  scan_result_json JSONB NOT NULL,
  created_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sa_agent_skill_binding (
  pk_id BIGSERIAL PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  skill_name VARCHAR(128) NOT NULL,
  revision_id VARCHAR(128) NOT NULL,
  inject_mode VARCHAR(32) NOT NULL DEFAULT 'METADATA_AND_BODY',
  created_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0
);
```

Also add:

```sql
ALTER TABLE sa_agent_version ADD COLUMN skill_set_json JSONB NOT NULL DEFAULT '{}';
```

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am test -Dtest=JdbcAgentSkillRepositoryAdapterTests,JdbcInitSqlSchemaAlignmentTests
```

- [ ] Write RED tests that save a skill, append revisions, replace bindings, page skills, and load a revision by id.
- [ ] Add init SQL tables, indexes, and `skill_set_json`.
- [ ] Add idempotent schema upgrade for existing deployments.
- [ ] Implement repository adapter with no raw SQL string interpolation from user input.
- [ ] Verify repository and init alignment tests pass.
- [ ] Commit: `feat: persist agent skills and revisions`

### Task 4: Implement Skill Management Service and REST API

**Files:**
- Create: `KernelAgentSkillManagementService.java`
- Create: `AgentSkillManagementInboundPort.java`
- Create: `SeahorseSkillController.java`
- Create request records listed in Backend Web.
- Test: `SeahorseSkillControllerTests.java`

**Why:** Admins need controlled lifecycle APIs for PUBLIC and CUSTOM skills.

**Impact/Compatibility:** New API namespace `/api/skills`; no existing route changes.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseSkillControllerTests
```

- [ ] Write RED controller tests for list, detail, create custom, edit custom, enable, disable, delete, history, rollback, and PUBLIC edit rejection.
- [ ] Implement service operations with `admin` role checks matching existing Agent management.
- [ ] Ensure every edit creates a new revision and updates `latest_revision_id`.
- [ ] Ensure `BLOCK` scan results reject create, edit, install, and rollback.
- [ ] Ensure responses omit raw hidden paths and secrets.
- [ ] Verify focused web tests pass.
- [ ] Commit: `feat: expose skill management api`

### Task 5: Add Agent Skill Binding and Version Snapshot

**Files:**
- Create: `KernelAgentSkillBindingService.java`
- Create: `AgentSkillBindingInboundPort.java`
- Create: `SkillSetJsonSupport.java`
- Modify: `AgentVersion.java`
- Modify: `AgentVersionPublishCommand.java`
- Modify: `AgentVersionPublishRequest.java`
- Modify: `KernelAgentDefinitionService.java`
- Modify: `SeahorseAgentDefinitionController.java`
- Modify: `JdbcAgentDefinitionRepositoryAdapter.java`
- Test: `SeahorseAgentDefinitionControllerTests.java`
- Test: `JdbcAgentDefinitionRepositoryAdapterTests.java`

**Why:** Published Agent versions must snapshot exact Skill revisions.

**Impact/Compatibility:** Existing versions read missing `skill_set_json` as `{}`.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am test -Dtest=JdbcAgentDefinitionRepositoryAdapterTests
.\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseAgentDefinitionControllerTests
```

- [ ] Write RED tests proving publish persists `skillSetJson`.
- [ ] Write RED tests proving missing `skill_set_json` loads as `{}` for compatibility.
- [ ] Add `skillSetJson` to `AgentVersion` with default JSON object behavior.
- [ ] Add `skillSetJson` to publish command and request records.
- [ ] Add binding read and replace endpoints under `/api/agents/{agentId}/skills`.
- [ ] Verify publish response includes the persisted skill snapshot.
- [ ] Commit: `feat: snapshot skills on agent publish`

### Task 6: Inject Skill Runtime Context into Agent Loop

**Files:**
- Modify: `AgentLoopRequest.java`
- Create: `SkillRuntimeComposer.java`
- Modify: `KernelAgentLoop.java`
- Test: `SkillRuntimeComposerTests.java`
- Test: `SkillRuntimeIntegrationTests.java`

**Why:** Skills must affect actual Agent behavior through bounded runtime prompt context.

**Impact/Compatibility:** Empty skill context is behaviorally identical to current runtime.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=SkillRuntimeComposerTests
.\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=SkillRuntimeIntegrationTests
```

- [ ] Write RED tests proving empty skills do not change messages.
- [ ] Write RED tests proving selected revisions appear in the system prompt under `<skills>`.
- [ ] Write RED tests proving Skill advisory tools do not expand `allowedToolIds`.
- [ ] Add `List<SkillRuntimeBlock>` or `String skillRuntimeContext` to `AgentLoopRequest`.
- [ ] Compose skill context after memory/context and before the user question.
- [ ] Truncate skill body by a deterministic per-skill and total budget.
- [ ] Verify runtime tests pass.
- [ ] Commit: `feat: inject versioned skills into agent runtime`

### Task 7: Add Progressive Skill Loading Tool

**Files:**
- Create: `SkillRuntimeLoaderToolPortAdapter.java`
- Modify: `SeahorseAgentKernelAgentAutoConfiguration.java`
- Modify: `ToolCatalog` startup registration if local built-in tools are registered there.
- Test: `SkillRuntimeIntegrationTests.java`

**Why:** Full Skill support should allow metadata-first prompt loading while the model can request full content for a selected skill.

**Impact/Compatibility:** The tool can only load skills already included in the Agent version snapshot.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=SkillRuntimeIntegrationTests
```

- [ ] Write RED tests proving `load_skill` rejects unbound skill names.
- [ ] Write RED tests proving `load_skill` returns the exact revision content for a bound skill.
- [ ] Implement local tool descriptor `load_skill` with low risk and no external side effects.
- [ ] Ensure tool invocation observes existing `allowedToolIds`; Agent versions that do not allow `load_skill` cannot call it.
- [ ] Register `load_skill` in ToolCatalog seed data or auto-configuration with disabled-by-default if existing policy requires explicit enablement.
- [ ] Verify focused integration tests pass.
- [ ] Commit: `feat: add progressive skill loading tool`

### Task 8: Seed Built-In Public Skill Library

**Files:**
- Create under `seahorse-agent-bootstrap/src/main/resources/skills/public/`:
  - `deep-research/SKILL.md`
  - `data-analysis/SKILL.md`
  - `code-review/SKILL.md`
  - `document-generation/SKILL.md`
  - `test-generation/SKILL.md`
  - `architecture-design/SKILL.md`
- Create startup importer in `SeahorseAgentSkillAutoConfiguration.java` or a dedicated `BuiltInSkillBootstrapService.java`.
- Test: `SkillBuiltInBootstrapTests.java`

**Why:** The product needs a usable default library, not an empty framework.

**Impact/Compatibility:** Built-ins are imported idempotently and cannot be edited through CUSTOM APIs.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am test -Dtest=SkillBuiltInBootstrapTests
```

- [ ] Write RED tests proving six built-ins are imported once.
- [ ] Write RED tests proving changed resource content creates a new PUBLIC revision without deleting old revisions.
- [ ] Add six `SKILL.md` files with valid frontmatter and scanner-safe content.
- [ ] Implement idempotent resource importer keyed by name and content hash.
- [ ] Verify bootstrap tests pass.
- [ ] Commit: `feat: seed built in agent skills`

### Task 9: Build Frontend Skill Management Page

**Files:**
- Create: `frontend/src/services/skillService.ts`
- Create: `SkillManagementPage.tsx`
- Create: `SkillDetailDrawer.tsx`
- Create: `SkillEditorDialog.tsx`
- Create: `SkillHistoryDialog.tsx`
- Create: `SkillInstallDialog.tsx`
- Modify: `router.tsx`
- Modify: `AdminLayout.tsx`
- Test: `skillService.test.ts`
- Test: `SkillManagementPage.test.tsx`

**Why:** Admins need a visible operating surface for Skill lifecycle management.

**Impact/Compatibility:** New `/admin/skills` route only.

**Verification:**
```powershell
cd frontend
npm test -- skillService SkillManagementPage
npm run build
```

- [ ] Write RED service tests for all Skill API calls.
- [ ] Write RED page tests for list, filter, detail, enable/disable, edit CUSTOM, PUBLIC edit rejection, history, rollback, and install dialog.
- [ ] Implement Skill service with typed request and response models.
- [ ] Implement management page using existing admin layout styles and feature guard.
- [ ] Ensure all Chinese UI text is valid UTF-8 and not mojibake.
- [ ] Verify focused frontend tests and build pass.
- [ ] Commit: `feat: add skill management frontend`

### Task 10: Add Agent Skill Binding UI and Publish Integration

**Files:**
- Create: `AgentSkillBindingPanel.tsx`
- Modify: `agentDefinitionService.ts`
- Modify: `AgentEditorPage.tsx`
- Modify: `AgentPublishDialog.tsx`
- Test: `AgentSkillBindingPanel.test.tsx`

**Why:** Skills must be selected for an Agent draft and snapshotted during publish.

**Impact/Compatibility:** Existing publish flow works with empty skill binding.

**Verification:**
```powershell
cd frontend
npm test -- AgentSkillBindingPanel agentDefinitionService
npm run build
```

- [ ] Write RED tests proving selected skills are saved to `/api/agents/{agentId}/skills`.
- [ ] Write RED tests proving publish payload includes `skillSetJson`.
- [ ] Implement binding panel with search, category filter, selected list, revision indicator, and inject mode selector.
- [ ] Add a Skills tab to Agent editor.
- [ ] Add publish confirmation display of selected Skill names and revisions.
- [ ] Verify focused frontend tests and build pass.
- [ ] Commit: `feat: bind skills to agent drafts`

### Task 11: Add Skill Creation Wizard

**Files:**
- Create: `SkillWizardDialog.tsx`
- Modify: `SkillManagementPage.tsx`
- Test: `SkillManagementPage.test.tsx`

**Why:** Admins need a guided way to create usable CUSTOM skills instead of pasting empty Markdown.

**Impact/Compatibility:** Wizard creates the same CUSTOM skill API request as the raw editor.

**Verification:**
```powershell
cd frontend
npm test -- SkillManagementPage
npm run build
```

- [ ] Write RED tests for wizard steps: basic info, content, test prompts, preview, submit.
- [ ] Implement wizard with generated Markdown preview.
- [ ] Validate name, description, and non-empty body before submit.
- [ ] Save optional test prompts into frontmatter `examples`.
- [ ] Verify frontend tests and build pass.
- [ ] Commit: `feat: add custom skill creation wizard`

### Task 12: Add Contract Tests and Runtime Regression Coverage

**Files:**
- Modify: `frontend/src/services/frontendCapabilityContracts.test.ts`
- Create or modify backend endpoint manifest generation if the project has automated extraction scripts.
- Add web tests for `/api/skills`.
- Add runtime tests under `seahorse-agent-tests`.

**Why:** The frontend must not drift from backend Skill endpoints.

**Impact/Compatibility:** Expands contract coverage only.

**Verification:**
```powershell
cd frontend
npm test -- frontendCapabilityContracts
cd ..
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
.\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=*Skill*
```

- [ ] Add `/api/skills` endpoints to contract coverage.
- [ ] Add `/api/agents/{agentId}/skills` to contract coverage.
- [ ] Add runtime regression proving published version `skillSetJson` drives prompt injection.
- [ ] Add regression proving editing a CUSTOM skill after publish does not change old version prompt.
- [ ] Verify contract and runtime tests pass.
- [ ] Commit: `test: cover skill api and runtime contracts`

### Task 13: Documentation and Deployment Readiness

**Files:**
- Create: `docs/skills/skill-management.md`
- Create: `docs/skills/skill-format.md`
- Modify: `docs/deployment/enterprise-mode.md`
- Modify: `.env.full.example` only if new runtime properties are introduced.

**Why:** Operators need to know how Skills are installed, governed, snapshotted, and injected.

**Impact/Compatibility:** Documentation only unless environment defaults are needed.

**Verification:**
```powershell
docker compose -f docker-compose.full.yml config
```

- [ ] Document Skill format, public/custom lifecycle, version snapshots, and tool-permission boundary.
- [ ] Document admin workflows for create, edit, rollback, install, bind, publish.
- [ ] Document migration behavior for existing Agent versions.
- [ ] Document runtime prompt budget defaults.
- [ ] Verify Docker compose config remains valid.
- [ ] Commit: `docs: document agent skills operations`

---

## Rollout Plan

### Phase 1: Foundation

Deliver Tasks 1 through 4. Admins can manage Skills, but Agent runtime behavior is unchanged.

**Exit evidence:**
- `/api/skills` works.
- Skill parser and scanner tests pass.
- PUBLIC edit attempts fail.
- CUSTOM edits create revisions.

### Phase 2: Agent Binding and Runtime

Deliver Tasks 5 through 7. Agent versions can snapshot Skill revisions and runtime can inject them.

**Exit evidence:**
- Publish persists `skillSetJson`.
- Runtime prompt includes selected skills.
- Editing a Skill after publish does not affect an old Agent version.
- Skill advisory tools do not expand `allowedToolIds`.

### Phase 3: Product Surface

Deliver Tasks 8 through 11. Admin UI supports full lifecycle, built-ins, binding, and wizard creation.

**Exit evidence:**
- `/admin/skills` is usable.
- Agent editor supports Skill binding.
- Publish dialog shows Skill snapshot.
- Six built-in Skills appear and are read-only.

### Phase 4: Hardening

Deliver Tasks 12 and 13. Contracts, docs, and deployment checks complete.

**Exit evidence:**
- Frontend contract tests include Skill APIs.
- Backend focused tests and frontend build pass.
- Operator documentation exists.

---

## Risks

### Prompt Bloat

Risk: Injecting full Skill bodies can consume too much context.

Mitigation: `SkillRuntimeComposer` must enforce per-skill and total budgets. Progressive `load_skill` can reduce default injection to metadata for high-count Skill sets.

### Behavior Drift

Risk: A global Skill edit changes already published Agent behavior.

Mitigation: Runtime reads only `AgentVersion.skillSetJson` bound revisions.

### Tool Permission Confusion

Risk: Users assume Skill `allowed_tools` grants tool access.

Mitigation: Label it as advisory dependencies in API and UI. Tool execution remains controlled by ToolCatalog, Agent tool bindings, and `allowedToolIds`.

### Unsafe Archives

Risk: `.skill` install introduces path traversal or oversized content.

Mitigation: The installer must reject absolute paths, parent traversal, hidden binary payloads, oversized files, and missing `SKILL.md`.

### Frontend Drift

Risk: Skill frontend calls endpoints that backend does not expose.

Mitigation: Extend frontend capability contract tests and backend endpoint manifest coverage.

---

## Acceptance Criteria

- `/api/features` exposes `SKILL_MANAGEMENT`.
- `/admin/skills` is hidden or unavailable when backend disables `SKILL_MANAGEMENT`.
- Admin can list PUBLIC and CUSTOM skills.
- Admin can create, edit, enable, disable, delete, view history, and rollback CUSTOM skills.
- Admin cannot edit or delete PUBLIC skills.
- Every CUSTOM edit creates a new immutable revision.
- `.skill` install validates archive shape and runs scanner before persistence.
- At least six built-in PUBLIC skills are seeded idempotently.
- Agent editor can bind selected skill revisions to a draft.
- Agent publish stores `skillSetJson`.
- Existing Agent versions without `skillSetJson` still load and run.
- Runtime prompt includes only selected Skill revisions from the Agent version snapshot.
- Editing a Skill after publish does not change old published Agent behavior.
- Skill advisory tools never expand `allowedToolIds`.
- Skill API and Agent binding API are covered by frontend/backend contract tests.
- Backend focused tests, frontend focused tests, frontend build, and compose config verification pass.

---

## Self-Review

### Spec Coverage

- Skill lifecycle management is covered by Tasks 2, 3, 4, 8, 9, and 11.
- PUBLIC/CUSTOM separation is covered by Tasks 2, 4, 8, and 9.
- Agent binding and publish snapshot are covered by Tasks 5 and 10.
- Runtime injection is covered by Tasks 6 and 7.
- Security scanning and install safety are covered by Tasks 2, 4, and 13.
- Feature gate and frontend degradation are covered by Task 1.
- Contract and deployment verification are covered by Tasks 12 and 13.

### Placeholder Scan

This plan intentionally avoids unresolved placeholder steps. Each task names concrete files, expected behavior, and verification commands.

### Type Consistency

- `skillSetJson` is the Java request, command, domain, JDBC, and frontend field name.
- `SkillSetSnapshot` is the runtime JSON structure stored inside `skillSetJson`.
- `SkillRuntimeBlock` is the runtime prompt block consumed by `AgentLoopRequest`.
- `allowedTools` is advisory metadata and remains distinct from runtime `allowedToolIds`.

### Compatibility

Existing Agent versions default `skillSetJson` to `{}`. Existing ToolCatalog, Agent tool binding, and run logic keep current ownership.

### ADR Signal

This plan creates a durable architecture decision: Skill runtime truth belongs to `AgentVersion.skillSetJson`, not global Skill enablement. Completion should consider an ADR if implementation changes this owner, adds executable Skill resources, or allows Skill-level tool permission grants.
