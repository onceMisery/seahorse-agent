# AI Infra Sandbox Runtime Foundation - Evidence

No evidence has been recorded yet.

## EvidenceBundleDraft

- Artifact key: red-sandbox-missing-contract
- Type: test-red
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,SandboxPolicyDecisionTests,SandboxExecutionTests,SandboxArtifactTests' test
- Summary: RED failed at testCompile because sandbox domain, inbound ports, outbound ports, and KernelSandboxRuntimeService did not exist.
- Verifier: maven-surefire/javac exit 1 with missing symbol errors

## EvidenceBundleDraft

- Artifact key: diff-check
- Type: format-check
- Source: git diff --check
- Summary: Whitespace/conflict-marker check passed; output only contained expected Windows LF-to-CRLF warnings.
- Verifier: git diff --check exit 0

## EvidenceBundleDraft

- Artifact key: green-sandbox-focused-regression
- Type: test-green
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,SandboxPolicyDecisionTests,SandboxExecutionTests,SandboxArtifactTests' test
- Summary: GREEN passed 10 sandbox kernel tests covering default deny, unsupported runtime, enum reason decisions, terminal session fail-closed behavior, execution transitions, and prompt-visible artifact filtering.
- Verifier: maven-surefire exit 0; tests run 10, failures 0, errors 0
