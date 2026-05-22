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
