# ADR-001: Core Agent/RAG Runtime Production Boundary and One-Way Quarantine Dependency

Status: Accepted  
Date: 2026-08-03  
Source: `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md` §4, §5, §11

## Context

The repository accumulated 2,188 backend production Java files with material
architecture drift: too many public boundaries for internal coordination,
duplicated runtime and frontend contract owners, feature breadth larger than the
verified production surface, compatibility and no-op paths without retirement
evidence, and quality gates that historically allowed false-green outcomes.

The previous governance cycle treated class presence as evidence of
production readiness. A release claim is invalid until the core runtime is the
only production commitment for the current cycle, backed by honest gates and
real infrastructure evidence.

## Decision

The core Agent/RAG runtime is the production boundary for this governance
cycle. It covers authentication and tenant isolation; knowledge ingestion,
durable source storage, indexing, retrieval, and citations; conversation
persistence, model context construction, model execution, and SSE; governed tool
execution, approval, sandboxing, artifacts, and audit evidence; and
cancellation, idempotency, restart recovery, and dual-instance behavior.

Non-core capabilities (marketplace, billing, experiments, advanced
administration, optional multi-agent integrations) are quarantined behind a
one-way dependency: they may call stable core use cases, but must not be
required by the core runtime.

Dependency direction is enforced:

```text
Core client -> HTTP/SSE adapter -> core inbound use cases
  -> necessary outbound Ports -> infrastructure adapters
Web depends on inbound use-case contracts, never concrete Kernel*Service
  implementations
Core application code does not depend on non-core application subdomains
Bootstrap/AutoConfiguration is assembly only, never a second business-policy
  owner
```

## Consequences

- Core startup and golden paths do not require non-core beans, schema, routes,
  or external dependencies.
- Disabled non-core endpoints return a stable feature-disabled result.
- Quarantined code may receive security and isolation fixes but no feature
  growth.
- Every compatibility-only endpoint or Port carries a consumer inventory and
  retirement trigger.

## Verification

- Controller-to-Kernel implementation dependencies must reach zero (ArchUnit
  `R5ControllerDependencyTest`).
- The 40-entry cross-domain whitelist only decreases.
- Core architecture tests (`R1KernelIsolationTest`, `R2DomainIsolationTest`,
  `R3SubdomainIsolationTest`, `R4AdapterIsolationTest`) pass.
