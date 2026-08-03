# ADR-002: Port Classification, Consolidation Rules, and Public Port Budget

Status: Accepted  
Date: 2026-08-03  
Source: `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md` §6

## Context

The `ports` source tree contained 809 Java files mixing actual abstractions with
request/response records, enums, helpers, repository fragments, readiness marker
interfaces, and internal coordination seams. Port proliferation is itself an
architecture defect; freezing the count is insufficient.

## Decision

A Port is a public interface that crosses at least one of these boundaries:

1. an external side effect or infrastructure dependency;
2. an independently replaceable runtime adapter;
3. a plugin/SPI extension boundary used by production extensions;
4. a capability boundary with different ownership, transaction, or failure
   semantics.

Tests, mocking convenience, naming consistency, and a desire to split a large
class are not sufficient reasons for a public Port.

Classification and disposition:

| Classification | Disposition |
| --- | --- |
| External infrastructure Port | Keep narrow and behavior-oriented |
| Production plugin/SPI Port | Keep with compatibility and lifecycle tests |
| Capability boundary Port | Keep only when ownership/failure semantics differ |
| Single implementation and single production consumer | Convert to package-private collaborator |
| Repository fragment for the same aggregate | Merge under the aggregate repository owner |
| Marker interface for evidence/readiness | Replace with one typed contributor contract |
| Request/response/record/enum/options | Move to a contract/domain package when touched |
| Compatibility-only Port | Keep with consumer list and retirement trigger |
| Unused or test-only Port | Delete |

The public Port budget is a baseline of 377 actual Port interfaces, a
core-stabilization target of no more than 300 repository-wide, and a directional
target of at least a 20% reduction concentrated in touched core capabilities.

Anti-gaming checks forbid God interfaces, `Map<String, Object>` carriers,
test-only retention, adapter-type leakage into the kernel, and reduction claims
without consumer/implementation/dependency-edge evidence.

## Consequences

- Records, enums, and classes moved out of `ports` are reported separately as
  conceptual-boundary cleanup, not Port reduction.
- The metric is implemented using a structured Java/ArchUnit inventory, not raw
  directory line or file counts.
- No slice may finish with a higher repository or touched-capability Port count.

## Verification

- `PortArchitectureTest` enforces the reviewed ceiling, the eight-operation
  budget, and the structured baseline.
- The complexity baseline (`complexity-baseline.txt`) ratchets downward only.

## Current State (2026-08-03)

Port interfaces fell from 367 to 365 after converting the ingestion prompt
interfaces to package-private collaborators. Repository-wide analysis found no
dead or single-consumer Ports; the remaining 365-to-300 gap requires
aggregate-level repository consolidation and is deferred to a dedicated slice.
