# AI Infra Phase 0/1 Evidence

Evidence will be appended as commands are run.

## 2026-05-23 Phase 0/1 kernel slice

Command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests' test
```

Result:

- Exit status: 0
- Tests run: 6
- Failures: 0
- Errors: 0
- Covered: Agent draft creation, immutable publish boundary, non-admin rejection, run start with latest version, disabled-agent rejection, cancel idempotency.

Blocked wider verification:

```powershell
.\mvnw -pl seahorse-agent-tests -am '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 1
- Blocked in `seahorse-agent-adapter-web` compile before test execution.
- Error shape: adapter-web could not resolve existing kernel classes such as `DocumentFetchRequest`, `CurrentUser`, and `KnowledgeBaseInboundPort`, even though those classes exist in `seahorse-agent-kernel/target/classes`.
- Decision: keep Phase 1 kernel verification at kernel owner boundary and record cross-module reactor failure as a follow-up integration issue.

## 2026-05-23 Phase 1 Task 1.5 run-store chat execution slice

RED command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

RED result:

- Exit status: 1
- Failure: `SeahorseAgentChatRunStoreAutoConfigurationTests.shouldWireRunStoreIntoAgentChatExecutionPath`
- Evidence: Spring context had no `AgentRunStepRecorder` bean, proving starter did not wire run-store step recording into the agent execution path.

GREEN / regression commands:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests,KernelChatAgentRunStoreTests' test
```

Result:

- Exit status: 0
- Tests run: 11
- Failures: 0
- Errors: 0
- Covered: Agent definition service, run lifecycle service, legacy AGENT chat run creation, run success, model turn step recording, tool call step recording.

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4
- Failures: 0
- Errors: 0
- Covered: JDBC persistence for AgentDefinition, AgentVersion, AgentRun, AgentStep.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 3
- Failures: 0
- Errors: 0
- Covered: Registry/run store starter beans, main kernel auto-configuration path, `AgentRunStepRecorder` repository wiring, AGENT chat run/step recording, no-repository noop fallback.

```powershell
.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 2
- Failures: 0
- Errors: 0
- Covered: Phase 1 agent registry/run web controller contracts.

Notes:

- `ChatMode.RAG` execution path was not modified in this slice.
- `KernelAgentLoop` depends only on the kernel application abstraction `AgentRunStepRecorder`; repository details remain behind ports/adapters.
- Fallback/noop behavior is covered when `AgentRunRepositoryPort` is absent.

## 2026-05-23 AI Infra prototype public route fix

Build command:

```powershell
npm run build
```

Run from:

```text
D:\code\seahorse-agent\frontend
```

Result:

- Exit status: 0
- Covered: TypeScript/Vite production build for the frontend after route/auth changes.
- Warning: Vite reported large chunks over 500 kB; unrelated to this auth-route fix.

Browser verification:

```powershell
npx --yes --package @playwright/cli playwright-cli -s=aiinfra-auth-check delete-data
npx --yes --package @playwright/cli playwright-cli -s=aiinfra-auth-check open http://127.0.0.1:5173/prototype/ai-infra
npx --yes --package @playwright/cli playwright-cli -s=aiinfra-auth-check eval "..."
npx --yes --package @playwright/cli playwright-cli -s=aiinfra-auth-check goto http://127.0.0.1:5173/admin/ai-infra
```

Result:

- Fresh browser session had no saved user data.
- `/prototype/ai-infra` rendered without login.
- Final URL stayed `http://127.0.0.1:5173/prototype/ai-infra`.
- Visible H1: `Seahorse 企业级 AI 控制面`.
- Legacy `/admin/ai-infra` resolved to `http://127.0.0.1:5173/prototype/ai-infra`, not `/login`.

Fix boundary:

- `frontend/src/router.tsx`: removed the protected `/admin` child page for `ai-infra`; retained a top-level legacy redirect to `/prototype/ai-infra`.
- `frontend/src/pages/admin/AdminLayout.tsx`: admin menu now links directly to `/prototype/ai-infra`.
- `frontend/src/utils/authSession.ts`: treats `/admin/ai-infra` as auth-neutral during unauthorized-session handling so startup auth checks do not steal the redirect.

