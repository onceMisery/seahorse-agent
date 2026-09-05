# Public Port Inventory and Retirement Rules

Updated: 2026-09-06
Authority: `PortArchitectureTest` and compiled source under
`com.miracle.ai.seahorse.agent.ports`

## Current Inventory

| Direction | Public interfaces | Meaning |
| --- | ---: | --- |
| Inbound | 93 | Capability entry points called by delivery adapters or other capabilities |
| Outbound | 264 | Independently replaceable external/runtime boundaries |
| Common | 1 | Shared boundary outside the directional packages |
| Total | 358 | Reviewed ceiling; must only decrease |

The inventory is measured by the public-interface ArchUnit scan, not by source
file count. The duplicate `SreHealthReportProviderPort` boundary was retired
in favor of `SreHealthInboundPort`; both previously exposed the same
`SreHealthReport current()` operation and shared one implementation.

The 789 Java files under `ports` are informational package-hygiene data, not
the Port count. Records, enums, commands, responses, and other value objects do
not become architectural Ports merely because they are stored in that package.

The current source tree is synchronized with `origin/main` at `22b99f0c`. That
upstream split retired the unused `DistributedSemaphorePort` boundary and moved
coordination value types out of the kernel Port package. The compiled-source
scan therefore moved the inventory from the previous 361 (93/267/1) to 360
(93/266/1); the historical reductions below remain unchanged.

Slice 1 removed `AuthRefreshInboundPort` and its separate service owner.
Refresh now belongs to the existing `AuthInboundPort` authentication use case,
reducing one interface and one implementation without increasing that Port
beyond three cohesive operations.

Slice 2 adds no Port interface. Reliable dispatch reuses
`OutboxEventRepositoryPort`, and ingestion finalization is an aggregate operation
on the existing `IngestionTaskRepositoryPort`. No cleanup Port, channel-evidence
Port, or broad ingestion facade was introduced.

Slice 3 retired `DocumentChangeListenerPort` after repository-wide source,
Spring configuration, reflection, and test searches found no consumer or
adapter. Document lifecycle behavior is already owned by the knowledge document
application service and ingestion task aggregate; the unused listener boundary
provided no replaceable runtime capability.

Slice 3 also retired `ConnectorCredentialVerificationPort` after a repository-
wide search found only its declaration. No application service, adapter, Spring
binding, reflection/ServiceLoader entry, test, or documentation consumer exists;
the migration comment describing credential verification is not an implemented
runtime contract.

Slice 4 internalized `EnhancementPromptPort` and `EnrichmentPromptPort` after
finding both were pure internal templates with no adapter implementation (only
static `defaults()`), matching the single-owner package-private collaborator
rule. The ingestion feature nodes now call the static
`IngestionPromptTemplates` methods directly, and the plugin auto-configuration
no longer registers prompt beans. Total Port interfaces fell from 367 to 365 and
`port_java_files_info` from 799 to 797. A repository-wide scan found no dead
Ports (every Port has at least two consumers) and no single-consumer Ports, so
the remaining reduction requires aggregate-level repository consolidation.

Slice 5 consolidated inbound use-case ports. `ContextPackQueryInboundPort`,
`ContextPackRetentionInboundPort`, and `ContextPackDiffInboundPort` were merged
into `ContextPackInboundPort` (one implementation, one controller, four cohesive
operations), and `PaymentInboundPort` + `SubscriptionInboundPort` were merged
into `PaymentSubscriptionInboundPort` behind a `KernelPaymentSubscriptionFacade`
(six cohesive billing operations). These merges align with design §6.3
(inbound ports align with user-observable use cases, not individual Controller
methods). Total Port interfaces fell from 370 to 367 and `port_java_files_info`
from 802 to 799.

`FeedbackEvaluationCandidateQueryInboundPort` was merged into
`MessageFeedbackInboundPort` behind a `KernelMessageFeedbackFacade`, reducing
the feedback capability to one cohesive inbound port. Port interfaces fell from
367 to 366 and `port_java_files_info` from 799 to 798.

`AgentRolloutCostSummaryInboundPort` was merged into `AgentRolloutInboundPort`
behind a `KernelAgentRolloutFacade`, reducing the rollout capability to one
cohesive inbound port. Port interfaces fell from 366 to 365 and
`port_java_files_info` from 798 to 797.

