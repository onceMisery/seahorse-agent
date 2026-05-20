# Memory P3 router context weaver - Evidence

## RED

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryWorkflowRoutingTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Result:
  Failed as expected before implementation.
- Failure signals:
  - `shouldRouteProfileQuestionsToStrongFactTracks` expected `EPISODIC=false`, current router returned true.
  - `shouldRouteGeneralChatToShortWindowWithoutProfileOrEpisodicTracks` expected `EPISODIC=false`, current router returned true.
  - `shouldWeavePriorityZonesWithinBudget` expected `[Correction Ledger]`, `[Profile KV]`, `[Short Window]`, and low-priority long memory trimming; current weaver used flat formatter and substring truncation.

## GREEN

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryWorkflowRoutingTests,KernelAgentLoopTests#memoryContextIsInjectedThroughConfiguredContextWeaver" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Result:
  BUILD SUCCESS.
- Scope:
  7 tests passed: router, Context Weaver, deterministic ingestion compatibility, and AgentLoop custom ContextWeaver injection.

## Regression

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryWorkflowRoutingTests,DefaultMemoryEnginePortTests,KernelAgentLoopTests,KernelChatPipelineTests,LocalRagPromptAdapterTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Result:
  BUILD SUCCESS.
- Scope:
  55 tests passed across memory engine, router/weaver, AgentLoop, chat pipeline, local RAG prompt, and Spring auto-configuration.
- Note:
  `DefaultMemoryEnginePortTests.shouldGracefullyDegradeWhenLayerThrowsException` intentionally logs a long-term memory load warning to verify degradation behavior.

## Package

- Command:
  `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`
- Result:
  BUILD SUCCESS.

## Whitespace

- Command:
  `git diff --check`
- Result:
  Exit 0. CRLF warnings only.

Method Pack evidence does not grant completion authority.
