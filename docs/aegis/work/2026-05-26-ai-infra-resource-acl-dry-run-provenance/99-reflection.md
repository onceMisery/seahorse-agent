# AI Infra Resource ACL Dry-run and Provenance - Reflection

Phase 4 Resource ACL dry-run/provenance slice is complete for the current acceptance boundary.

Completed behavior:

- Dry-run import is additive and non-mutating.
- Batch duplicate detection runs before existing-rule lookup.
- `ALLOW`/`DENY` mismatch on the same natural key reports `CONFLICT`.
- `RESOURCE_TYPE` scope reports `UNSUPPORTED_SCOPE` in this slice.
- Expired input reports `INVALID` instead of being converted into a disabled persisted rule.
- JDBC provides natural-key lookup and enum/check constraints without moving conflict policy into persistence.
- Web exposes `POST /api/resource-acl-rules:dry-run-import` and returns item index, status, reason code, and natural key without SQL/table/commit token fields.

Compatibility note:

- The database unique index intentionally prevents duplicate active exact rules, not all active natural-key overlaps. Existing runtime semantics support multiple effective ACL rules for the same natural key and resolve them with deny-wins/priority ordering; the broader natural-key conflict remains a dry-run operating signal instead of a persistence invariant.

Remaining later-phase hook:

- Full Audit Ledger `RESOURCE_ACL_CHANGED` / `CONTEXT_ACCESSED` wiring belongs to Phase 8B+ after the ledger foundation and phase-specific event contracts are ready.