## 2026-05-23 AI Infra prototype backend auth whitelist fix

Root cause:

- The frontend route was already public, but backend direct access still passed through `SeahorseSecurityWebMvcConfiguration`.
- The Sa-Token interceptor was registered on `/**` and only excluded `/auth/**` and `/error`, so production/backend-served SPA routes such as `/prototype/ai-infra` and `/admin/ai-infra` could be rejected before the React router rendered.

RED command:

```powershell
.\mvnw -pl seahorse-agent-adapter-web '-Dtest=SeahorseSecurityWebMvcConfigurationTests' test
```

RED result:

- Exit status: 1
- Failure shape: `shouldAllowAiInfraPrototypeRoutesWithoutLogin` triggered `StpUtil.checkLogin()` and failed under the lightweight test Sa-Token context, proving the public prototype route still entered login verification.

GREEN / regression commands:

```powershell
.\mvnw -pl seahorse-agent-adapter-web '-Dtest=SeahorseSecurityWebMvcConfigurationTests,SeahorseAgentControllerTests' test
```

Result:

- Exit status: 0
- Tests run: 4
- Failures: 0
- Errors: 0
- Covered: `/prototype/ai-infra` and `/admin/ai-infra` bypass backend login verification; protected paths such as `/user/me`, `/agents`, and `/agent-runs/run-1` are not classified as public; Phase 1 web controller contracts remain green.

```powershell
npm run build
```

Run from:

```text
D:\code\seahorse-agent\frontend
```

Result:

- Exit status: 0
- Covered: frontend production build still passes after backend auth whitelist fix.
- Warning: Vite reported large chunks over 500 kB; unrelated to auth behavior.

```powershell
git diff --check
```

Result:

- Exit status: 0
- Covered: no whitespace errors.
- Notes: Git reported CRLF conversion warnings for existing touched files.

Broad contract test attempt:

```powershell
.\mvnw -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 1
- Blocked in `seahorse-agent-tests` runtime, not in adapter-web compile.
- Error shape: `NoClassDefFoundError` for existing kernel port classes such as `AuthInboundPort`, `AgentDefinitionInboundPort`, and `MetadataSchemaInboundPort`.
- Diagnostic evidence: those classes exist under `seahorse-agent-kernel/target/classes` and inside `seahorse-agent-kernel-0.0.1-SNAPSHOT.jar`; the failure is recorded as an existing broad test classpath issue.

Fix boundary:

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSecurityWebMvcConfiguration.java`: added exact/prefix public path classification and interceptor excludes for SPA shell, static assets, auth endpoints, and the AI Infra prototype routes.
- `seahorse-agent-adapter-web/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSecurityWebMvcConfigurationTests.java`: added regression coverage for public prototype routes and protected API paths.

## 2026-05-23 Phase 1 runId stream meta closure

RED command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelChatAgentRunStoreTests' test
```

RED result:

- Exit status: 1
- Failure shape: test compilation failed because `RecordingCallback` attempted to override `onRunStarted(String runId)`, but `StreamCallback` did not expose that callback yet.
- Evidence: current stream contract could not carry the Agent Run identity to callers.

GREEN / regression commands:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelChatAgentRunStoreTests' test
```

Result:

- Exit status: 0
- Tests run: 1
- Failures: 0
- Errors: 0
- Covered: `ChatMode.AGENT` creates `AgentRun`, records model/tool steps, and emits the same persisted `runId` through `StreamCallback.onRunStarted`.

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests,KernelChatAgentRunStoreTests' test
```

Result:

- Exit status: 0
- Tests run: 12
- Failures: 0
- Errors: 0
- Covered: Phase 1 kernel registry/run service regressions plus stream runId callback.

```powershell
.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests,SeahorseSecurityWebMvcConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4 in adapter-web
- Failures: 0
- Errors: 0
- Covered: reactor compile for updated `StreamCallback`/`StreamMetaPayload` contract and existing web API/security regressions.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 3 in starter
- Failures: 0
- Errors: 0
- Covered: starter wiring still creates registry/run store/chat run lifecycle beans after stream callback contract change.

