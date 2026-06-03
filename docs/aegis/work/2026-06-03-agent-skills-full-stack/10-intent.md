# Agent Skills Full Stack Implementation

## Requested Outcome

Implement a complete Agent Skills capability from the approved plan: manage reusable prompt-method skills, persist immutable revisions, bind selected revisions to agents, snapshot them on publish, and inject selected skill context into runtime.

## Scope

- Backend feature gate, domain model, parser, scanner, repository, services, and REST API.
- DDL and schema upgrade aligned with existing `sa_*` table types.
- Agent version `skillSetJson` snapshot compatibility.
- Runtime skill prompt composition.
- Frontend management and binding entry points where feasible.

## Non-Goals

- Do not grant tools from skill metadata.
- Do not mutate old published agent versions when global skills change.
- Do not rewrite unrelated Agent, tool, memory, or frontend layout behavior.

## Baseline Read Set

- `resources/database/seahorse_init.sql`
- `JdbcChatSchemaUpgrade.java`
- `AgentVersion.java`
- `AgentVersionPublishCommand.java`
- `KernelAgentDefinitionService.java`
- `KernelAgentLoop.java`
- `AdvancedFeature.java`
- `frontend/src/config/productMode.ts`
- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`

## Impact Notes

Current `sa_agent_version` stores JSON snapshot columns as `TEXT NOT NULL`, so new `skill_set_json` must also use `TEXT NOT NULL DEFAULT '{}'` instead of JSONB. Skill metadata JSON fields should likewise use `TEXT` to stay aligned with the nearby Agent Infra schema unless a local table family already requires JSONB.
