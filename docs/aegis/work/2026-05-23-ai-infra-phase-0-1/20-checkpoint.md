# AI Infra Phase 0/1 Checkpoint

Current todo:

- Read phase docs and current architecture.
- Complete Phase 0 docs/package ownership shells.
- Add failing tests for Phase 1 kernel behavior.
- Implement the minimum kernel domain/ports/services to pass those tests.
- Run focused Maven tests and record evidence.
- Connect Phase 1 run store to `ChatMode.AGENT` execution path.
- Ensure starter auto-configuration wires run lifecycle and step recording while preserving noop fallback.

Completed:

- Phase docs reviewed.
- Two read-only subagents completed: phase requirements and code architecture mapping.
- Current branch and dirty worktree inspected.
- Phase 1 kernel registry/run service tests are green.
- `ChatMode.AGENT` now creates a legacy `AgentRun` when `AgentRunInboundPort` is present.
- `KernelAgentLoop` can record model turns and tool calls through `AgentRunStepRecorder`.
- Spring Boot starter now creates `AgentRunStepRecorder` and injects it into `KernelAgentLoop`.
- Spring Boot starter now passes optional `AgentRunInboundPort` into `KernelChatInboundService`.
- Starter fallback path is covered: AGENT chat remains available when no run repository exists.
- Focused Phase 1 kernel, JDBC, starter, and web controller regressions passed.
- AI Infra prototype page public access issue fixed: `/prototype/ai-infra` renders without login, and legacy `/admin/ai-infra` redirects to the public route without being intercepted by auth handling.
- Backend direct-access auth gap fixed: `SeahorseSecurityWebMvcConfiguration` now treats `/prototype/**`, `/admin/ai-infra`, `/login`, `/index.html`, `/assets/**`, and `/auth/**` as public paths while protected API paths remain non-public.
- Phase 1 runId stream meta gap fixed: `ChatMode.AGENT` now calls `StreamCallback.onRunStarted(runId)` after creating `AgentRun`; SSE meta accepts optional `runId`; frontend preserves it on the streaming assistant message as `agentRunId`.
- Phase 1 agent registry/run store API contract fixture was added to the broad web API contract test, but the broad `seahorse-agent-tests` run is still blocked by an existing test-runtime classpath issue unrelated to this auth-route fix.
- Phase 2 Tool Gateway first slice is green: `KernelAgentLoop` no longer executes tools through `ToolRegistryPort.find(...)->ToolPort.invoke(...)`; it builds `ToolInvocationRequest` and delegates actual execution decisions to `ToolGatewayPort`.
- Phase 2 Policy Engine baseline is green: `LocalToolGatewayPort` now calls `ToolPolicyPort.decide(...)` before touching `ToolPort`; `DENY` and `APPROVAL_REQUIRED` results do not execute tools.
- Core AI Infra domain/port comments are aligned with the project convention: key entity fields, interface methods, and Tool Gateway policy boundary logic now have Chinese Javadoc or inline comments.
- Phase 2 Tool Catalog policy slice is green: kernel now has `ToolCatalogEntry`, `AgentToolBinding`, risk/action/provider metadata, catalog/binding repository ports, stable policy reason codes, and `CatalogBackedToolPolicyPort`.
- Starter wiring now prefers catalog-backed policy when both catalog and binding repositories exist, injects `ToolGatewayPort` into `KernelAgentLoop`, and preserves the default local gateway fallback when no custom gateway is present.
- Phase 2 durable tool catalog/binding persistence slice is green: JDBC now persists `sa_tool_catalog` and `sa_agent_tool_binding`, starter auto-configures both repository ports, and the shared PostgreSQL schema resource includes both tables.
- Phase 2 invocation audit slice is green: `LocalToolGatewayPort` now records requested/decision/completed audit events, JDBC persists `sa_tool_invocation`, starter auto-configures `ToolInvocationAuditPort`, and legacy no-runId/no-userId calls retain a compatible audit identity instead of breaking tool execution.

Active slice:

- Phase 2 `02-tool-gateway-policy-engine.md` invocation audit slice completed at kernel/JDBC/starter boundary. Next Phase 2 work should add `maxCallsPerRun` enforcement, argument policy enforcement, MCP catalog registration, and management APIs.

Evidence refs:

- Subagent summaries in thread.
- `git status --short --branch`
- `rg --files docs/company-agent/ai-infra-phases`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests,KernelChatAgentRunStoreTests' test`
- `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=CatalogBackedToolPolicyPortTests,LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,KernelAgentRunServiceTests' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolCatalogRepositoryAdapterTests,JdbcAgentToolBindingRepositoryAdapterTests,JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=LocalToolGatewayPortAuditTests,LocalToolGatewayPortPolicyTests,CatalogBackedToolPolicyPortTests,KernelAgentLoopToolGatewayTests' test`
- `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolInvocationAuditRepositoryAdapterTests,JdbcToolCatalogRepositoryAdapterTests,JdbcAgentToolBindingRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-adapter-web '-Dtest=SeahorseSecurityWebMvcConfigurationTests,SeahorseAgentControllerTests' test`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelChatAgentRunStoreTests' test`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentDefinitionServiceTests,KernelAgentRunServiceTests,KernelChatAgentRunStoreTests' test`
- `.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests,SeahorseSecurityWebMvcConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `npm run build` in `frontend`
- `git diff --check`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentLoopToolGatewayTests' test`
- `.\mvnw -pl seahorse-agent-kernel '-Dtest=LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,KernelAgentRunServiceTests' test`
- `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentDefinitionRepositoryAdapterTests,JdbcAgentRunRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests,SeahorseSecurityWebMvcConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Playwright CLI direct checks for `http://127.0.0.1:5173/prototype/ai-infra` and `http://127.0.0.1:5173/admin/ai-infra`
- `.\mvnw -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` failed in `seahorse-agent-tests` because runtime classpath could not load existing kernel port classes that are present in `seahorse-agent-kernel/target/classes` and the kernel jar.

Blockers:

- Broad `seahorse-agent-tests` API contract suite remains blocked by existing runtime classpath resolution of kernel inbound ports.

Next step:

- Continue Phase 2 against `02-tool-gateway-policy-engine.md`: add `maxCallsPerRun` enforcement, argument policy enforcement, MCP catalog registration, and storage-backed management APIs. Keep JDBC/web surfaces behind focused RED/GREEN slices.

Drift check:

- Scope: aligned with Phase 0/1 and the first kernel-only part of Phase 2.
- Compatibility: no existing class moved or deleted; no-repository AGENT fallback remains covered; old `KernelAgentLoop` and `LocalToolGatewayPort` constructors remain compatible.
- New owners: documented Agent Registry, Agent Runtime, Tool Gateway, Tool Policy, starter wiring surfaces, stream meta run identity, public prototype route alias, and backend security public-path whitelist.
- Phase boundary: Phase 2 is only partially implemented. Runtime-to-Gateway, minimal policy boundary, catalog metadata, binding metadata, starter catalog-backed policy selection, durable catalog/binding persistence, and invocation audit persistence are covered; call limits, argument policy, MCP catalog registration, management APIs, and HITL remain future slices.
- Decision: continue.
