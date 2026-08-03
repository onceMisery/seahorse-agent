# ADR-003: Durable Operation State and UNKNOWN Side-Effect Semantics

Status: Accepted  
Date: 2026-08-03  
Source: `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md` §8.5, §9, §10

## Context

Core operation state and tool side-effect state previously lived in in-memory
callbacks, per-instance maps, and executor-local retry assumptions. A process
crash between starting a side effect and recording its outcome left the
authoritative state only in the failed process. Duplicate delivery or redelivery
could repeat an external side effect without detection.

## Decision

Durable operation states follow a single state machine:

```text
ACCEPTED -> RUNNING -> SUCCEEDED | FAILED | CANCELLED | UNKNOWN
UNKNOWN -> SUCCEEDED | FAILED only after reconciliation or compensation
```

Rules:

- Terminal states are immutable to ordinary retries.
- Retries create a new attempt and retain prior evidence.
- Automatic retry is allowed only for proven idempotent operations.
- Idempotency identity binds tenant, operation type, and business object.
- In-memory callback state is never the authoritative recovery source.

For governed tool execution:

- A durable invocation record is persisted before any side effect.
- `UNKNOWN` is used for uncertain outcomes: when the idempotency claim collides
  with a prior PROCESSING attempt, the previous attempt's side-effect outcome is
  unknown, so the invocation is recorded `UNKNOWN` rather than fabricated
  `FAILED`.
- `UNKNOWN` transitions to `SUCCEEDED` or `FAILED` only after reconciliation or
  compensation.

## Consequences

- Core adapter errors expose a stable, sanitized shape (`code`, `message`,
  `retryable`, `traceId`, `details`); `code` is the stable machine contract and
  `message` contains no secret, internal path, prompt, stack trace, or raw
  provider response.
- Validation, authentication, authorization, policy rejection, conflict,
  throttling, dependency, timeout, cancellation, and internal defects remain
  distinct.
- `FEATURE_DISABLED` is explicit and not retryable.
- Required correctness and security dependencies fail closed.

## Verification

- `LocalToolGatewayPortAuditTests` asserts duplicate idempotency hits record
  `UNKNOWN` with the error message retained for reconciliation.
- `ToolInvocationStatus` includes `UNKNOWN`; JDBC persistence is storage
  compatible via `name()`.
- Terminal callback idempotency and SSE timeout/reconnect tests pass.
