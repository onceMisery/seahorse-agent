# AI Infra ContextPack Foundation Plan

## Goal

Implement the first Phase 4 Context DB slice without duplicating existing Phase 3 work: ContextPack domain, context item invariants, builder ports, JDBC persistence, and read APIs.

## Architecture

Kernel owns domain invariants and ports. JDBC owns persistence only. Web owns HTTP mapping only. Spring starter wires adapters and kernel services.

## Tech Stack

Java 17, Maven, Spring Boot, Spring JDBC, JUnit 5.

## Baseline/Authority Refs

- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- Current Phase 3 implementation already includes approval, checkpoint, resume, WAITING_APPROVAL, and lease primitives.

## Compatibility Boundary

- Do not rewrite existing RAG, Memory, or AgentLoop flows in this slice.
- Keep existing `MemoryContext` path intact.
- Add small interfaces and adapters so later runtime integration can compose through ports.
- ACL default is deny: items without an allow decision do not enter a ContextPack.

## Verification

- `mvn -Dspotless.apply.skip=true -pl seahorse-agent-kernel -Dtest=KernelContextPackBuilderServiceTests test`
- `mvn -Dspotless.apply.skip=true -pl seahorse-agent-adapter-repository-jdbc -Dtest=JdbcContextPackRepositoryAdapterTests test`
- `mvn -Dspotless.apply.skip=true -pl seahorse-agent-adapter-web -Dtest=SeahorseAgentControllerTests test`
- `mvn -Dspotless.apply.skip=true -pl seahorse-agent-spring-boot-starter -am -Dtest=SeahorseAgentRegistryAutoConfigurationTests -Dsurefire.failIfNoSpecifiedTests=false test`

## Tasks

1. Write RED kernel tests for ContextPack builder filtering.
2. Add context domain records/enums and small ports under `ports.inbound.agent` / `ports.outbound.agent`.
3. Implement `KernelContextPackBuilderService` using injected resource access and budget rules.
4. Write RED JDBC tests for saving pack/items and reading them back.
5. Implement `JdbcContextPackRepositoryAdapter` and schema entries for `sa_context_pack`, `sa_context_item`, `sa_access_decision_log`.
6. Write RED Web/starter tests for `/api/context-packs/{id}` and `/items` plus auto-configuration.
7. Implement `SeahorseContextPackController` and starter beans.

## Risks

This slice does not prove Agent prompt uses ContextPack by default. That remains a separate runtime integration slice after the foundation is persisted and queryable.

## Retirement

No old path is retired in this slice. Existing `MemoryContext` remains the compatibility path.
