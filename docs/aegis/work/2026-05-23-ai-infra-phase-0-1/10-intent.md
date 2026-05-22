# AI Infra Phase 0/1 Execution Intent

Requested outcome: start executing `docs/company-agent/ai-infra-phases` toward enterprise AI Infra, using subagents only where it improves throughput, and preserving the existing clean architecture rules.

Scope for the first implementation slice:

- Phase 0: architecture baseline docs, package ownership shells, and test baseline.
- Phase 1: first vertical backend kernel slice for Agent Definition, Agent Version, Agent Run, Agent Step, inbound ports, outbound ports, and framework-neutral services.

Non-goals for this slice:

- No Tool Gateway implementation yet.
- No HITL/checkpoint worker implementation yet.
- No database adapter, web controller, or frontend management page until kernel contracts are stable.
- No refactor of `KernelAgentLoop` in this slice.

Baseline refs:

- `docs/company-agent/ai-infra-phases/README.md`
- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`
- `docs/company-agent/ai-infra-phases/01-agent-registry-run-store.md`
- `docs/aegis/BASELINE-GOVERNANCE.md`
- `CLAUDE.md`

Impact statement:

- Establishes the canonical Agent Registry and Agent Runtime owner boundaries without moving existing chat/agent-loop code.
- Adds new kernel contracts behind ports, preserving hexagonal architecture and DIP.
- Keeps the implementation small and test-first to respect KISS and YAGNI.
