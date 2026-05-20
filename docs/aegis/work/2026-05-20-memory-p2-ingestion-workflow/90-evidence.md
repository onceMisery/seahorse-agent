# Memory P2 ingestion workflow - Evidence

## Code Evidence

- Added `MemoryIngestionAction`, `MemoryOperationType`, `MemoryOperationStatus`, `MemoryOperation`, and `MemoryOperationLogPort`.
- Extended `MemoryIngestionCommand` with `operationId`, `tenantId`, `source`, `chatCompleted()`, and `toolWrite()`.
- Extended `MemoryIngestionResult` with workflow action and details while preserving status/reason/operations factories.
- Added deterministic workflow components:
  - `MemorySanitizer`
  - `MemoryPreFilter`
  - `MemorySemanticClassifier`
  - `MemorySchemaValidator`
- Refactored `DefaultMemoryEnginePort` to implement `MemoryIngestionWorkflowPort` and gate writes through operation idempotency, filtering, classification, schema validation, and deterministic writes.
- Added `JdbcMemoryOperationLogRepositoryAdapter`, `t_memory_operation_log`, and schema-upgrade DDL.
- Updated chat capture and `memory_write` to prefer `MemoryIngestionWorkflowPort` while keeping compatibility fallbacks.
- Updated Spring auto-configuration to register the workflow and operation log port.
- Preserved existing `memory_write.policyDecision=ALLOW_IF_CAPTURE_POLICY_ACCEPTS` and added `ingestionStatus`/`ingestionAction` for P2 workflow results.

## Verification Evidence

### Targeted P2 Regression

Command:

```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests,MemoryCapturePolicyTests,KernelMemoryGovernanceServiceTests,MemoryWorkflowRoutingTests,AgentToolPortAdapterTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist,SeahorseAgentNativeAdapterAutoConfigurationTests#shouldRegisterJdbcKnowledgeRepositoryWhenDataSourceExists" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result:

- Exit status: 0
- `BUILD SUCCESS`
- Tests run: 51
- Failures: 0
- Errors: 0
- Skipped: 0

Notes:

- `DefaultMemoryEnginePortTests.shouldGracefullyDegradeWhenLayerThrowsException` intentionally logs a simulated long-term memory load failure to verify graceful degradation.

### Packaging

Command:

```powershell
.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"
```

Result:

- Exit status: 0
- `BUILD SUCCESS`
- Reactor modules through `seahorse-agent-spring-boot-starter`: success

### Tool Compatibility Follow-Up

Command:

```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=AgentToolPortAdapterTests#memoryWriteSubmitsIngestionCommandWhenWorkflowExists,AgentToolPortAdapterTests#memoryWriteDelegatesToCapturePolicyWithServerScope" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result:

- Exit status: 0
- `BUILD SUCCESS`
- Tests run: 2
- Failures: 0
- Errors: 0

### Diff Hygiene

Command:

```powershell
git diff --check
```

Result:

- Exit status: 0
- No whitespace errors.
- Windows CRLF conversion warnings were reported for touched files.

## Not Covered

- Live Docker redeploy and browser/API memory round-trip validation.
- LLM refiner behavior; P2 implementation is deterministic/rule-based.
- P3 read-path router and context weaver beyond the existing P1 skeleton.

Method Pack evidence does not grant completion authority.