```powershell
npm run build
```

Run from:

```text
D:\code\seahorse-agent\frontend
```

Result:

- Exit status: 0
- Covered: frontend accepts optional `runId` in stream meta and preserves it as `Message.agentRunId`.
- Warning: Vite reported large chunks over 500 kB; unrelated to this contract change.

```powershell
git diff --check
```

Result:

- Exit status: 0
- Covered: no whitespace errors.
- Notes: Git reported CRLF conversion warnings for existing touched files.

Fix boundary:

- `StreamCallback`: added default `onRunStarted(String runId)` to preserve binary/source compatibility for existing callback implementations.
- `KernelChatInboundService`: emits `onRunStarted` immediately after `AgentRun` creation and before `KernelAgentLoop.streamExecute`.
- `StreamMetaPayload` / `LocalChatStreamCallbackFactory`: meta payload accepts optional `runId`; existing initial meta remains valid and a second meta event carries `runId` when an Agent run starts.
- `frontend/src/types/index.ts` / `frontend/src/stores/chatStore.ts`: frontend stream meta type accepts `runId` and stores it on the active assistant message.

## 2026-05-23 Phase 2 Tool Gateway / Policy Engine baseline

RED/GREEN coverage:

- `KernelAgentLoopToolGatewayTests` verifies tool calls go through `ToolGatewayPort`, carrying runId, stepId, toolCallId, agentId/versionId, tenant/user identity, toolId, arguments, resource refs, idempotency key, and allowedToolIds.
- `KernelAgentLoopToolGatewayTests` also verifies a hallucinated/unexposed tool call is still delegated to Gateway, so policy/audit can own rejection instead of the runtime pre-filtering it.
- `LocalToolGatewayPortPolicyTests` verifies `ALLOW` executes the real tool, while `DENY` and `APPROVAL_REQUIRED` do not touch `ToolPort`.

Command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,KernelAgentRunServiceTests' test
```

Result:

- Exit status: 0
- Tests run: 13
- Failures: 0
- Errors: 0
- Covered: Phase 2 Gateway delegation, minimal policy boundary, Phase 1 run lifecycle, and chat run-store regression.

Command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 3 in starter
- Failures: 0
- Errors: 0
- Covered: starter registry/run store/chat lifecycle wiring after Gateway and stream callback changes.

Command:

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4 in JDBC adapter
- Failures: 0
- Errors: 0
- Covered: AgentDefinition, AgentVersion, AgentRun, and AgentStep JDBC persistence.

Command:

```powershell
.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests,SeahorseSecurityWebMvcConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4 in adapter-web
- Failures: 0
- Errors: 0
- Covered: Phase 1 agent registry/run controller contracts and AI Infra prototype public-route backend whitelist.

Frontend command:

```powershell
npm run build
```

Run from:

```text
D:\code\seahorse-agent\frontend
```

Result:

- Exit status: 0
- Covered: AI Infra prototype route, auth-neutral route handling, and stream meta runId frontend typing.
- Warning: Vite reported large chunks over 500 kB; unrelated to this AI Infra slice.

Whitespace command:

```powershell
git diff --check
```

Result:

- Exit status: 0
- Covered: no whitespace errors.
- Notes: Git reported LF-to-CRLF conversion warnings for existing touched files.

Fix boundary:

- `ToolGatewayPort` and `ToolInvocationRequest`: added the explicit Gateway contract and request context object.
- `KernelAgentLoop`: now builds `ToolInvocationRequest` and delegates actual execution to `ToolGatewayPort`; direct runtime `ToolPort.invoke` path is removed.
- `LocalToolGatewayPort`: resolves the registered tool, calls `ToolPolicyPort.decide(...)`, and only touches `ToolPort` after `PolicyDecision.Effect.ALLOW`.
- `PolicyDecision`, `ToolPolicyRequest`, and `ToolPolicyPort`: added the Phase 2 minimum policy vocabulary and built-in existence/binding policy.
- Core Agent definition/run/gateway records and ports now have Chinese Javadoc for entity fields and interface methods.

