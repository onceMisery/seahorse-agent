# Docs Implementation Audit - Intent

## TaskIntentDraft

- Requested outcome: Audit docs/README.md and related docs, implement remaining documented gaps when feasible, and update docs to reflect true implementation status.
- Goal: Docs and implementation agree on current Seahorse Agent capability status, with remaining metadata JDBC governance work either implemented or accurately externalized.
- Success evidence:
- Focused metadata JDBC tests pass, Spring metadata auto-configuration tests pass, touched docs describe implemented and remaining items accurately, and diff hygiene checks report no whitespace errors.
- Stop condition: done when evidence covers the implemented slice and docs are synchronized; blocked if a remaining documented feature needs unclear product or architecture decisions; needs-verification if implementation exists but tests cannot run; scope-exceeded if completing all historical roadmap items would exceed this docs audit task.
- Non-goals:
- Do not refactor unrelated dirty worktree files or implement every historical roadmap item unrelated to docs/README.md current audit.
- Scope: docs/README.md, docs/DEVELOPMENT-GUIDE.md, docs/zh/content/架构设计/未来规划审计与剩余设计.md, Spring metadata auto-configuration, and JDBC metadata governance repository adapters/tests.
- Change kinds:
- architecture-governance
- Risk hints:
- Metadata JDBC ports are shared persistence adapters; review/backfill flows have cross-table status coupling and should be split only with tests.

## BaselineReadSetHint

- docs/README.md
- docs/zh/content/架构设计/未来规划审计与剩余设计.md
- seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataGovernanceRepositoryAdapter.java
- seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentMetadataAdapterAutoConfiguration.java

## ImpactStatementDraft

- Compatibility boundary: Keep legacy JdbcMetadataGovernanceRepositoryAdapter facade available only through explicit compatibility switch; preserve port behavior and existing tests.
- Affected layers:
- docs
- jdbc-adapter
- spring-autoconfigure
- Owners:
- Metadata governance JDBC adapter owner
- Spring Boot autoconfigure owner
- Invariants:
- none
- Non-goals:
- Do not refactor unrelated dirty worktree files or implement every historical roadmap item unrelated to docs/README.md current audit.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
