# Public Port Inventory and Retirement Rules

Updated: 2026-09-13 (metadata governance trio consolidation + agent team feature ports)
Authority: `PortArchitectureTest` and compiled source under
`com.miracle.ai.seahorse.agent.ports`

## Current Inventory

| Direction | Public interfaces | Meaning |
| --- | ---: | --- |
| Inbound | 93 | Capability entry points called by delivery adapters or other capabilities |
| Outbound | 259 | Independently replaceable external/runtime boundaries |
| Common | 1 | Shared boundary outside the directional packages |
| Total | 353 | Reviewed ceiling; must only decrease |

The inventory is measured by the public-interface ArchUnit scan, not by source
file count. The duplicate `SreHealthReportProviderPort` boundary was retired
in favor of `SreHealthInboundPort`; both previously exposed the same
`SreHealthReport current()` operation and shared one implementation.

2026-09-13 agent team feature addition (Multi-Agent A2A design §6 P1): two
ports were added for a genuinely new capability, the first ceiling increase
since the consolidation program began, recorded here with its §6.3
justification so it is not mistaken for consolidation drift:

- `AgentTeamInboundPort` (inbound, 5 operations: createTeam/listTeams/
  getTeam/startTeamRun/getTeamRun) is the team-orchestration capability
  boundary consumed by the web controller; controllers depend on inbound
  contracts only, so a new capability API requires a new inbound Port.
- `AgentTeamRepositoryPort` (outbound, 6 operations) is the persistence
  boundary for `AgentTeamDefinition` and `AgentTeamRun` aggregates with a
  JDBC adapter; replacing the store must not touch the orchestration engine.
- Consolidation balances remain: the four consolidation waves below brought
  legacy ports 360 -> 351, and the feature added 2, for the current 353.

The 786 Java files under `ports` are informational package-hygiene data, not
the Port count. Records, enums, commands, responses, and other value objects do
not become architectural Ports merely because they are stored in that package.

2026-09-13 metadata governance trio consolidation (design §6.2 repository
fragment rule), all three merges verified by same-aggregate evidence below:

- `MetadataExtractionResultManagementRepositoryPort` was merged into
  `MetadataExtractionResultRepositoryPort`. Extraction-result persistence in
  the ingestion pipeline and the governance-side result trace query share the
  same `metadata_extraction_result` table and transaction boundary; the
  merged port holds five cohesive operations and the management-only
  `empty()` default resolves to the existing `noop()` write-side fake. The
  fragment port and its adapter class were deleted.
- `MetadataQuarantineManagementRepositoryPort` was merged into
  `MetadataQuarantinePort`. Quarantine writes from the ingestion pipeline and
  the governance-side item read/resolve/retry-schedule land in the same
  `metadata_quarantine_item` table and transaction boundary; the merged port
  holds five operations and preserves the failing `empty()` semantics for
  resolve/retry. The fragment port and its adapter class were deleted.
- `MetadataReviewManagementRepositoryPort` was merged into
  `MetadataReviewQueuePort`. Review enqueueing and the governance-side item
  read, audit trace, and decision persistence share the same
  `metadata_review_item` table and transaction boundary; the merged port
  holds five operations. The fragment port and its adapter class were
  deleted.

In all three merges `KernelMetadataExtractionResultService`,
`KernelMetadataQuarantineService`, and `KernelMetadataReviewService` are the
single consuming services, and the JDBC governance adapter absorbed the
fragment implementations (the delegate adapter classes in
`JdbcMetadataPortAdapters` were deleted). Port interfaces fell from 354 to
351 (outbound 261 to 258) and `port_java_files_info` from 785 to 782.

2026-09-06 billing aggregate consolidation (design §6.2 repository fragment
rule), both merges verified by same-aggregate evidence below:

- `BillLineItemRepositoryPort` was merged into `BillRepositoryPort`. Line
  items are child entities of the bill aggregate (`sa_bill_line_item`
  references `sa_bill`), the merged port holds six cohesive operations,
  `KernelBillingService` is the single consuming service, and the JDBC and
  MyBatis-Plus adapters each absorbed their fragment implementations (the
  MyBatis-Plus bill bean now requires both mappers). The fragment port and
  its two adapter classes were deleted.
- `PaymentCallbackLogRepositoryPort` was merged into
  `PaymentOrderRepositoryPort`. Callback-log rows are the payment-order
  aggregate's idempotency evidence (`sa_payment_callback_log` is keyed by
  channel + trade_no and written inside the order-state transaction), the
  merged port holds six operations with honest names
  (`callbackAlreadyProcessed`/`recordCallback` replacing the overloaded
  `exists`/`save`), and `KernelPaymentService` is the single consuming
  service. The fragment port and its two adapter classes were deleted.