## 2026-05-23 Phase 2 Tool Catalog / Agent Binding policy slice

RED/GREEN coverage:

- `CatalogBackedToolPolicyPortTests` verifies disabled tools are denied before binding lookup, unbound tools are denied, low-risk READ tools are allowed when catalog and binding both allow them, and `CRITICAL` / `DELETE` / `EXTERNAL_SEND` / explicit `requiresApproval` tools return `APPROVAL_REQUIRED`.
- `SeahorseAgentChatRunStoreAutoConfigurationTests.shouldWireCatalogBackedToolPolicyIntoAgentLoop` verifies starter wiring injects the catalog-backed policy through `ToolGatewayPort` into `KernelAgentLoop`; an unbound catalog tool is blocked with `TOOL_NOT_BOUND` and the real `ToolPort` is not invoked.

Focused kernel command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=CatalogBackedToolPolicyPortTests,LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,KernelAgentRunServiceTests' test
```

Result:

- Exit status: 0
- Tests run: 18
- Failures: 0
- Errors: 0
- Covered: catalog-backed policy decisions, local gateway policy enforcement, agent loop gateway delegation, chat run-store regression, and agent run lifecycle regression.

Focused starter command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4 in starter
- Failures: 0
- Errors: 0
- Covered: registry/run store starter wiring plus catalog-backed `ToolPolicyPort` and injected `ToolGatewayPort` selection.

Fix boundary:

- `ToolCatalogEntry`, `ToolProvider`, `ToolRiskLevel`, and `ToolActionType`: added the kernel metadata model required for risk/action/provider-based policy decisions.
- `AgentToolBinding`: added immutable Agent version to tool binding snapshot metadata, including max calls and argument policy placeholders for later enforcement.
- `ToolCatalogRepositoryPort` and `AgentToolBindingRepositoryPort`: added narrow outbound ports with empty fallbacks for catalog and binding lookup.
- `ToolPolicyReasonCodes`: centralized stable policy reason codes used by default and catalog-backed policies.
- `CatalogBackedToolPolicyPort`: added catalog/binding-backed built-in policy while keeping tool execution in `LocalToolGatewayPort`.
- `SeahorseAgentKernelAgentAutoConfiguration`: now creates catalog-backed policy only when both catalog and binding repositories exist, creates a local `ToolGatewayPort`, and injects the selected gateway into `KernelAgentLoop`.

Remaining Phase 2 work:

- Invocation audit port and storage-backed invocation records.
- `maxCallsPerRun` and argument policy enforcement.
- MCP allowlist registration into the catalog.
- Tool catalog/binding/audit management APIs.

## 2026-05-23 Phase 2 MCP allowlist catalog registration slice

RED coverage:

- `McpToolAllowlistRegistrarTests.shouldWriteAllowlistedMcpToolToCatalogWithDefaultPolicyMetadata` first failed because `McpToolAllowlistRegistrar` registered `weather_query` into the runtime `ToolRegistryPort` but saved zero entries into `ToolCatalogRepositoryPort`.

RED command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=McpToolAllowlistRegistrarTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

RED result:

- Exit status: 1
- Failure shape: expected catalog saved entry count 1, actual 0.

GREEN support command:

```powershell
.\mvnw '-Dspotless.apply.skip=true' '-Dmaven.test.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc -am install
```

Result:

- Exit status: 0
- Covered: refreshed required kernel and JDBC main artifacts without compiling unrelated tests.

Focused starter command:

```powershell
.\mvnw '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter clean test '-Dtest=McpToolAllowlistRegistrarTests,SeahorseAgentChatRunStoreAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Result:

- Exit status: 0
- Tests run: 6
- Failures: 0
- Errors: 0
- Covered: MCP allowlist catalog registration, existing Agent chat run-store starter wiring, and registry repository auto-configuration.

