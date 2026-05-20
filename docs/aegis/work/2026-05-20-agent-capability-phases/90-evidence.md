# EvidenceBundleDraft

## Implementation Evidence

- Added Agent tool adapters under `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/`.
- Added Spring registration through `BuiltInAgentToolRegistrar` and `SeahorseAgentKernelAgentAutoConfiguration`.
- Extended Agent mode memory activation in `KernelChatInboundService` and `KernelAgentLoop`.
- Extended memory write/governance integration so Agent `memory_write` triggers `MemoryGovernanceInboundPort.runGovernance(userId, "agent-memory-write", false)`.
- Added stable occupation semantic-key mapping in `KernelMemoryGovernanceService`.
- Added profile-slot deduplication when loading long-term and semantic memory in `DefaultMemoryEnginePort`.
- Enabled Docker full-stack agent mode through `docker-compose.full.yml`.

## Automated Verification

### Target Agent/Memory Regression

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelAgentLoopTests,KernelChatInboundServiceAgentModeTests,SeahorseAgentKernelAgentAutoConfigurationTests,AgentToolPortAdapterTests,KernelMemoryGovernanceServiceTests,DefaultMemoryEnginePortTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result:

- Exit status: 0
- Maven result: `BUILD SUCCESS`
- Test result: 55 tests, 0 failures, 0 errors, 0 skipped

### Package

Command:

```powershell
./mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"
```

Result:

- Exit status: 0
- Maven result: `BUILD SUCCESS`

### Docker Build

Command:

```powershell
docker compose -f docker-compose.full.yml build backend
```

Result:

- Exit status: 0
- Image: `seahorse-agent-backend:latest`
- Docker output: `Image seahorse-agent-backend Built`

### Docker Restart

Command:

```powershell
docker compose -f docker-compose.full.yml up -d --no-deps backend
```

Result:

- Exit status: 0
- Container: `seahorse-backend`
- Status: `Up`
- Port: `0.0.0.0:9090->9090/tcp`
- Spring Boot log: `Started SeahorseAgentApplication`

## Runtime Verification

Authentication:

- Endpoint: `POST http://localhost:9090/auth/login`
- User: `admin`
- Role returned: `admin`
- Token: present, redacted from records

Conversation A:

- Endpoint: `GET /rag/v3/chat`
- Query: `请记住：我是学生。以后当我问职业时，请根据你记住的信息回答。`
- Params: `chatMode=agent`, unique `conversationId`
- SSE evidence: model called `memory_write -> ok`
- Response: acknowledged that "我是学生" was stored

Conversation B:

- Endpoint: `GET /rag/v3/chat`
- Query: `我的职业是什么？请只根据你记住的信息回答。`
- Params: `chatMode=agent`, different unique `conversationId`
- SSE evidence: model called `memory_read -> ok`
- Response: `你的职业是学生`
- Regression signal: response did not expose the previous stale "老师" conflict after the latest patch.

## Not Covered

- Full browser UI testing.
- Full all-module test suite.
- Historical data migration or deletion of every stale semantic record.
- Complete implementation of all long-range Phase C/D/F UI, HITL, and enterprise dashboard work.

## Residual Risk

- Profile-slot normalization is currently rule-based and focused on high-value profile slots such as occupation/name/education/organization.
- Historical stale semantic records may remain in storage; current mitigation is activation-time deduplication plus same-request governance promotion.
- Model behavior can still vary, but server-side memory context injection and tool scope enforcement reduce reliance on voluntary tool selection.
