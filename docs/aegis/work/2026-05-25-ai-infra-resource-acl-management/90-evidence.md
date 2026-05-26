# AI Infra Resource ACL Management - Evidence

No evidence has been recorded yet.

## EvidenceBundleDraft

- Artifact key: focused-regression
- Type: test-command
- Source: mvn -nsu -Dspotless.apply.skip=true focused kernel/jdbc/web/starter Resource ACL tests plus git diff --check
- Summary: Resource ACL domain, management service, policy composition, JDBC adapter, Web API, and starter wiring all passed focused regression; git diff --check reported no whitespace errors.
- Verifier: Codex 2026-05-25 23:18-23:20 Asia/Shanghai

## EvidenceBundleDraft

- Artifact key: review-fix-deny-wins-regression
- Type: test-command
- Source: mvn -nsu -Dspotless.apply.skip=true focused Resource ACL kernel/JDBC tests after deny-wins review fix
- Summary: Added cross-priority deny-wins coverage; ResourceAclRule.effectiveOrder and JDBC findEffective now rank DENY before priority; ResourceAclRepositoryPort.findEffective is mandatory. Kernel ACL 10 tests and JDBC ACL 2 tests passed.
- Verifier: Codex 2026-05-25 23:30-23:32 Asia/Shanghai