Blocked broader command:

```powershell
.\mvnw '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am clean test '-Dtest=McpToolAllowlistRegistrarTests,SeahorseAgentChatRunStoreAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Result:

- Exit status: 1
- Blocked before starter tests by unrelated `seahorse-agent-adapter-vector-pgvector` test compilation instability. The error shape referenced missing kernel class files under `seahorse-agent-kernel/target/classes` while compiling pgvector tests.
- Decision: keep verification at the starter owner boundary after refreshing required main artifacts.

Fix boundary:

- `McpToolAllowlistRegistrar`: now accepts an optional `ToolCatalogRepositoryPort` and `Clock`, preserves the old constructor, and writes a catalog entry only after runtime MCP tool registration succeeds.
- MCP catalog entries use provider `MCP`, default risk `MEDIUM`, default action `EXECUTE`, resource type `MCP`, owner team `mcp`, enabled=true, approval=false, and the same generated input schema used by runtime tool descriptors.
- `SeahorseAgentKernelAgentAutoConfiguration`: injects the optional catalog repository and clock into `McpToolAllowlistRegistrar`, falling back to the empty catalog repository when no storage is configured.

Remaining Phase 2 work:

- Tool catalog/binding/audit management APIs.
- HITL approval workflow, resource ACL, and output redaction policy remain later slices.

## 2026-05-23 Phase 2 Tool Invocation Audit slice

RED/GREEN coverage:

- `LocalToolGatewayPortAuditTests` first failed because `ToolInvocationAuditPort`, audit event records, and `ToolInvocationStatus` did not exist; it then verified Gateway records requested, policy decision, and completed events for allowed, denied, failed, and legacy no-runId/no-userId tool calls.
- `JdbcToolInvocationAuditRepositoryAdapterTests` first failed because `JdbcToolInvocationAuditRepositoryAdapter` did not exist; it then verified requested/decision/completed lifecycle persistence into `sa_tool_invocation`.
- `SeahorseAgentRegistryAutoConfigurationTests` now verifies the JDBC repository auto-configuration creates `ToolInvocationAuditPort` together with registry, run-store, catalog, and binding repositories.

Focused kernel command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=LocalToolGatewayPortAuditTests,LocalToolGatewayPortPolicyTests,CatalogBackedToolPolicyPortTests,KernelAgentLoopToolGatewayTests' test
```

Result:

- Exit status: 0
- Tests run: 14
- Failures: 0
- Errors: 0
- Covered: Tool Gateway audit lifecycle, policy enforcement, catalog-backed policy regression, and AgentLoop Gateway delegation.

Focused JDBC command:

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolInvocationAuditRepositoryAdapterTests,JdbcToolCatalogRepositoryAdapterTests,JdbcAgentToolBindingRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 5
- Failures: 0
- Errors: 0
- Covered: `sa_tool_invocation` audit lifecycle persistence plus existing tool catalog, binding, and run-store JDBC regressions.

Focused starter command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4
- Failures: 0
- Errors: 0
- Covered: `ToolInvocationAuditPort` JDBC auto-configuration and existing Agent chat run-store / Tool Gateway wiring regressions.

Debug note:

- An earlier parallel Maven run failed with missing class files under `seahorse-agent-adapter-repository-jdbc/target/classes`. Root cause was concurrent Maven commands writing the same reactor module output directory; the same JDBC command passed when rerun sequentially.

Fix boundary:

- `ToolInvocationAuditPort`: added a narrow outbound audit port with noop fallback.
- `ToolInvocationAuditRecord`, `ToolInvocationAuditDecision`, `ToolInvocationAuditCompletion`, and `ToolInvocationStatus`: added immutable audit event models with Chinese field documentation and database-aligned non-null constraints.
- `LocalToolGatewayPort`: records requested, decision, and completed audit events around policy and tool execution; preserves legacy constructors and generates compatible audit run/user identifiers when older direct callers do not provide runtime identity.
- `JdbcToolInvocationAuditRepositoryAdapter`: persists requested/decision/completed lifecycle updates into `sa_tool_invocation`.
- `agent-registry-run-store-postgresql.sql`: added `sa_tool_invocation` table and indexes.
- `SeahorseAgentRegistryRepositoryAutoConfiguration`: auto-configures the JDBC audit repository.
- `SeahorseAgentKernelAgentAutoConfiguration`: injects audit port and clock into the local gateway while preserving noop fallback.

