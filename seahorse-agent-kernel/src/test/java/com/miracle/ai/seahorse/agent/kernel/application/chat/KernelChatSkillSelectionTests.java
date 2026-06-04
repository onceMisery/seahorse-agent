package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests proving that {@code selectedSkillNames} from
 * {@link StreamChatCommand} flows end-to-end into
 * {@link com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest}
 * skill runtime blocks and context.
 */
class KernelChatSkillSelectionTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    // ── Test 1: selectedSkillNames enters AgentLoopRequest ───

    @Test
    void perTurnSelectedSkillAppearsInSystemPromptAndRuntimeBlocks() {
        // Set up a skill in the repository
        StubSkillRepository skillRepo = new StubSkillRepository();
        AgentSkill skill = new AgentSkill("code-review", "default",
                AgentSkillCategory.PUBLIC, AgentSkillSource.MANUAL, AgentSkillStatus.ACTIVE,
                true, "rev-cr-1", "Review code for quality", List.of(), List.of("read_file"),
                "admin", "admin", Instant.now(), Instant.now());
        skillRepo.addSkill(skill);
        skillRepo.addRevision(new AgentSkillRevision("rev-cr-1", "code-review", "default", 1,
                "hash-cr", "# Code Review\nCheck naming conventions.", "{}", null, "{}",
                "admin", Instant.now()));

        // Set up agent definition with NO version-bound skills
        MemoryAgentDefinitionRepository defRepo = new MemoryAgentDefinitionRepository();
        defRepo.create(agentDef("my-agent", "v1"));
        defRepo.saveVersion(agentVer("my-agent", "v1", "{\"modelId\":\"test-model\"}", "{}"));

        MemoryAgentRunRepository runRepo = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                defRepo, runRepo,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);

        CapturingModel model = new CapturingModel();
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model, new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepo, FIXED_CLOCK));

        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(), StreamTaskPort.noop(),
                Optional.of(agentLoop), KernelRagTraceRecorder.noop(),
                null, MemoryEnginePort.noop(),
                Optional.of(runService), Optional.empty(),
                Optional.of(defRepo),
                ConversationAttachmentContextAssembler.noop(),
                Optional.of(skillRepo));

        // Send chat with selectedSkillNames
        service.streamChat(new StreamChatCommand(
                        "Review my code", "conv-1", "task-1", "user-1", false,
                        ChatMode.AGENT, "my-agent", null, null, List.of(),
                        List.of("code-review")),
                callback);

        assertTrue(callback.awaitTerminal(), "should reach terminal state");
        assertNull(callback.error, "should not have error: " + callback.error);

        // Verify the skill appeared in the system prompt (skillRuntimeContext)
        assertFalse(model.capturedRequests.isEmpty(), "model should have been called");
        ChatRequest req = model.capturedRequests.get(0);
        String systemPrompt = req.getMessages().get(0).getContent();
        assertTrue(systemPrompt.contains("<skill name=\"code-review\""),
                "system prompt should contain the selected skill");
        assertTrue(systemPrompt.contains("Check naming conventions"),
                "system prompt should contain skill instructions (METADATA_AND_BODY)");
    }

    // ── Test 2: resolver missing + selectedSkillNames → error ─

    @Test
    void missingResolverWithSelectedSkillNamesFailsRequest() {
        // No skill repository → chatSkillResolver is null
        MemoryAgentDefinitionRepository defRepo = new MemoryAgentDefinitionRepository();
        defRepo.create(agentDef("my-agent", "v1"));
        defRepo.saveVersion(agentVer("my-agent", "v1", "{\"modelId\":\"test-model\"}", "{}"));

        MemoryAgentRunRepository runRepo = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                defRepo, runRepo,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);

        CapturingModel model = new CapturingModel();
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model, new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepo, FIXED_CLOCK));

        RecordingCallback callback = new RecordingCallback();
        // Construct service WITHOUT skill repository (last param = Optional.empty())
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(), StreamTaskPort.noop(),
                Optional.of(agentLoop), KernelRagTraceRecorder.noop(),
                null, MemoryEnginePort.noop(),
                Optional.of(runService), Optional.empty(),
                Optional.of(defRepo),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty());  // No skill repository!

        // Send chat with selectedSkillNames despite no resolver
        service.streamChat(new StreamChatCommand(
                        "Review my code", "conv-1", "task-1", "user-1", false,
                        ChatMode.AGENT, "my-agent", null, null, List.of(),
                        List.of("code-review")),
                callback);

        assertTrue(callback.awaitTerminal(), "should reach terminal state");
        assertNotNull(callback.error, "should have error when resolver is missing");
        assertTrue(callback.error.getMessage().contains("not configured")
                        || callback.error.getMessage().contains("missing")
                        || callback.error instanceof IllegalStateException,
                "error should indicate resolver is not configured, got: " + callback.error.getMessage());
        assertTrue(model.capturedRequests.isEmpty(),
                "model should NOT have been called when resolver is missing");
    }

    // ── Test 3: no selectedSkillNames → normal flow, no error ─

    @Test
    void noSelectedSkillNamesWorksWithoutResolver() {
        MemoryAgentDefinitionRepository defRepo = new MemoryAgentDefinitionRepository();
        defRepo.create(agentDef("my-agent", "v1"));
        defRepo.saveVersion(agentVer("my-agent", "v1", "{\"modelId\":\"test-model\"}", "{}"));

        MemoryAgentRunRepository runRepo = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                defRepo, runRepo,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);

        CapturingModel model = new CapturingModel();
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model, new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepo, FIXED_CLOCK));

        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(), StreamTaskPort.noop(),
                Optional.of(agentLoop), KernelRagTraceRecorder.noop(),
                null, MemoryEnginePort.noop(),
                Optional.of(runService), Optional.empty(),
                Optional.of(defRepo),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty());  // No skill repository

        // Send chat WITHOUT selectedSkillNames → should work fine
        service.streamChat(new StreamChatCommand(
                        "Hello", "conv-1", "task-1", "user-1", false,
                        ChatMode.AGENT, "my-agent", null),
                callback);

        assertTrue(callback.awaitTerminal(), "should reach terminal state");
        assertNull(callback.error, "should not error when no skills selected");
    }

    // ── Test 4: version-bound + per-turn merge ───────────────

    @Test
    void versionBoundSkillTakesPriorityOverPerTurnOnCollision() {
        StubSkillRepository skillRepo = new StubSkillRepository();
        // Per-turn skill with different content
        AgentSkill skill = new AgentSkill("deep-research", "default",
                AgentSkillCategory.PUBLIC, AgentSkillSource.MANUAL, AgentSkillStatus.ACTIVE,
                true, "rev-per-turn", "Per-turn research", List.of(), List.of(),
                "admin", "admin", Instant.now(), Instant.now());
        skillRepo.addSkill(skill);
        skillRepo.addRevision(new AgentSkillRevision("rev-per-turn", "deep-research", "default", 1,
                "hash-pt", "Per-turn instructions", "{}", null, "{}",
                "admin", Instant.now()));

        MemoryAgentDefinitionRepository defRepo = new MemoryAgentDefinitionRepository();
        defRepo.create(agentDef("my-agent", "v1"));
        // Version-bound skill with SAME name but different revision
        defRepo.saveVersion(agentVer("my-agent", "v1", "{\"modelId\":\"test-model\"}", """
                {
                  "mode": "BOUND_REVISIONS",
                  "skills": [{
                    "name": "deep-research",
                    "revisionId": "rev-version-bound",
                    "contentHash": "hash-vb",
                    "description": "Version-bound research",
                    "category": "PUBLIC",
                    "injectMode": "METADATA_AND_BODY",
                    "allowedTools": [],
                    "content": "Version-bound instructions"
                  }]
                }
                """));

        MemoryAgentRunRepository runRepo = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                defRepo, runRepo,
                () -> Optional.of(new CurrentUser(1L, "alice", "user", null)), FIXED_CLOCK);

        CapturingModel model = new CapturingModel();
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model, new InMemoryToolRegistry(),
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepo, FIXED_CLOCK));

        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(), StreamTaskPort.noop(),
                Optional.of(agentLoop), KernelRagTraceRecorder.noop(),
                null, MemoryEnginePort.noop(),
                Optional.of(runService), Optional.empty(),
                Optional.of(defRepo),
                ConversationAttachmentContextAssembler.noop(),
                Optional.of(skillRepo));

        // Send chat selecting the same skill that's version-bound
        service.streamChat(new StreamChatCommand(
                        "Research", "conv-1", "task-1", "user-1", false,
                        ChatMode.AGENT, "my-agent", null, null, List.of(),
                        List.of("deep-research")),
                callback);

        assertTrue(callback.awaitTerminal());
        assertNull(callback.error, "should not error: " + callback.error);

        ChatRequest req = model.capturedRequests.get(0);
        String systemPrompt = req.getMessages().get(0).getContent();
        // Version-bound should win (published contract)
        assertTrue(systemPrompt.contains("Version-bound instructions"),
                "version-bound skill content should take priority");
        assertFalse(systemPrompt.contains("Per-turn instructions"),
                "per-turn content should be overridden by version-bound");
    }

    // ── Test helpers ─────────────────────────────────────────

    private static AgentDefinition agentDef(String agentId, String versionId) {
        return new AgentDefinition(agentId, AgentDefinition.DEFAULT_TENANT_ID,
                "Test Agent", "Test", "owner", "platform",
                AgentType.ASSISTANT, null, AgentStatus.PUBLISHED,
                AgentRiskLevel.LOW, versionId, FIXED_CLOCK.instant(), FIXED_CLOCK.instant());
    }

    private static AgentVersion agentVer(String agentId, String versionId,
                                          String modelConfig, String skillSetJson) {
        return new AgentVersion(versionId, agentId, 1L, "You are a test assistant.",
                AgentVersion.EMPTY_JSON_OBJECT, modelConfig,
                AgentVersion.EMPTY_JSON_OBJECT, AgentVersion.EMPTY_JSON_OBJECT,
                skillSetJson, "owner", FIXED_CLOCK.instant(), "test");
    }

    private static KernelChatPipeline newPipeline() {
        ChatPreparationPorts prep = new ChatPreparationPorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort.empty(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build());
        ChatResponsePorts resp = new ChatResponsePorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort.simple(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort.empty(),
                StreamingChatModelPort.noop(), StreamTaskPort.noop());
        return new KernelChatPipeline(prep, resp);
    }

    /** Model that captures requests and returns a simple final answer. */
    static final class CapturingModel implements StreamingChatModelPort {
        final List<ChatRequest> capturedRequests = new ArrayList<>();

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request, StreamCallback callback, ToolCallCollector collector) {
            capturedRequests.add(request);
            callback.onContent("test answer");
            collector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {};
        }
    }

    static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);
        final List<String> contents = new ArrayList<>();
        String runId;
        Throwable error;

        @Override public void onContent(String content) { contents.add(content); }
        @Override public void onThinking(String content) {}
        @Override public void onRunStarted(String runId) { this.runId = runId; }
        @Override public void onComplete() { terminal.countDown(); }
        @Override public void onError(Throwable error) { this.error = error; terminal.countDown(); }

        boolean awaitTerminal() {
            try { return terminal.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
    }

    static class StubSkillRepository implements AgentSkillRepositoryPort {
        private final Map<String, AgentSkill> skills = new HashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new HashMap<>();

        void addSkill(AgentSkill skill) { skills.put(skill.tenantId() + ":" + skill.name(), skill); }
        void addRevision(AgentSkillRevision rev) { revisions.put(rev.tenantId() + ":" + rev.revisionId(), rev); }

        @Override public void saveSkill(AgentSkill skill) { addSkill(skill); }
        @Override public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(tenantId + ":" + name))
                    .filter(s -> s.status() != AgentSkillStatus.DELETED);
        }
        @Override public AgentSkillPage page(String t, long c, long s, String k) {
            return new AgentSkillPage(List.of(), 0, s, c, 0);
        }
        @Override public void saveRevision(AgentSkillRevision rev) { addRevision(rev); }
        @Override public long nextRevisionNo(String t, String s) { return 1; }
        @Override public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
            return Optional.ofNullable(revisions.get(tenantId + ":" + revisionId));
        }
        @Override public List<AgentSkillRevision> listRevisions(String t, String s) { return List.of(); }
        @Override public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding> listBindings(
                String t, String a) { return List.of(); }
        @Override public void replaceBindings(String t, String a,
                List<com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding> b) {}
    }

    static class MemoryAgentDefinitionRepository implements AgentDefinitionRepositoryPort {
        private final Map<String, AgentDefinition> defs = new HashMap<>();
        private final Map<String, AgentVersion> vers = new HashMap<>();

        @Override public void create(AgentDefinition d) { defs.put(d.agentId(), d); }
        @Override public void update(AgentDefinition d) { defs.put(d.agentId(), d); }
        @Override public Optional<AgentDefinition> findById(String id) {
            return Optional.ofNullable(defs.get(id));
        }
        @Override public AgentDefinitionPage page(String t, long c, long s, String k) {
            return new AgentDefinitionPage(List.of(), 0, s, c, 0);
        }
        @Override public long nextVersionNo(String id) { return 1; }
        @Override public void saveVersion(AgentVersion v) { vers.put(v.agentId() + ":" + v.versionId(), v); }
        @Override public Optional<AgentVersion> latestVersion(String id) {
            return vers.values().stream().filter(v -> id.equals(v.agentId())).findFirst();
        }
        @Override public Optional<AgentVersion> findVersion(String id, String vid) {
            return Optional.ofNullable(vers.get(id + ":" + vid));
        }
    }

    static class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun> runs = new HashMap<>();
        private final List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> steps = new ArrayList<>();

        @Override public void createRun(com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun r) {
            runs.put(r.runId(), r);
        }
        @Override public void updateRun(com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun r) {
            runs.put(r.runId(), r);
        }
        @Override public Optional<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun> findRunById(String id) {
            return Optional.ofNullable(runs.get(id));
        }
        @Override public void appendStep(com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep s) {
            steps.add(s);
        }
        @Override public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            return steps.stream().filter(s -> runId.equals(s.runId())).toList();
        }
    }
}
