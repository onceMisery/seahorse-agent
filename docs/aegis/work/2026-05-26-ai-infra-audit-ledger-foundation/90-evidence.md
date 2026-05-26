# AI Infra Audit Ledger Foundation - Evidence

## EvidenceBundleDraft

- RED: kernel tests initially failed at compile time because `AuditEvent`, audit enums, audit repository/query ports, `KernelAuditLedgerService`, Production Gate domain objects, `ProductionGateRepositoryPort`, and `KernelProductionGateService` were missing.
- RED: JDBC tests initially failed at compile time because `JdbcAuditEventRepositoryAdapter` and `JdbcProductionGateRepositoryAdapter` were missing.
- RED: Web tests initially failed at compile time because `SeahorseAuditEventController` and `SeahorseProductionGateController` were missing.
- RED: starter test initially failed because the context had no `JdbcAuditEventRepositoryAdapter` bean.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests,ProductionGateReportTests,KernelProductionGateServiceTests' test` passed with 8 tests.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` passed with 2 tests.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` passed with 2 tests.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` passed with 2 tests.
- Hygiene: `git diff --check` passed; it reported only Windows line-ending warnings.

## Evidence Boundary

This record covers the Phase 8A Audit Ledger foundation slice only. It does not verify full AI Infra completion, Phase 4 dry-run/provenance, Phase 6 publish-ready, Phase 3 worker hardening, Phase 7 handoff, or Phase 8B/C/D.
