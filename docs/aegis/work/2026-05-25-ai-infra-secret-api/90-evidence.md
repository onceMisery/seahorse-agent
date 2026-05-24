# AI Infra Secret API - Evidence

## EvidenceBundleDraft

- Artifact key: focused-regression
- Type: command
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=KernelSecretManagementServiceTests,JdbcSecretStoreAdapterTests,JdbcSecretSchemaAlignmentTests,SeahorseAgentControllerTests#shouldExposeSecretCreationApiWithoutEchoingPlaintext,SeahorseAgentCredentialAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
- Summary: Passed. Covered secret management service, JDBC encrypted store, schema alignment, Web POST /api/secrets response shape, and Spring credential auto-configuration.
- Verifier: codex

## EvidenceBundleDraft

- Artifact key: mcp-credential-regression
- Type: command
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http,seahorse-agent-spring-boot-starter -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,SeahorseAgentCredentialAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
- Summary: Passed. Covered SecretValue/CredentialMaterial redaction, SecretStoreCredentialProvider, MCP HTTP bearer injection, MCP credential auto-configuration, and the new credential auto-configuration.
- Verifier: codex

## RED Evidence

- `JdbcSecretSchemaAlignmentTests` was added before updating `resources/database/seahorse_init.sql`.
- Initial focused run failed because `seahorse_init.sql` did not contain `CREATE TABLE IF NOT EXISTS sa_secret_ref`.
- After adding the schema block, the same test passed.

## Coverage Notes

- API response coverage verifies `POST /api/secrets` returns `SecretMetadata` and no `secretValue` field.
- JDBC coverage verifies stored `encrypted_value` does not equal or contain the raw token and that `getSecret(secretRef)` decrypts it.
- Starter coverage verifies JDBC secret store and management service are created only when the AES key property is configured.
- MCP coverage verifies the existing static bearer `SecretStorePort -> CredentialProviderPort` path still works.

## Not Covered

- No live PostgreSQL migration execution.
- No remote vault adapter, OAuth flow, OpenAPI connector import, sandbox runtime, or rotation workflow.

## EvidenceBundleDraft

- Artifact key: affected-modules-regression
- Type: command
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am test
- Summary: Passed. Covered full tests for kernel, web adapter, repository JDBC adapter, starter, and transitively built adapter modules included by -am.
- Verifier: codex

## EvidenceBundleDraft

- Artifact key: review-fix-affected-modules-regression
- Type: command
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-adapter-mcp-http,seahorse-agent-spring-boot-starter -am test
- Summary: Passed after metadata plaintext guard, Web rejection regression, and MCP/credential auto-configuration order fix.
- Verifier: Codex, 2026-05-25