Remaining Phase 2 work:

- `maxCallsPerRun` and argument policy enforcement.
- MCP allowlist registration into the catalog.
- Tool catalog/binding/audit management APIs.

## 2026-05-23 Phase 2 maxCallsPerRun enforcement slice

RED/GREEN coverage:

- `CatalogBackedToolPolicyPortTests` first failed because no `ToolInvocationUsagePort` existed; it then verified policy denies with `TOOL_CALL_LIMIT_EXCEEDED` when requested calls exceed `AgentToolBinding.maxCallsPerRun`, and allows when the count equals the limit because Gateway records the current `REQUESTED` event before policy decision.
- `JdbcToolInvocationAuditRepositoryAdapterTests` verifies requested-call usage is counted by `runId + agentId + versionId + toolId` without leaking across other runs, versions, or tools.
- `SeahorseAgentRegistryAutoConfigurationTests` verifies the JDBC audit adapter is exposed as `ToolInvocationUsagePort`.
- `SeahorseAgentChatRunStoreAutoConfigurationTests` verifies starter catalog-backed policy receives `ToolInvocationUsagePort` and returns `TOOL_CALL_LIMIT_EXCEEDED`.

Validation environment:

- Main workspace verification was blocked before test execution by unrelated unstaged memory-maintenance changes: `DefaultMemoryMaintenanceService` called a `MemoryMaintenanceRunRecord` constructor signature that did not exist in the current dirty worktree.
- Final verification was run in clean detached worktree `C:\Users\miracle\.config\aegis\worktrees\seahorse-agent\tool-call-limit-verify`, created from `HEAD=5862147`, with only the staged `maxCallsPerRun` patch applied.

Focused kernel command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=CatalogBackedToolPolicyPortTests,LocalToolGatewayPortAuditTests,LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests' test
```

Result:

- Exit status: 0
- Tests run: 16
- Failures: 0
- Errors: 0
- Covered: call-limit policy boundary, Tool Gateway audit lifecycle, policy enforcement, and AgentLoop Gateway delegation.

Focused JDBC command:

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolInvocationAuditRepositoryAdapterTests,JdbcToolCatalogRepositoryAdapterTests,JdbcAgentToolBindingRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 6
- Failures: 0
- Errors: 0
- Covered: requested-call usage counting plus audit, tool catalog, Agent tool binding, and Agent run JDBC regressions.

Focused starter command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 5 in starter
- Failures: 0
- Errors: 0
- Covered: `ToolInvocationUsagePort` repository exposure, catalog-backed policy usage injection, and existing Agent chat run-store / Tool Gateway wiring regressions.

Fix boundary:

- `ToolInvocationUsagePort`: added a narrow read-only outbound port for requested-call usage, preserving ISP instead of expanding the audit write port.
- `CatalogBackedToolPolicyPort`: enforces `AgentToolBinding.maxCallsPerRun` only when `runId` is present; legacy no-runId calls keep the previous compatibility behavior.
- `ToolPolicyReasonCodes`: added stable `TOOL_CALL_LIMIT_EXCEEDED`.
- `JdbcToolInvocationAuditRepositoryAdapter`: implements usage counting over `sa_tool_invocation` requested records.
- `SeahorseAgentKernelAgentAutoConfiguration`: injects usage port into catalog-backed policy with an empty fallback.

Remaining Phase 2 work:

- Argument policy enforcement.
- MCP allowlist registration into the catalog.
- Tool catalog/binding/audit management APIs.

## 2026-05-23 Phase 2 argument policy enforcement slice

RED/GREEN coverage:

- `CatalogBackedToolPolicyPortTests.shouldDenyWhenRequiredArgumentIsMissing` first failed because catalog-backed policy allowed a tool call even when `argumentPolicyJson` required `query` and the request did not provide it.
- `CatalogBackedToolPolicyPortTests.shouldDenyWhenArgumentIsNotAllowedByBindingPolicy` first failed because catalog-backed policy allowed an argument outside the binding whitelist.
- The final test set verifies missing required arguments, disallowed argument names, valid argument policy pass-through, and fail-closed handling for invalid `argumentPolicyJson`.

Focused kernel command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=CatalogBackedToolPolicyPortTests,LocalToolGatewayPortAuditTests,LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests' test
```

