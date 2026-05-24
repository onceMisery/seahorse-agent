# TodoCheckpointDraft

Current todo:
- [x] Confirm branch was created from current `main`.
- [x] Merge `main` into the new branch; result was `Already up to date`.
- [x] Read Phase 4 Context DB / Resource ACL doc and current implementation.
- [x] Write RED tests for default resource policy and query authorization.
- [x] Implement minimal default ACL policy and query owner/admin checks.
- [x] Wire Spring default policy bean.
- [x] Run first focused kernel regression.
- [x] Run Spring starter auto-configuration regression.
- [x] Run broader focused regression and diff checks.
- [ ] Commit and merge slice back to root `main`.

Active slice:
- Phase 4 lightweight Resource ACL and ContextPack query authorization.

Completed todos:
- Avoided repeating ContextPack domain, JDBC persistence, web query API, runtime assembler, producer, approval, checkpoint, retry, and model-routing work already in `main`.
- Kept ACL policy as a replaceable port implementation instead of adding rules to the builder.
- Kept query authorization inside kernel application service, before repository item exposure.

Evidence refs:
- `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultResourceAccessPolicyPortTests,KernelContextPackBuilderServiceTests,KernelContextPackQueryServiceTests' test` exited 0: 11 tests, 0 failures.
- `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` exited 0.
- `git diff --check` exited 0; Git only reported Windows LF/CRLF conversion warnings.
- Broader focused regression exited 0:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=DefaultResourceAccessPolicyPortTests,KernelContextPackBuilderServiceTests,KernelContextPackQueryServiceTests,KernelChatPipelineTests,KernelChatInboundServiceAgentModeTests,SeahorseAgentControllerTests,SeahorseWebApiContractTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`.

Blocked-on items:
- None.

Next step:
- Commit the feature branch, then merge back to root `main`.

ResumeStateHint:
- Continue in `D:\code\seahorse-agent\.worktrees\ai-infra-contextpack-acl`.
- Root `D:\code\seahorse-agent` should only have pre-existing untracked local files.
- Do not edit or remove unrelated root untracked files.

DriftCheckDraft:
- Scope: still Phase 4 lightweight ACL gap.
- Compatibility: custom `ResourceAccessPolicyPort` beans remain replaceable; kernel still depends on ports/domain, not Spring/JDBC/Web.
- New owner/adapter: a new default policy adapter exists, while the port remains the extension point.
- Retirement: Spring starter no longer defaults to deny-all for ContextPack building; deny-all remains available as an explicit port fallback.
- Decision: continue-to-commit.
