# EvidenceBundleDraft

- Baseline read: handoff document identifies Approval query/decision API as the next recommended slice.
- Baseline command: `mvn -pl seahorse-agent-kernel '-Dtest=KernelToolInvocationAuditQueryServiceTests,LocalToolGatewayPortAuditTests' test`.
- Baseline result: 7 tests, 0 failures, 0 errors.
- Known verification issue: Maven wrapper fails in PowerShell before Maven startup; use system `mvn` for current local verification unless wrapper is fixed separately.
- Kernel verification: `mvn -pl seahorse-agent-kernel '-Dtest=KernelAgentRunResumeServiceTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,LocalToolGatewayPortAuditTests,KernelApprovalManagementServiceTests,KernelAgentRunServiceTests' test`.
- Kernel result: 26 tests, 0 failures, 0 errors.
- JDBC verification: `mvn '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentCheckpointRepositoryAdapterTests,JdbcToolApprovalRequestRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`.
- JDBC result: 7 tests, 0 failures, 0 errors.
- Web verification: `mvn '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`.
- Web result: 6 tests, 0 failures, 0 errors.
- Spring starter verification: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`.
- Spring starter result: 8 tests, 0 failures, 0 errors.
- Coverage summary: approval management API, JDBC approval query/decision and latest run-step lookup, checkpoint persistence, WAITING_APPROVAL interruption, resume from checkpoint, Web run resume/checkpoint endpoints, and Spring auto-configuration wiring.
- Not covered: full repository-wide test suite, real PostgreSQL migration execution, worker lease, resource ACL enforcement, and production-grade output desensitization beyond the checkpoint argument redaction rule.