Port interfaces fell from 358 to 356 (outbound 264 to 262) and
`port_java_files_info` from 789 to 787.

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

2026-09-06 second consolidation wave:

- `MemoryRecallGoldenHarnessInboundPort` was merged into
  `MemoryRecallEvaluationInboundPort` (both are the memory-recall
  evaluation capability; the harness adds profile lookup on top of the
  same report type). The merged port holds three cohesive operations
  (evaluate/runProfile/listProfiles); the harness service is the single
  port bean delegating scoring to the internal evaluation service.
- `SandboxArtifactQueryPort` was merged into `SandboxArtifactPort`
  (write and query views of the same `sa_sandbox_artifact` aggregate,
  one JDBC adapter, the same kernel consumers). The merged port holds
  four operations; `emptyQueries()` preserves the optional query-side
  default; the internal Empty fake class was deleted.
- Cross-subdomain edge dissolution removed four more stale whitelist
  rows (whitelist 44 -> 40, back at the Phase 0 count).

Port interfaces fell from 356 to 354 (inbound 93 to 92, outbound 262 to
261) and `port_java_files_info` from 787 to 785.

## Per-Capability Design Review (2026-09-06)

The mechanical same-aggregate/same-table sweep is exhausted (every JDBC
adapter maps to a disjoint aggregate owner). The remaining 354 -> 300
reduction therefore requires this per-capability design review. Verdicts
below are final for this governance cycle; each KEEP cites the §6.3 rule
that forbids the merge.

| Family | Ports | Verdict | Reason |
| --- | --- | --- | --- |
| quota (agent) | QuotaSummaryInboundPort (1), QuotaManagementInboundPort (3) | KEEP split | user-facing read vs admin policy write — distinct authorization (§6.3) |
| memory governance/trace | MemoryGovernanceInboundPort (3), MemoryTraceInboundPort (1) | KEEP split | governance runs mutate state; trace is a read-only observability query (§6.3) |
| memory recall evaluation | MemoryRecallEvaluationInboundPort (3, merged) | DONE | harness merged this cycle |
| sandbox artifacts | SandboxArtifactPort (4, merged) | DONE | write+query views of one aggregate merged this cycle |
| agent run worker | AgentRunWorkerInboundPort (1) | KEEP | single tick operation is the worker SPI consumed by the scheduler adapter |
| workflow visualization | WorkflowVisualizationInboundPort (1) | KEEP | standalone read-only rendering capability; merging would widen a core port |
| access decision / resource ACL | AccessDecisionQueryInboundPort (1), ResourceAclManagementInboundPort | KEEP split | query vs management CQRS split |
| agentscope invocation | AgentExternalInvocationInboundPort (1) | KEEP | production SPI consumed by the agentscope adapter (§6.2 plugin/SPI) |
| SRE health | SreHealthInboundPort (1) | KEEP | one coherent health-read use case; already de-duplicated once |
| context pack builder | ContextPackBuilderInboundPort (1) | KEEP | internal chat-pipeline collaborator; merging into ChatInboundPort would widen the core chat boundary |
| audit | AuditEventRepositoryPort, AuditLogRepositoryPort | KEEP split | agent audit trail vs admin operation log — distinct domains and lifecycles |
| subscription | SubscriptionRepositoryPort, SubscriptionPlanRepositoryPort | KEEP split | user-state vs catalog aggregates |
| agent definition | AgentDefinitionInboundPort (9, legacy budget) | KEEP | at reviewed operation budget; absorbing siblings would breach it |
| sandbox runtime | SandboxRuntimeInboundPort (12, legacy budget) | KEEP | reviewed legacy budget |
| metadata management trio | MetadataExtractionResultRepositoryPort (5, merged), MetadataQuarantinePort (5, merged), MetadataReviewQueuePort (5, merged) | DONE | write+governance views of one aggregate per port merged this cycle (§6.2) |

| agent team orchestration | AgentTeamInboundPort (5), AgentTeamRepositoryPort (6) | DONE | new P1 capability added this cycle with §6.3 justification (see inventory header) |

Review outcome: 351 was the honest consolidation floor for this cycle, reached
with the metadata trio merge above, without breaching §6.3/§6.5. The agent team
P1 feature then added 2 justified capability ports (351 -> 353). Reaching 300
requires capability-level API redesign (reducing operation counts of
legacy-budget ports and collapsing small capabilities into their parents),
which is a feature-cycle decision, not a consolidation-mechanical one.
