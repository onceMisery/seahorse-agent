# C Web AI Infra Implementation - Evidence

## Commands

### Frontend Build

Command:

```powershell
npm run build
```

Working directory: `frontend`.

Exit status: 0.

Covered:

- TypeScript compile and Vite production bundle.
- Shared first-message and later-message `ChatInput` path.
- Attachment upload UI and services.
- Task template selector, quota display/blocking, and high-cost confirmation.
- Feedback reason/comment dialog.
- User memory center route/page/service.
- Product-mode hiding of advanced admin routes.

Notable output:

- Vite warned that one JS chunk is larger than 500 kB after minification. This is a bundle-size warning, not a build failure.

### Target Backend Regression

Command:

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseConversationAttachmentControllerTests,JdbcConversationAttachmentRepositoryAdapterTests,SeahorseChatControllerTests,AdvancedFeatureControllerGateTests,SeahorseUserMemoryControllerTests,SeahorseUserQuotaControllerTests,SeahorseTaskTemplateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Exit status: 0.

Covered:

- Attachment upload/list/delete API and cross-user delete 403 behavior.
- JDBC conversation attachment save/list/find/delete owner filtering.
- `SeahorseChatController` passthrough of `attachmentIds`.
- `consumer-web` gate coverage for advanced/local/mesh-related APIs in the targeted controller gate tests.
- User memory list/delete/privacy-mode API.
- User quota summary API.
- Task template list/get API.

Observed test summary:

- Web adapter targeted tests: 22 tests, 0 failures, 0 errors.
- JDBC attachment adapter targeted tests: 1 test, 0 failures, 0 errors.
- Reactor build: SUCCESS.

### Earlier Slice Evidence

Previously recorded and still relevant targeted checks in this worktree covered:

- Structured stream timeline events.
- Run snapshot query and frontend structured event rendering.
- Pending approvals query and user/admin approval decision boundary.
- Agent artifact domain/API/JDBC/frontend integration.
- Web search/fetch adapter safety boundary.
- Product-mode advanced route hiding in frontend admin.

### Final Closure Slice Evidence

Command:

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=KernelAgentRunCostSummaryServiceTests,KernelFeedbackEvaluationCandidateQueryServiceTests,KernelCostUsageQueryServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Exit status: 0.

Covered:

- Run cost summary ownership/admin checks and aggregation.
- Feedback evaluation candidate admin-only query boundary.
- Existing cost usage query behavior.

Command:

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcMessageFeedbackRepositoryAdapterTests,JdbcConversationMemoryAdapterTests,JdbcConversationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Exit status: 0.

Covered:

- `agent_run_id` persistence/readback for conversation messages.
- Feedback evaluation candidate JDBC query path.
- Existing conversation memory/repository behavior around the changed schema contract.

Command:

```powershell
.\mvnw -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseCostUsageControllerTests,SeahorseMessageFeedbackControllerTests,SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Exit status: 0.

Covered:

- Run cost summary web API.
- Feedback evaluation candidate web API.
- Existing agent controller regressions around changed run/message behavior.

Command:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Exit status: 0.

Covered:

- Spring auto-configuration still wires the changed registry/chat run store surfaces.

Command:

```powershell
git diff --check
```

Exit status: 0.

Covered:

- No whitespace errors in the working diff. Git reported line-ending normalization warnings only.

## Files Of Interest

- `frontend/src/components/chat/ChatInput.tsx`
- `frontend/src/components/chat/WelcomeScreen.tsx`
- `frontend/src/components/chat/FeedbackButtons.tsx`
- `frontend/src/pages/MemoryCenterPage.tsx`
- `frontend/src/services/conversationAttachmentService.ts`
- `frontend/src/services/userMemoryService.ts`
- `frontend/src/stores/chatStore.ts`
- `frontend/src/stores/chatStoreTypes.ts`
- `frontend/src/types/index.ts`
- `frontend/src/utils/helpers.ts`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseConversationAttachmentController.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/conversation/KernelConversationAttachmentService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/chat/StreamChatCommand.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcConversationAttachmentRepositoryAdapter.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeatureGate.java`
- `docs/company-agent/c-web-ai-infra-phases/`

## Not Covered

- Live model use of uploaded attachment contents. Current implementation stores attachments and passes IDs through chat.
- Full event replay by `lastEventSeq`. Current resume path returns snapshot/done.
- Full Deer Flow style research orchestration. Current implementation has web tool adapters/templates/boundaries, but not the complete planner/researcher/writer pipeline.
- End-to-end feedback evaluation candidate promotion. Current implementation captures feedback reason/comment in the message feedback contract.
- Paid billing and product entitlement system. Current implementation covers quota/cost transparency and guardrails only.
