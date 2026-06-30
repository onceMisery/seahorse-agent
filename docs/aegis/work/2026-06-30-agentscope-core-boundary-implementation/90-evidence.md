# AgentScope core boundary implementation - Evidence

## Verification Commands

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-kernel -Dtest=LocalGovernedToolExecutionPortTests -DfailIfNoTests=false test`
- Exit Status: 0
- Covered: Kernel governed tool facade focused test.
- Not Covered: broader kernel tool gateway regression.
- Residual Risk: bounded by later broader kernel and release-gate checks.
- Confidence: B

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeReActAutoConfigurationTests,AgentScopeToolFactoryTests,AgentScopeReActExecutorTests,AgentScopeA2aServerRunnerTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Exit Status: 0
- Covered: AgentScope core-only autoconfiguration, tool bridge/factory compatibility, ReAct executor, and A2A runner tests.
- Result: 36 tests run, 0 failures, 0 errors, 0 skipped.
- Not Covered: full adapter suite.
- Residual Risk: bounded by full adapter test below.
- Confidence: B

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-kernel "-Dtest=LocalGovernedToolExecutionPortTests,LocalToolGatewayPortPolicyTests,KernelAgentLoopToolGatewayTests" "-DfailIfNoTests=false" test`
- Exit Status: 0
- Covered: governed facade plus existing tool gateway and Kernel agent loop tool policy behavior.
- Result: 27 tests run, 0 failures, 0 errors, 0 skipped.
- Not Covered: full kernel module in this exact command.
- Residual Risk: bounded by adapter full suite and release gate, both exercising kernel dependencies.
- Confidence: B

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-adapter-agent-agentscope -am test`
- Exit Status: 0
- Covered: full AgentScope adapter suite and upstream kernel test suite in the reactor.
- Result: kernel 495 tests passed; AgentScope adapter 106 tests run, 0 failures, 0 errors, 1 existing live test skipped.
- Not Covered: live A2A integration without explicit `-IncludeLive`.
- Residual Risk: live A2A remains environment-dependent.
- Confidence: A for non-live adapter behavior.

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-tests -am "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Exit Status: 0
- Covered: cross-module Kernel chat route smoke for `engine=agentscope`.
- Result: smoke test 1 run, 0 failures, 0 errors, 0 skipped; 27-module reactor success.
- Not Covered: exhaustive E2E chat matrix.
- Residual Risk: broader product E2E remains outside this slice.
- Confidence: B

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-spring-boot-autoconfigure -am test`
- Exit Status: 0
- Covered: Spring autoconfiguration module and its upstream reactor dependencies.
- Result: Spring autoconfigure 106 tests run, 0 failures, 0 errors, 0 skipped; reactor success.
- Not Covered: modules outside the autoconfigure dependency graph.
- Residual Risk: bounded by release gate and smoke checks.
- Confidence: A for autoconfigure surfaces.

Evidence Card:

- Command / Check: `rg -n "ToolApprovalRequestRepositoryPort|ToolPolicyPort|ApprovalRequestQueryPort|ToolGatewayPort" seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope`
- Exit Status: 1, expected no-match status.
- Covered: AgentScope adapter no longer directly imports or references Seahorse tool policy, approval repository/query, or tool gateway ports in main AgentScope package.
- Not Covered: generated files or test fixtures.
- Residual Risk: future changes should keep this as a release-gate style check.
- Confidence: A

Evidence Card:

- Command / Check: `rg -n "ReActExecutorPort|AgentLoopRequest|streamExecute|resolveReActExecutor" seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeA2aServerRunner.java seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeAutoConfigurationSupport.java`
- Exit Status: 1, expected no-match status.
- Covered: A2A runner/support no longer directly construct loop requests or resolve/invoke ReAct executors.
- Not Covered: other AgentScope classes where ReAct executor references are legitimate core executor implementation.
- Residual Risk: future A2A changes need this boundary scan.
- Confidence: A

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=KernelAgentExternalInvocationInboundServiceTests,AgentScopeA2aServerControllerTests,AgentScopeA2aServerRunnerTests,AgentScopeReActAutoConfigurationTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Exit Status: 0
- Covered: Kernel external invocation identity/default agent mapping, A2A controller auth behavior, A2A runner boundary, and split AgentScope autoconfiguration ordering.
- Result: Kernel focused tests 4 run, 0 failures; AgentScope focused tests 29 run, 0 failures.
- Not Covered: live A2A transport; covered by Docker E2E below.
- Residual Risk: bounded by live shared-secret and tenant-signed E2E plus release gate.
- Confidence: A

Evidence Card:

- Command / Check: `mvn -pl seahorse-agent-bootstrap -am package -DskipTests "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"`
- Exit Status: 0
- Covered: bootstrap executable jar packaging after A2A ordering and external invocation fixes.
- Not Covered: tests were skipped in this packaging command; covered by targeted tests and release gate.
- Residual Risk: none known for packaging.
- Confidence: A

