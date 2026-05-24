# TaskIntentDraft

Requested outcome:
- Continue AI-Infra Phase 4 without duplicating completed ContextPack runtime/producer work.

Current slice:
- Add a lightweight default resource ACL policy for ContextPack build candidates.
- Enforce owner/admin authorization when querying persisted ContextPacks and items.

Success evidence:
- Tests prove default policy allows owner user input, owner memory, and public/owned documents.
- Tests prove unrelated users cannot query another user's ContextPack/items, while owner and admin can.
- Spring starter wires the lightweight policy by default instead of the previous deny-all fallback.

Stop condition:
- Done: focused kernel and starter regressions pass, diff check passes, branch is committed and merged back to root `main`.
- Needs-verification: code exists but focused regression has not passed.
- Scope-exceeded: work requires full ACL management UI/API, ACL persistence model, remote mesh, workflow engine, or a complex JSON DB model.
- Blocked: current resource references do not carry enough ownership/visibility data to preserve conservative access semantics.

Non-goals:
- No `POST /api/resources/{type}/{id}/acl`.
- No access-decision log query API.
- No complex ACL database model.
- No RAG/Memory pipeline rewrite.
- No ContextBudgetPort implementation in this slice.

BaselineReadSetHint:
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- Existing ContextPack domain and repository port.
- Existing ContextPack runtime assembler.
- Existing Spring kernel registry auto-configuration.

ImpactStatementDraft:
- Kernel application adds a replaceable default `ResourceAccessPolicyPort` implementation.
- Kernel query service adds owner/admin read checks before exposing pack metadata or items.
- Spring starter changes only the default bean; custom `ResourceAccessPolicyPort` beans still override it.
