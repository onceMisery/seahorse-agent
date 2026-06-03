# Checkpoint

## Current Todo

- Implement backend Skill core: done.
- Add schema and repository with existing type alignment: done.
- Add management, install, and binding API: done.
- Add Agent version `skillSetJson` snapshot and runtime injection: done.
- Add progressive `load_skill` runtime tool: done.
- Add built-in PUBLIC skills copied from `D:\code\deer-flow\skills\public`, including package resources: done.
- Add frontend Skill management and Agent binding/publish flow: done.
- Verify focused backend/frontend commands: done.

## Active Slice

Complete; awaiting integration choice.

## Completed

- Added Skill parser/scanner/domain/services/repository/web API.
- Added DDL and schema upgrade for `sa_agent_skill`, `sa_agent_skill_revision`, `sa_agent_skill_binding`, and `sa_agent_version.skill_set_json`.
- Kept new Skill JSON-ish persistence columns as `TEXT`, matching existing `sa_agent_version` JSON snapshot style.
- Added raw `SKILL.md` install endpoint and built-in PUBLIC Skill bootstrap from complete Deer Flow public skill packages.
- Added Agent publish payload `skillSetJson`, persisted version snapshots, runtime prompt injection, and read-only `load_skill`.
- Added frontend `/admin/skills`, menu entry, Agent editor Skills tab, Agent detail publish snapshot flow, CUSTOM Skill edit/delete, and frontend contract coverage.

## Evidence

- Frontend target tests: `node_modules\.bin\vitest.cmd --run src/pages/admin/agents/components/AgentPublishDialog.test.tsx src/services/frontendCapabilityContracts.test.ts` passed, 2 files / 11 tests.
- Frontend build: `npm.cmd run build` passed.
- JDBC tests: `mvn --% -pl seahorse-agent-adapter-repository-jdbc -am -Dtest=JdbcAgentSkillRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test` passed, 5 tests.
- Web tests: `mvn --% -pl seahorse-agent-adapter-web -am -Dtest=SeahorseSkillControllerTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test` passed, 3 tests.
- Starter tests: `mvn --% -pl seahorse-agent-spring-boot-starter -am -Dtest=BuiltInAgentSkillRegistrarTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test` passed, 2 tests, with upstream reactor modules successful.
- Runtime tests:
  - `mvn -pl seahorse-agent-kernel -Dtest=KernelAgentLoopToolGatewayTests#shouldLoadSelectedSkillWithoutCallingExternalToolGateway+shouldRejectLoadingSkillOutsideCurrentVersionSnapshot -DforkCount=0 test` passed.
  - `mvn -pl seahorse-agent-kernel -Dtest=KernelChatAgentRunStoreTests#registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools -DforkCount=0 test` passed.
- Backend compile: `mvn -pl seahorse-agent-adapter-web,seahorse-agent-adapter-repository-jdbc -am -DskipTests compile` passed.
- Deployment config: `docker compose -f docker-compose.full.yml config` passed, with Docker config access warnings only.
- DDL check: `skill_set_json`, `tags_json`, `allowed_tools_json`, `frontmatter_json`, and `scan_result_json` are `TEXT`.
- Resource package check: starter `skills/public` file list matches `D:\code\deer-flow\skills\public` and resource appendices preserve nested Markdown fences.

## Blockers

- None active.

## Next

User chooses whether to merge locally, push/create PR, keep branch as-is, or discard.

## Drift Check

Decision: complete current implementation scope. The implementation stayed aligned with the user's DDL type-alignment constraint and with the plan's full Skills objective.