Result:

- Exit status: 0
- Tests run: 20
- Failures: 0
- Errors: 0
- Covered: argument policy decisions, call-limit policy boundary, Tool Gateway audit lifecycle, policy enforcement, and AgentLoop Gateway delegation.

Fix boundary:

- `ToolArgumentPolicy`: added a package-local parser/validator for binding-level `argumentPolicyJson`, currently supporting `required` and `allowed` arrays only.
- `CatalogBackedToolPolicyPort`: enforces argument policy after call-limit checks and before approval/execution decisions.
- `ToolPolicyReasonCodes`: added stable reason codes for invalid argument policy, missing required argument, and disallowed argument.
- `CatalogBackedToolPolicyPortTests`: covers reject/pass/fail-closed behavior and uses stable reason-code constants for new assertions.

Remaining Phase 2 work:

- MCP allowlist registration into the catalog.
- Tool catalog/binding/audit management APIs.
- HITL approval workflow and broader resource ACL/output redaction policy remain later slices.

## 2026-05-23 Phase 2 Tool Catalog / Agent Binding persistence slice

RED/GREEN coverage:

- `JdbcToolCatalogRepositoryAdapterTests` first failed because the catalog page/query model and JDBC adapter did not exist; it then verified save/update/find/page and enable/disable persistence for `sa_tool_catalog`.
- `JdbcAgentToolBindingRepositoryAdapterTests` first failed because the binding JDBC adapter did not exist; it then verified Agent version binding snapshots are replaced, listed, and found without leaking bindings across Agent versions.
- `SeahorseAgentRegistryAutoConfigurationTests` first failed because no `ToolCatalogRepositoryPort` bean existed; it then verified starter JDBC repository auto-configuration creates both catalog and binding repositories.

Focused JDBC command:

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolCatalogRepositoryAdapterTests,JdbcAgentToolBindingRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 6
- Failures: 0
- Errors: 0
- Covered: tool catalog JDBC persistence, Agent tool binding JDBC persistence, and existing Agent registry/run JDBC regressions.

Focused starter command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit status: 0
- Tests run: 4 in starter
- Failures: 0
- Errors: 0
- Covered: starter registration of Agent definition/run repositories, tool catalog repository, Agent tool binding repository, and catalog-backed policy wiring regression.

Fix boundary:

- `ToolCatalogQuery` and `ToolCatalogPage`: added a minimal catalog pagination contract for repository and future management API reuse.
- `ToolCatalogRepositoryPort`: added a default `page(...)` method while preserving empty fallback compatibility.
- `JdbcToolCatalogRepositoryAdapter`: added JDBC persistence for tool catalog save/update/find/page and enable/disable.
- `JdbcAgentToolBindingRepositoryAdapter`: added JDBC persistence for Agent version tool binding snapshots.
- `agent-registry-run-store-postgresql.sql`: added `sa_tool_catalog` and `sa_agent_tool_binding` table definitions and indexes.
- `SeahorseAgentRegistryRepositoryAutoConfiguration`: auto-configures the two new JDBC repository beans using the existing repository type conditions.

Remaining Phase 2 work:

- Invocation audit port and storage-backed invocation records.
- `maxCallsPerRun` and argument policy enforcement.
- MCP allowlist registration into the catalog.
- Tool catalog/binding/audit management APIs.
