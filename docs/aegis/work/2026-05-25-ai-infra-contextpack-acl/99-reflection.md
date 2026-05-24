# Reflection

Goal:
- Close the Phase 4 lightweight Resource ACL gap without expanding into full ACL management.

Repair track:
- Root issue: default `ResourceAccessPolicyPort` was deny-all, so ContextPack builder could filter legitimate owner user input and memory when no custom policy bean exists.
- Canonical owner: default policy adapter behind `ResourceAccessPolicyPort`, plus query authorization in `KernelContextPackQueryService`.
- Minimal change: add owner/public document rules and owner/admin query checks.
- Compatibility boundary: keep existing port contract and allow custom policies to replace the default bean.

Retirement track:
- Old default deny-all remains available as an explicit static fallback, but Spring starter no longer uses it as the default policy for normal runtime assembly.
- Full ACL persistence/API remains outside this slice until a resource ACL storage model is specified.

Risk / Unknown:
- Admin handling inside `ResourceAccessPolicyPort` is not implemented because the current request contract has subject type/id but no role. Admin exception is implemented at ContextPack query boundary where `CurrentUser` is available.
- Document public detection is intentionally minimal and only honors `attributesJson.visibility = public`.

Decision:
- Broader focused verification passed; continue to commit and merge.
