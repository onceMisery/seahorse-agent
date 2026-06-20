# AgentScope + Nacos A2A local deployment

This guide covers the local switch-on path for the AgentScope runtime and
Nacos-backed A2A/config-center integration.

## Runtime versions

- AgentScope: `io.agentscope:agentscope:2.0.0-RC3`
- AgentScope Nacos extensions: `2.0.0-RC3`
- Nacos server image: `nacos/nacos-server:v3.0.3`
- Nacos Java client resolved through AgentScope: `3.2.1-2026.03.30`

## Docker compose

Both `docker-compose.yml` and `docker-compose.full.yml` include a `nacos`
service. The backend receives the Nacos address through:

```bash
SEAHORSE_AGENTSCOPE_NACOS_SERVER_ADDR=nacos:8848
SEAHORSE_AGENTSCOPE_A2A_NACOS_SERVER=nacos:8848
```

The default executor remains `kernel`. To run with AgentScope and register the
local backend as an A2A agent:

```bash
SEAHORSE_AGENT_EXECUTOR_ENGINE=agentscope
SEAHORSE_AGENTSCOPE_A2A_ENABLED=true
SEAHORSE_AGENTSCOPE_A2A_REGISTER_ENABLED=true
SEAHORSE_AGENTSCOPE_A2A_TENANT_ID=default
SEAHORSE_AGENTSCOPE_A2A_AGENT_NAME=seahorse-agent
SEAHORSE_AGENTSCOPE_A2A_URL=http://backend:9090/a2a
```

The A2A tool exposed to the Seahorse tool registry is:

```text
invoke_remote_a2a_agent
```

It resolves remote cards through Nacos and enforces same-tenant discovery before
invocation.

## Nacos as config center

Enable config-center integration separately:

```bash
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_ENABLED=true
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_PROMPT_KEY=agent.system.prompt
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_SKILL_NAMESPACE=agent-skills
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_SKILL_VERSION=v1
SEAHORSE_AGENTSCOPE_CONFIG_CENTER_SKILL_LABEL=stable
```

When enabled, Seahorse creates the AgentScope Nacos prompt listener and skill
repository from the same Nacos `AiService`, so Nacos is used for both service
discovery and config-center access.

## M3 mode

M3 is represented as A2A tenant-boundary metadata on the registered agent card.
It is intentionally not written as unsupported custom Nacos client properties.

```bash
SEAHORSE_AGENTSCOPE_NACOS_M3_ENABLED=true
SEAHORSE_AGENTSCOPE_NACOS_M3_MODE=M3
SEAHORSE_AGENTSCOPE_NACOS_M3_NAMESPACE=seahorse-agent
SEAHORSE_AGENTSCOPE_NACOS_M3_GROUP=DEFAULT_GROUP
SEAHORSE_AGENTSCOPE_NACOS_M3_CLUSTER_NAME=local
```

The registered card includes the tenant marker and M3 tags under the
`seahorse.tenant-boundary` skill. A2A resolution rejects cards without matching
tenant metadata.

## OTEL visibility

This slice does not expose new OTEL UI or tracing infrastructure. Existing
Micrometer observation remains available. AgentScope Studio can be enabled
independently with:

```bash
SEAHORSE_AGENTSCOPE_STUDIO_ENABLED=true
```

Full OTEL span export is still controlled by the observability workstream and
should be enabled through the existing observation infrastructure when that
slice is selected.

## E2E acceptance

Before merging this branch, verify:

1. `engine=agentscope` starts and serves the same chat/RAG smoke path as
   `engine=kernel`.
2. Two backend instances register different A2A cards through the Nacos service.
3. A same-tenant remote invocation succeeds through `invoke_remote_a2a_agent`.
4. A cross-tenant remote invocation is rejected during card resolution.
