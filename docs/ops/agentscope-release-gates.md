# AgentScope Release Gates

This page defines the required checks for AgentScope, A2A, and Nacos changes.

## PR Gate

Run focused unit and contract tests:

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am test
mvn -pl seahorse-agent-kernel `
  "-Dtest=KernelChatAgentRunStoreTests" `
  test
mvn -pl seahorse-agent-tests -am `
  "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" `
  "-DfailIfNoTests=false" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  test
```

Required evidence:

- Upstream kernel tests and AgentScope adapter tests pass in the same reactor.
- `AgentScopeA2aE2eScriptContractTests` passes.
- `AgentScopeA2ALiveSmokeTest` is skipped unless explicitly enabled.
- `KernelChatInboundServiceAgentScopeEngineSmokeTests` passes and proves `ChatMode.AGENT` can route through
  a real `AgentScopeReActExecutor` while preserving StreamCallback output, task handle binding, and RAG bypass.
- `AgentScopeModelBridgeTests` covers Seahorse model `onThinking(...)` preservation through AgentScope `ThinkingBlock`.
- `AgentScopeReActExecutorTests` covers AgentScope thinking events flowing back to Seahorse `StreamCallback.onThinking(...)`.
- `OpenAiCompatibleModelAdapterTests` covers streaming token usage request/parse:
  OpenAI-compatible payload includes `stream_options.include_usage=true`, and SSE `usage` chunks are emitted as Seahorse `ChatTokenUsage`.
- `AgentScopeModelBridgeTests` covers Seahorse model `onUsage(...)` preservation through AgentScope `ChatUsage`.
- `AgentScopeReActExecutorTests` covers AgentScope `ModelCallEndEvent` usage flowing back to Seahorse `StreamCallback.onUsage(...)`.
- `KernelChatAgentRunStoreTests#shouldRecordAgentModeModelUsageForAgentRun` covers Agent mode usage persistence into
  `CostUsageRepositoryPort` with run, rollout, user, and model dimensions.
- `KernelChatAgentRunStoreTests#registeredAgentModeInjectsVersionedSkillSnapshotWithoutGrantingTools` covers Agent run
  metadata snapshots for version instructions, model config, skill set, and runtime tool exposure.
- `KernelChatAgentRunStoreTests#agentRunMetadataIncludesExecutionBackendPromptSourceSnapshot` covers backend-contributed
  prompt source metadata, including Nacos key/version/label/revision, being merged into Agent run metadata.
- `AgentScopeRunMetadataContributorTests` covers Nacos config-center prompt and skill repository source snapshots.
- `AgentScopeReActAutoConfigurationTests#configCenterCreatesPromptProviderAndNacosSkillRepositoryWhenEnabled` covers
  auto-configured registration of the AgentScope run metadata contributor.
- `AgentScopeReActExecutorTests#streamExecuteRecordsAgentscopeExecuteObservationWithRunDimensions` covers streaming
  `agentscope.execute` observation.
- `AgentScopeReActExecutorTests#streamExecuteClassifiesAgentscopeRuntimeErrorsAsRecoverableEvents` covers streaming
  AgentScope runtime errors being classified as Seahorse `recoverable_error` events before `StreamCallback.onError(...)`.
- `A2aAgentRemoteInvokerTests#recordsA2aInvokeObservationWithRemoteDimensions` covers `a2a.invoke` observation.
- `AgentScopeA2aServerControllerTests#recordsA2aAuthObservationWithAgentDimensions` covers `a2a.auth` observation.
- `AgentScopeA2AAgentConnectorTests` covers exact version resolution and prevents silent fallback to latest when a requested version is missing.
- `AgentScopeA2AAgentConnectorTests` covers TTL stale filtering so expired candidates lose to fresh candidates when both are discovered.
- `AgentScopeAgentCardFactoryTests` covers optional `registration-ttl` metadata (`registeredAt`, `expiresAt`).
- `AgentScopeAgentCardRegistrarTests` covers duplicate registration governance:
  same tenant/name/version/url is idempotent, same tenant/name/version with a different url is rejected by default, and `duplicate-registration-policy=replace` allows controlled replacement.
