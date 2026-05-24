# EvidenceBundleDraft

Verification so far:
- Kernel context ACL/query tests passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultResourceAccessPolicyPortTests,KernelContextPackBuilderServiceTests,KernelContextPackQueryServiceTests' test`
- Spring registry auto-configuration test passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Diff check passed:
  `git diff --check`
- Broader focused regression passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=DefaultResourceAccessPolicyPortTests,KernelContextPackBuilderServiceTests,KernelContextPackQueryServiceTests,KernelChatPipelineTests,KernelChatInboundServiceAgentModeTests,SeahorseAgentControllerTests,SeahorseWebApiContractTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Changed production files:
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/DefaultResourceAccessPolicyPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/KernelContextPackQueryService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/context/ResourceAccessReasonCodes.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelRegistryAutoConfiguration.java`

Changed test files:
- `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/DefaultResourceAccessPolicyPortTests.java`
- `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/KernelContextPackBuilderServiceTests.java`
- `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/KernelContextPackQueryServiceTests.java`
- `seahorse-agent-spring-boot-starter/src/test/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentRegistryAutoConfigurationTests.java`

Pending evidence:
- Commit and merge evidence.
