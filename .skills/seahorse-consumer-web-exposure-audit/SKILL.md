---
name: seahorse-consumer-web-exposure-audit
description: Use when auditing Seahorse Agent C-side Web / consumer-web default exposure, ProductMode or AdvancedFeatureGate behavior, frontend admin route gating, or non-Web/local/mesh capabilities such as shell, filesystem, MCP/OpenAPI, A2A, sandbox, secrets, handoff, rollout, readiness, and enterprise admin operations.
---

# Seahorse Consumer Web Exposure Audit

## Overview

Audit whether the default `consumer-web` product path exposes non-Web, local, mesh, or enterprise-only capabilities. Keep the review evidence-based: cite exact files/lines, distinguish already-gated surfaces from real exposure, and recommend the smallest fix.

## Start Set

Read only the relevant docs and code for the requested worktree. Prefer:

- `docs/company-agent/c-web-ai-infra-phases/01-web-task-runtime.md`
- `docs/company-agent/c-web-ai-infra-phases/02-research-web-agent.md`
- `docs/company-agent/c-web-ai-infra-phases/03-personalization-operations.md`
- `docs/company-agent/c-web-ai-infra-phases/04-advanced-extension-boundary.md`
- `docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md`
- `docs/company-agent/ai-infra-phases/07-multi-agent-a2a-mesh.md`
- Any non-Web remediation or handoff doc named by the user.

If a Chinese filename is hard to type, locate it with:

```powershell
rg --files docs | rg "非 Web|过渡|c-web-ai-infra|ai-infra-phases|handoff"
```

## Workflow

1. Confirm the product baseline:
   - `ProductMode.DEFAULT` and config default must be `CONSUMER_WEB`.
   - `AdvancedFeatureGate` must force-disable advanced features for `CONSUMER_WEB`, even if config flags are true.
   - Tests should cover default consumer-web denial and explicit enterprise/advanced allowance.

2. Build an exposure inventory with `rg`:

```powershell
rg -n "ProductMode|AdvancedFeatureGate|AdvancedFeature|ToolProviderExposurePolicy|consumer-web|sandbox|shell|filesystem|local|MCP|OpenAPI|connector|credential|secret|A2A|mesh|handoff|rollout|readiness|enterprise|admin" .
```

3. Check backend surfaces:
   - Controllers under `seahorse-agent-adapter-web/src/main/java/.../web`.
   - Default constructors and Spring constructors; both must preserve safe defaults.
   - `AdvancedFeature` enum coverage for each non-Web or enterprise capability.
   - `ToolProviderExposurePolicyPort` and tool registration paths; local/shell/filesystem/handoff tools must not enter default consumer-web tool sets.
   - Spring auto-configuration paths that can register local agent, sandbox, connector, A2A, or handoff capabilities without the web controller being called.

4. Check frontend surfaces:
   - Router, admin layout, sidebar, feature config, and direct page routes.
   - Admin-only AI Infra pages, intent/ingestion/tool/connector/secret/sandbox/handoff/rollout pages.
   - Hidden navigation is not enough if a direct route still renders or service calls remain reachable in default mode.

5. Separate evidence:
   - **Safe**: explicitly gated, hidden, denied by default, or impossible to instantiate in consumer-web.
   - **Risk**: reachable route/API/tool/auto-config path, missing gate in a constructor, config flag that bypasses product mode, or a frontend direct route that exposes admin/local concepts.
   - **Deferred**: enterprise-only design present in docs or code but unreachable by default and intentionally behind advanced/enterprise gates.

## Output

Return this shape:

```markdown
Findings
- P1/P2/P3 [file:line]: what is exposed, default path, why the current gate is insufficient, and the minimal fix.

Confirmed Safe
- [file:line]: capability and gate evidence.

Minimal Fixes
- Smallest code/test changes, grouped by backend, frontend, and tool registration.

Verification
- Commands run, or "not run" with reason.
```

## Guardrails

- Do not modify files during a read-only audit.
- Do not treat an enterprise feature as a bug solely because it exists; the bug is default consumer-web reachability.
- Do not run broad suites unless the user asks. Recommend focused tests such as `AdvancedFeatureGateTests`, controller gate tests, and targeted frontend build/type checks.
- Do not rely on titles or navigation labels alone; verify the actual route/API/tool construction path.