`AgentArtifactQueryInboundPort` + `AgentArtifactUpdateInboundPort` were merged
into `AgentArtifactInboundPort` behind a `KernelAgentArtifactFacade`, reducing
the artifact capability to one cohesive inbound port. Port interfaces fell from
365 to 364 and `port_java_files_info` from 797 to 796.

`AgentCheckpointQueryInboundPort` + `AgentRunCostSummaryInboundPort` +
`AgentRunResumeInboundPort` were merged into `AgentRunQueryInboundPort` behind a
`KernelAgentRunQueryFacade` combining checkpoint query, cost summary, and resume
services. Port interfaces fell from 364 to 362 and `port_java_files_info` from
796 to 794.

2026-09-06 aggregate-level repository consolidation (design §6.2 repository
fragment rule), both merges verified by the same-table evidence below:

- `AgentRunQueueRepositoryPort` was merged into `AgentRunLeaseRepositoryPort`.
  Both fragments operate on the `agent_run_lease` claim aggregate: the queue
  adapter's runnable-run query joins `sa_agent_run` with `sa_agent_run_lease`,
  and the lease adapter owns the claim rows. The merged port holds five
  cohesive operations (acquire/heartbeat/release/findByRunId/findRunnable),
  one JDBC adapter (`JdbcAgentRunLeaseRepositoryAdapter`), and the worker
  service consumes it together with the lease inbound port. The queue port,
  its JDBC adapter, and its test class were deleted; the two findRunnable
  H2 regression tests moved into `JdbcAgentRunLeaseRepositoryAdapterTests`.
- `MemoryReviewCandidatePort` was merged into
  `MemoryReviewManagementRepositoryPort`. The one-operation write fragment
  (`save`) and the review management read/decision operations belong to the
  same `memory_review_candidate` aggregate and the same single JDBC adapter.
  The merged port now exposes `noop()` as the single `NoopFallback`-marked
  fallback, so the Class A NoOp guard watches the same binding the memory
  engine and review services consume; the Class A fail-fast semantics are
  unchanged.

Port interfaces fell from 360 to 358 (outbound 266 to 264) and
`port_java_files_info` from 791 to 789.

## Retention Test

A public Port may remain only when it represents at least one of:

- an external side effect or production SPI;
- an independently replaceable adapter boundary;
- an inbound capability boundary with distinct ownership, transaction, or
  failure semantics.

Single-implementation internal coordination is internalized when touched.
Records, enums, and value objects leave `ports` when their owning capability is
refactored. Interfaces must not be merged across capabilities or replaced by a
generic `Map<String, Object>` carrier to satisfy the count.

## Operation Budget

New and modified public Ports have at most eight declared abstract operations.
The following existing interfaces are frozen at their current reviewed budget;
their operation count may decrease but not increase:

| Legacy Port | Budget |
| --- | ---: |
| `inbound.agent.AgentDefinitionInboundPort` | 9 |
| `inbound.agent.SandboxRuntimeInboundPort` | 12 |
| `inbound.agent.skill.AgentSkillManagementInboundPort` | 10 |
| `inbound.knowledge.KnowledgeDocumentInboundPort` | 10 |
| `inbound.metadata.MetadataReviewInboundPort` | 9 |
| `inbound.retrieval.RetrievalEvaluationDatasetInboundPort` | 10 |
| `inbound.task.TaskInboundPort` | 9 |
| `outbound.admin.AdminRepositoryPort` | 11 |
| `outbound.agent.AgentDefinitionRepositoryPort` | 9 |
| `outbound.agent.AgentSkillRepositoryPort` | 9 |
| `outbound.agent.ConnectorRepositoryPort` | 9 |

## Retirement Target

The final target is no more than 300 real public Port interfaces. At least 62
interfaces must therefore be deleted or internalized semantically. Each
capability slice records its before/after count and the owner/consumer evidence
for every retained compatibility boundary. A count decrease caused only by a
file move, a God interface, or a generic carrier is rejected.

## Verification

```powershell
.\mvnw.cmd -pl seahorse-agent-architecture-tests -Dtest=PortArchitectureTest test
bash scripts/complexity-report.sh
```

`PortArchitectureTest` is authoritative because it inventories compiled public
interfaces. The shell report is a CI/reporting ratchet and must preserve the
same directional baseline fields when `--update-baseline` is used.
