# Evidence

## Frontend

- Command: `node_modules\.bin\vitest.cmd --run src/pages/admin/agents/components/AgentPublishDialog.test.tsx src/services/frontendCapabilityContracts.test.ts`
  - Result: passed.
  - Output summary: 2 test files, 11 tests passed.
  - Covered: publish payload includes `skillSetJson`; Skill frontend contracts cover list/detail/create/update/delete/install/enable/disable/history/rollback/bindings/snapshot.
- Command: `npm.cmd run build`
  - Result: passed.
  - Notes: Vite reported large chunk and Browserslist freshness warnings.
- Command: `rg -n "<mojibake-patterns>" frontend/src/pages/admin frontend/src/components frontend/src/services frontend/src/config frontend/src/router.tsx`
  - Result: no matches.
  - Covered: repaired visible mojibake and malformed JSX in the Skills management and Agent Skill binding UI.
- Manual source audit:
  - Result: fixed.
  - Covered: CUSTOM Skill edit loads latest revision content instead of overwriting with placeholder markdown; CUSTOM Skill delete is exposed in the management UI.

## Backend

- Command: `mvn --% -pl seahorse-agent-adapter-repository-jdbc -am -Dtest=JdbcAgentSkillRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 5 tests passed.
  - Covered: Skill repository CRUD/page/revisions/bindings, deleted row exclusion, soft-delete binding replacement, and Agent version `skillSetJson` persistence.
- Command: `mvn --% -pl seahorse-agent-adapter-web -am -Dtest=SeahorseSkillControllerTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 3 tests passed.
  - Covered: Skill management endpoints, Agent Skill binding/snapshot endpoints, and disabled feature gate rejection.
- Command: `mvn --% -pl seahorse-agent-spring-boot-starter -am -Dtest=BuiltInAgentSkillRegistrarTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 2 tests passed; upstream reactor modules also succeeded.
  - Covered: built-in classpath PUBLIC Skill import uses default tenant and `system` operator, skips unreadable resources, appends package resources such as `references/`, `scripts/`, and root safe text files such as `LICENSE.txt` as read-only Markdown appendix content, and uses a dynamic fence when a resource itself contains Markdown code fences.
- Command: `mvn -pl seahorse-agent-kernel -Dtest=KernelAgentLoopToolGatewayTests#shouldLoadSelectedSkillWithoutCallingExternalToolGateway+shouldRejectLoadingSkillOutsideCurrentVersionSnapshot -DforkCount=0 test`
  - Result: passed.
  - Covered: `load_skill` loads only selected Skill snapshot content and rejects missing snapshot entries without external gateway calls.
- Command: `mvn -pl seahorse-agent-kernel -Dtest=KernelChatAgentRunStoreTests#registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools -DforkCount=0 test`
  - Result: passed.
  - Covered: published Agent version injects Skills and does not expose advisory tools such as `web_search`.
- Command: `mvn -pl seahorse-agent-adapter-web,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter -am -DskipTests compile`
  - Result: passed.
  - Covered: kernel, web adapter, JDBC adapter, and Spring starter compilation after Skills changes.
- Command: `Get-ChildItem -Path seahorse-agent-spring-boot-starter\src\main\resources\skills\public -Recurse | Where-Object { -not $_.PSIsContainer } | Measure-Object`
  - Result: 89 files.
  - Covered: starter now bundles complete Deer Flow public skill packages, not only `SKILL.md`.
- Command: compare relative file lists under `D:\code\deer-flow\skills\public` and `seahorse-agent-spring-boot-starter\src\main\resources\skills\public`
  - Result: matched.
  - Covered: all Deer Flow public skill files are present in starter resources.

## Deployment Config

- Command: `docker compose -f docker-compose.full.yml config`
  - Result: passed.
  - Notes: Docker warned it could not read `C:\Users\miracle\.docker\config.json`; compose config still rendered successfully.

## DDL Type Alignment

- Checked `resources/database/seahorse_init.sql` and `JdbcChatSchemaUpgrade`.
- Skills-added JSON-ish fields use `TEXT`:
  - `sa_agent_version.skill_set_json TEXT NOT NULL DEFAULT '{}'`
  - `sa_agent_skill.tags_json TEXT NOT NULL DEFAULT '[]'`
  - `sa_agent_skill.allowed_tools_json TEXT NOT NULL DEFAULT '[]'`
  - `sa_agent_skill_revision.frontmatter_json TEXT NOT NULL`
  - `sa_agent_skill_revision.scan_result_json TEXT NOT NULL`

## Fresh Closeout Verification

- Command: `rg -n "skill_set_json|tags_json|allowed_tools_json|frontmatter_json|scan_result_json" resources\database\seahorse_init.sql seahorse-agent-adapter-repository-jdbc\src\main\java\com\miracle\ai\seahorse\agent\adapters\repository\jdbc\JdbcChatSchemaUpgrade.java`
  - Result: passed.
  - Covered: all Skill JSON-ish DDL columns remain `TEXT`, matching the existing schema style.
- Command: compare relative file lists under `D:\code\deer-flow\skills\public` and `seahorse-agent-spring-boot-starter\src\main\resources\skills\public`
  - Result: `MATCHED: 89 files`.
  - Covered: bundled PUBLIC Skill resources match the Deer Flow source package.
- Command: `rg -n "<mojibake-patterns>" frontend\src\pages\admin frontend\src\components frontend\src\services frontend\src\config frontend\src\router.tsx docs\aegis\work\2026-06-03-agent-skills-full-stack`
  - Result: no matches.
  - Covered: visible Chinese mojibake patterns were not found in touched frontend/admin/docs surfaces.
- Command: `node_modules\.bin\vitest.cmd --run src/pages/admin/agents/components/AgentPublishDialog.test.tsx src/services/frontendCapabilityContracts.test.ts`
  - Result: passed.
  - Output summary: 2 test files, 11 tests passed.
- Command: `mvn --% -pl seahorse-agent-adapter-repository-jdbc -am -Dtest=JdbcAgentSkillRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 5 tests passed.
- Command: `mvn --% -pl seahorse-agent-adapter-web -am -Dtest=SeahorseSkillControllerTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 3 tests passed.
- Command: `mvn --% -pl seahorse-agent-spring-boot-starter -am -Dtest=BuiltInAgentSkillRegistrarTests -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test`
  - Result: passed.
  - Output summary: 2 tests passed; reactor build succeeded.
- Command: `mvn --% -pl seahorse-agent-kernel -Dtest=KernelAgentLoopToolGatewayTests#shouldLoadSelectedSkillWithoutCallingExternalToolGateway+shouldRejectLoadingSkillOutsideCurrentVersionSnapshot,KernelChatAgentRunStoreTests#registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools -DforkCount=0 test`
  - Result: passed.
  - Output summary: 3 tests passed.
- Command: `npm.cmd run build`
  - Result: passed.
  - Notes: Vite reported existing large chunk, stale Browserslist data, and dynamic/static import warnings.
- Command: `docker compose -f docker-compose.full.yml config`
  - Result: passed.
  - Notes: Docker warned it could not read `C:\Users\miracle\.docker\config.json`; compose config still rendered successfully.
