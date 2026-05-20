# Memory P6 observability policy - Reflection

P6 adds the first operational control plane for the memory system without changing the canonical memory hierarchy. The implementation keeps Profile KV and Correction Ledger as the strong fact sources, while giving operators a way to inspect health and adjust deterministic capture thresholds at runtime.

What changed:

- `MemoryPolicyConfigPort` now owns runtime policy configuration for capture thresholds, token budget, track toggles, review flag, alert thresholds, and grey-release key.
- `MemoryValueAssessor` now reads thresholds from policy config instead of hard-coded constants.
- `KernelMemoryManagementService` can aggregate a `MemoryHealthReport` from Profile, Correction, conflict, operation, quality snapshot, and outbox ports.
- Web management APIs expose memory health and policy config read/update operations.
- Spring auto-configuration initializes the default policy port from `seahorse-agent.memory.policy.*` properties and injects it into the memory engine and management service.

Architecture alignment:

- Result: aligned with P6 observability and dynamic policy subset.
- Compatibility: existing constructors and store paths are preserved through default policy ports.
- Source of truth: Profile KV and Correction Ledger remain canonical; policy config only controls deterministic thresholds and management visibility.
- Retirement: static value-assessment constants are superseded by config-backed defaults.

ADR backfill signal:

- Suggested action: skip for now.
- Reason: this slice implements the existing memory-system improvement plan. A durable ADR may be useful later if policy config persistence, Micrometer metric names, or operator API stability become platform contracts.

Method Pack output does not grant completion authority.