Evidence Card:

- Command / Check: Docker full backend smoke after injecting the rebuilt `seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar` into `seahorse-agent-backend:latest`, with `SEAHORSE_AGENTSCOPE_A2A_ENABLED=true` and mock AI adapter.
- Exit Status: 0
- Covered: real Spring Boot/Tomcat route registration for `GET http://127.0.0.1:9090/a2a`.
- Result: backend health became `healthy`; `/a2a` returned HTTP 200 with Agent Card `tenant-a/seahorse-a`.
- Not Covered: full remote connector flow; covered by E2E below.
- Residual Risk: local Docker image was refreshed by jar injection because `docker compose build backend` was blocked by Docker Hub metadata auth/network timeout.
- Confidence: A

Evidence Card:

- Command / Check: `.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token -AuthMode shared-secret -MainAgentName seahorse-a`
- Exit Status: 0
- Covered: live full-Docker A2A shared-secret auth path, main and remote Agent Cards, unauthenticated/wrong-token rejection, authenticated JSON-RPC execution, direct remote call, Nacos discovery, and connector smoke.
- Result: `MAIN_CARD_OK=200`, `MAIN_POST_NO_AUTH=401`, `MAIN_POST_WRONG_TOKEN=401`, `MAIN_POST_AUTH=200`, `REMOTE_CARD_OK=200`, `REMOTE_POST_AUTH=200`, `REMOTE_DIRECT_OK`, `NACOS_CONNECTOR_SMOKE_OK`, `E2E_RESULT=PASS`.
- Not Covered: tenant-signed auth; covered by next card.
- Residual Risk: local Docker/Nacos dependent.
- Confidence: A

Evidence Card:

- Command / Check: `.\scripts\agentscope-a2a-e2e.ps1 -MainUrl http://127.0.0.1:9090/a2a -RemotePort 9092 -NacosServer 127.0.0.1:8848 -TenantId tenant-a -SharedSecret seahorse-local-a2a-token -AuthMode tenant-signed -MainAgentName seahorse-a`
- Exit Status: 0
- Covered: live full-Docker A2A tenant-signed auth path, main and remote Agent Cards, unauthenticated/wrong-signature rejection, authenticated JSON-RPC execution, direct remote call, Nacos discovery, and connector smoke.
- Result: `MAIN_CARD_OK=200`, `MAIN_POST_NO_AUTH=401`, `MAIN_POST_WRONG_TOKEN=401`, `MAIN_POST_AUTH=200`, `REMOTE_CARD_OK=200`, `REMOTE_POST_AUTH=200`, `REMOTE_DIRECT_OK`, `NACOS_CONNECTOR_SMOKE_OK`, `E2E_RESULT=PASS`.
- Not Covered: non-local external Nacos deployment.
- Residual Risk: local Docker/Nacos dependent.
- Confidence: A

Evidence Card:

- Command / Check: `git diff --check`
- Exit Status: 0
- Covered: whitespace conflict/error scan.
- Not Covered: line-ending warnings from the existing Windows checkout are not semantic diff errors.
- Residual Risk: none known.
- Confidence: A

Evidence Card:

- Command / Check: `.\scripts\agentscope-release-gate.ps1`
- Exit Status: 0
- Covered: AgentScope unit gate, kernel run contracts, application smoke, and bootstrap packaging.
- Result: `AGENTSCOPE_RELEASE_GATE=PASS`.
- Not Covered: live A2A E2E because `-IncludeLive` was not requested.
- Residual Risk: live shared-secret and tenant-signed A2A remain environment-dependent checks.
- Confidence: A for default release gate.

## Acceptance Mapping

- Core-only startup without optional integration beans: covered by `AgentScopeReActAutoConfigurationTests#coreOnlyExecutorDoesNotCreateOptionalIntegrationBeans`.
- AgentScope tool calls through governed facade: covered by `AgentScopeToolFactoryTests` and `LocalGovernedToolExecutionPortTests`.
- AgentScope adapter no direct policy/approval/gateway dependencies: covered by boundary scan.
- Model calls remain through `StreamingChatModelPort`: preserved by `AgentScopeModelBridge` tests and unchanged bridge ownership.
- A2A enters Kernel inbound port: covered by `AgentScopeA2aServerRunnerTests` and boundary scan.
- Default `engine=kernel` rollback path: preserved by Kernel chat executor selection and A2A no longer forcing a preferred engine.
- `engine=agentscope` route smoke: covered by `KernelChatInboundServiceAgentScopeEngineSmokeTests`.
- Optional A2A/config-center/studio behavior: covered by full AgentScope adapter suite and release gate.

## Residual Risk

- M4 physical Maven module split is intentionally deferred. The current slice lands the logical split and compatibility aggregate, which lowers behavior risk before packaging risk.
- Live A2A shared-secret and tenant-signed E2E passed in the local full-Docker environment. Non-local Nacos/A2A deployments remain environment-dependent.

Method Pack output does not grant completion authority.
