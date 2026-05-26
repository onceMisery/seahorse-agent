# AI Infra Agent Factory Kernel Foundation - Evidence

No evidence has been recorded yet.

## EvidenceBundleDraft

- Artifact key: diff-check
- Type: format-check
- Source: git diff --check
- Summary: Whitespace/conflict-marker check passed; output only contained expected Windows LF-to-CRLF warnings.
- Verifier: git diff --check exit 0

## EvidenceBundleDraft

- Artifact key: green-agent-factory-kernel-regression
- Type: test-green
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
- Summary: GREEN passed 6 kernel tests covering template tool subset, risk cap, instruction overlay merge, enabled template listing, from-template DRAFT creation through AgentDefinitionInboundPort, and structured publish validation WARN/FAIL items.
- Verifier: maven-surefire exit 0; tests run 6, failures 0, errors 0

## EvidenceBundleDraft

- Artifact key: red-agent-factory-missing-contract
- Type: test-red
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
- Summary: RED failed at testCompile because Agent Factory domain, inbound ports, outbound ports, and KernelAgentFactoryService did not exist.
- Verifier: maven/javac exit 1 with missing symbol errors