- `AgentScopeAgentCardRegistrarTests` covers lifecycle deregistration:
  after a successful registration, Spring destroy calls Nacos 3.x `A2aService.deregisterAgentEndpoint(...)` with the same endpoint fields used by registration, and disabled endpoint registration skips deregistration.
- `AgentScopeReActAutoConfigurationTests` covers that the auto-configured registrar receives the existing `AiService`, so deregistration is wired in the real Spring bean graph.
- `AgentScopeConfigCenterStartupValidatorTests` covers config-center strict startup validation:
  configured prompt is loaded before serving traffic, configured skill namespace must expose skills, and non-strict startup keeps fallback behavior.

The same PR gate can be run through the repository script:

```powershell
.\scripts\agentscope-release-gate.ps1
```

## Nightly Live A2A Gate

Run the deployed local compose stack and execute:

```powershell
.\scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9090/a2a `
  -RemotePort 9092 `
  -NacosServer 127.0.0.1:8848 `
  -TenantId tenant-a `
  -SharedSecret seahorse-local-a2a-token
```

Required final lines:

```text
REMOTE_DIRECT_OK
NACOS_CONNECTOR_SMOKE_OK
E2E_RESULT=PASS
```

The script must leave no `seahorse-a2a-e2e-*` containers after success or failure.

The live gates can also be run through:

```powershell
.\scripts\agentscope-release-gate.ps1 -IncludeLive
.\scripts\agentscope-release-gate.ps1 -IncludeLive -IncludeTenantSigned
```

## Release Smoke Gate

Before marking an AgentScope release healthy, collect evidence for:

- Backend starts and `/actuator/health` is `UP`.
- `GET /a2a` returns a public Agent Card.
- Main Agent Card contains tenant and M3 metadata.
- When `SEAHORSE_AGENTSCOPE_A2A_REGISTRATION_TTL` is configured, Main Agent Card contains `registeredAt` and `expiresAt` A2A metadata.
- When `SEAHORSE_AGENTSCOPE_A2A_DUPLICATE_REGISTRATION_POLICY=reject`, conflicting registrations for the same tenant/name/version are rejected before calling AgentScope registration.
- Registered A2A endpoints are deregistered through Nacos `A2aService` when the backend context is destroyed.
- When `SEAHORSE_AGENTSCOPE_CONFIG_CENTER_STRICT_STARTUP=true`, missing Nacos prompt or empty skill namespace fails startup with a clear AgentScope config-center strict startup error.
- Missing A2A auth is rejected.
- Wrong A2A auth is rejected.
- Correct A2A auth succeeds.
- Temporary remote backend registers through Nacos.
- Nacos resolver finds the remote Agent Card.
- Remote JSON-RPC `message/send` succeeds.
- Agent mode token usage is persisted in the cost usage repository for run-level cost and quota summaries.
- Temporary E2E containers are cleaned.

## Tenant-Signed Gate

When deploying with `SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE=tenant-signed`, rerun the same E2E command with:

```powershell
.\scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9090/a2a `
  -RemotePort 9092 `
  -NacosServer 127.0.0.1:8848 `
  -TenantId tenant-a `
  -SharedSecret seahorse-local-a2a-token `
  -AuthMode tenant-signed `
  -MainAgentName seahorse-agent
```

The main backend must also be deployed with matching tenant, agent name, and shared signing secret.

Required final lines:

```text
MAIN_POST_AUTH=200
REMOTE_POST_AUTH=200
NACOS_CONNECTOR_SMOKE_OK
E2E_RESULT=PASS
```

PowerShell signing must use an explicit `HMACSHA256` constructor with UTF-8 key bytes. Do not use
`[System.Security.Cryptography.HMACSHA256]::Create()` because it can resolve through platform crypto
configuration and produce a non-SHA256 HMAC on some Windows/.NET environments.
