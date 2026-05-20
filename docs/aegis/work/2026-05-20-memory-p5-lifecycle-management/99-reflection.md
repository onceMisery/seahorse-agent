# Memory P5 lifecycle management - Reflection

P5 added lifecycle governance without replacing the existing layered memory stores. The implementation makes lifecycle state a first-class compatibility layer: old records remain valid, while new or updated records can carry status, generation, Profile slot, tenant, validity, and read-feedback metadata.

What changed:

- `MemoryLifecyclePort` now gives the kernel a deterministic lifecycle boundary for Profile slot invalidation and read feedback.
- JDBC lifecycle persistence can mark old same-slot Profile fragments obsolete across short-term, long-term, and semantic tables.
- Layered memory reads now exclude obsolete/deleted records from normal active retrieval.
- Profile writes record slot and generation metadata on compatible short-term fragments.
- Memory reads report referenced records back to lifecycle storage, including short-term access count updates.
- Management APIs can inspect Profile facts, Correction rules, operation logs, and memory outbox tasks.
- Spring wiring supplies the lifecycle adapter and expanded management-service dependency set when repositories exist.

Architecture alignment:

- Result: aligned with P5 lifecycle governance subset.
- Compatibility: additive schema and no-op fallback preserve old deployments and tests.
- Source of truth: Profile KV and Correction Ledger remain canonical; lifecycle status controls compatibility-store visibility.
- Retirement: no legacy table retired; active reads now honor lifecycle status.

ADR backfill signal:

- Suggested action: skip for now.
- Reason: this is an implementation of the existing memory-system improvement plan. A durable ADR may be appropriate after P6 if policy/observability ownership or lifecycle state semantics become broader platform contracts.

Method Pack output does not grant completion authority.
