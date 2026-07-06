/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskEventPort;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskOrchestrationServiceTests {

    @Test
    void createConversationalTaskPersistsAndReturnsRunningStatus() {
        FakeTaskRepository repository = new FakeTaskRepository();
        InMemoryTaskEventBus eventBus = new InMemoryTaskEventBus();
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                null,
                null,
                null,
                eventBus
        );

        Task task = service.createTask(new CreateTaskCommand(
                TaskType.QUICK_CHAT,
                "user-1",
                "Summarize the workspace",
                "conversation-1",
                null,
                "Workspace summary"
        ));

        Task persisted = repository.findById(task.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals(TaskStatus.RUNNING, persisted.getStatus());
        assertNotNull(task.getStartedAt());
        assertNotNull(persisted.getStartedAt());
        assertEquals(List.of(TaskEvent.CREATED, TaskEvent.STARTED),
                eventBus.history(task.getTaskId()).stream().map(TaskEvent::type).toList());
    }

    @Test
    void createAgentRunTaskUsesChatAgentExecutionWhenAvailable() {
        FakeTaskRepository repository = new FakeTaskRepository();
        InMemoryTaskEventBus eventBus = new InMemoryTaskEventBus();
        CompletingAgentChatPort chatPort = new CompletingAgentChatPort();
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                chatPort,
                new FailingAgentRunPort(),
                null,
                eventBus
        );

        Task task = service.createTask(new CreateTaskCommand(
                TaskType.AGENT_RUN,
                "42",
                "Generate Mermaid architecture",
                "conversation-1",
                "github-visual-project-intro-agent",
                "Mermaid architecture"
        ));

        Task persisted = repository.findById(task.getTaskId()).orElseThrow();
        assertEquals(ChatMode.AGENT, chatPort.command.chatMode());
        assertEquals("github-visual-project-intro-agent", chatPort.command.agentId());
        assertEquals("run-chat-1", persisted.getRunId());
        assertEquals(TaskStatus.SUCCEEDED, persisted.getStatus());
        assertEquals(List.of(
                        TaskEvent.CREATED,
                        TaskEvent.STARTED,
                        TaskEvent.MODEL_SELECTED,
                        TaskEvent.COMPLETED),
                eventBus.history(task.getTaskId()).stream().map(TaskEvent::type).toList());
    }

    @Test
    void createAgentRunTaskRedactsCredentialShapedChatStartFailureEvent() {
        FakeTaskRepository repository = new FakeTaskRepository();
        InMemoryTaskEventBus eventBus = new InMemoryTaskEventBus();
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                new ThrowingAgentChatPort(
                        "provider failed Authorization: Bearer abcdefghijklmnop api_key=plain-task-start-secret"),
                new FailingAgentRunPort(),
                null,
                eventBus
        );

        Task task = service.createTask(new CreateTaskCommand(
                TaskType.AGENT_RUN,
                "42",
                "Generate Mermaid architecture",
                "conversation-1",
                "github-visual-project-intro-agent",
                "Mermaid architecture"
        ));

        TaskEvent failed = lastEvent(eventBus, task.getTaskId());
        assertEquals(TaskStatus.FAILED, repository.findById(task.getTaskId()).orElseThrow().getStatus());
        assertEquals(TaskEvent.FAILED, failed.type());
        assertEquals("Agent start failed: provider failed [REDACTED] [REDACTED]", failed.message());
        assertEquals("provider failed [REDACTED] [REDACTED]", failed.data().get("error"));
        assertFalse(failed.message().contains("abcdefghijklmnop"));
        assertFalse(failed.message().contains("plain-task-start-secret"));
        assertFalse(String.valueOf(failed.data().get("error")).contains("abcdefghijklmnop"));
        assertFalse(String.valueOf(failed.data().get("error")).contains("plain-task-start-secret"));
    }

    @Test
    void createAgentRunTaskRedactsCredentialShapedStreamErrorEvent() {
        FakeTaskRepository repository = new FakeTaskRepository();
        InMemoryTaskEventBus eventBus = new InMemoryTaskEventBus();
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                new ErroringAgentChatPort(
                        "stream failed Authorization: Bearer qwertyuiopasdfgh password=plain-task-stream-secret"),
                new FailingAgentRunPort(),
                null,
                eventBus
        );

        Task task = service.createTask(new CreateTaskCommand(
                TaskType.AGENT_RUN,
                "42",
                "Generate Mermaid architecture",
                "conversation-1",
                "github-visual-project-intro-agent",
                "Mermaid architecture"
        ));

        TaskEvent failed = lastEvent(eventBus, task.getTaskId());
        assertEquals(TaskStatus.FAILED, repository.findById(task.getTaskId()).orElseThrow().getStatus());
        assertEquals(TaskEvent.FAILED, failed.type());
        assertEquals("Task failed: stream failed [REDACTED] [REDACTED]", failed.message());
        assertEquals("stream failed [REDACTED] [REDACTED]", failed.data().get("error"));
        assertEquals("run-chat-1", failed.data().get("runId"));
        assertFalse(failed.message().contains("qwertyuiopasdfgh"));
        assertFalse(failed.message().contains("plain-task-stream-secret"));
        assertFalse(String.valueOf(failed.data().get("error")).contains("qwertyuiopasdfgh"));
        assertFalse(String.valueOf(failed.data().get("error")).contains("plain-task-stream-secret"));
    }

    @Test
    void createAgentRunTaskPassesUserSnapshotToAsyncRunAndPolling() throws Exception {
        FakeTaskRepository repository = new FakeTaskRepository();
        InMemoryTaskEventBus eventBus = new InMemoryTaskEventBus();
        SnapshotRequiredAgentRunPort agentRunPort = new SnapshotRequiredAgentRunPort();
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                null,
                agentRunPort,
                null,
                eventBus
        );

        CurrentUser currentUser = new CurrentUser(42L, "admin", "admin", null, "default");
        Task task = service.createTask(new CreateTaskCommand(
                TaskType.AGENT_RUN,
                "42",
                "Generate Mermaid architecture",
                "conversation-1",
                "github-visual-project-intro-agent",
                "Mermaid architecture",
                null,
                null,
                null,
                currentUser
        ));

        assertTrue(agentRunPort.started.await(3, TimeUnit.SECONDS));
        assertTrue(agentRunPort.polled.await(3, TimeUnit.SECONDS));
        assertEquals("admin", agentRunPort.startedBy);
        assertEquals("admin", agentRunPort.polledBy);
        assertTrue(awaitStatus(repository, task.getTaskId(), TaskStatus.SUCCEEDED));
        assertEquals(TaskStatus.SUCCEEDED, repository.findById(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void ownerCanReadTaskWhenCurrentUserPortIsConfigured() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.QUICK_CHAT,
                "42",
                "conversation-1",
                null,
                "title",
                "question"
        ));
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(new CurrentUser(42L, "owner", "user", null));
        TaskOrchestrationService service = service(repository, new InMemoryTaskEventBus(), null, null, currentUserPort);

        assertEquals(task.getTaskId(), service.getTask(task.getTaskId()).getTaskId());
    }

    @Test
    void adminCanReadTaskForAnotherUser() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.QUICK_CHAT,
                "user-1",
                "conversation-1",
                null,
                "title",
                "question"
        ));
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(
                new CurrentUser(99L, "admin-1", "admin", null));
        TaskOrchestrationService service = service(repository, new InMemoryTaskEventBus(), null, null, currentUserPort);

        assertEquals(task.getTaskId(), service.getTask(task.getTaskId()).getTaskId());
    }

    @Test
    void unrelatedUserCannotReadTask() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.QUICK_CHAT,
                "user-1",
                "conversation-1",
                null,
                "title",
                "question"
        ));
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(
                new CurrentUser(99L, "user-99", "user", null));
        TaskOrchestrationService service = service(repository, new InMemoryTaskEventBus(), null, null, currentUserPort);

        assertThrows(IllegalStateException.class, () -> service.getTask(task.getTaskId()));
    }

    @Test
    void unrelatedUserCannotCancelTaskBeforeDownstreamCancellation() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.QUICK_CHAT,
                "user-1",
                "conversation-1",
                null,
                "title",
                "question"
        ).transitionTo(TaskStatus.RUNNING));
        CountingChatPort chatPort = new CountingChatPort();
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(
                new CurrentUser(99L, "user-99", "user", null));
        TaskOrchestrationService service = new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                chatPort,
                null,
                null,
                new InMemoryTaskEventBus(),
                currentUserPort
        );

        assertThrows(IllegalStateException.class, () -> service.cancelTask(task.getTaskId()));
        assertEquals(0, chatPort.stopTaskCalls.get());
        assertEquals(TaskStatus.RUNNING, repository.findById(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void unrelatedUserCannotReadOrSubscribeEventsBeforeEventBusAccess() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.QUICK_CHAT,
                "user-1",
                "conversation-1",
                null,
                "title",
                "question"
        ));
        CountingTaskEventPort eventPort = new CountingTaskEventPort();
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(
                new CurrentUser(99L, "user-99", "user", null));
        TaskOrchestrationService service = service(repository, eventPort, null, null, currentUserPort);

        assertThrows(IllegalStateException.class, () -> service.listEvents(task.getTaskId()));
        assertThrows(IllegalStateException.class, () -> service.subscribeEvents(task.getTaskId(), ignored -> {
        }));
        assertEquals(0, eventPort.historyCalls.get());
        assertEquals(0, eventPort.subscribeCalls.get());
    }

    @Test
    void unrelatedUserCannotListArtifactsBeforeArtifactQuery() {
        FakeTaskRepository repository = new FakeTaskRepository();
        Task task = repository.save(Task.create(
                TaskType.AGENT_RUN,
                "user-1",
                "conversation-1",
                "agent-1",
                "title",
                "question"
        ).withRunId("run-1"));
        CountingArtifactQueryPort artifactQueryPort = new CountingArtifactQueryPort();
        MutableCurrentUserPort currentUserPort = new MutableCurrentUserPort(
                new CurrentUser(99L, "user-99", "user", null));
        TaskOrchestrationService service = service(repository, new InMemoryTaskEventBus(), artifactQueryPort, null,
                currentUserPort);

        assertThrows(IllegalStateException.class, () -> service.listArtifacts(task.getTaskId()));
        assertEquals(0, artifactQueryPort.listByRunIdCalls.get());
    }

    private static TaskOrchestrationService service(FakeTaskRepository repository,
                                                    TaskEventPort eventPort,
                                                    AgentArtifactQueryInboundPort artifactQueryPort,
                                                    AgentRunInboundPort agentRunPort,
                                                    CurrentUserPort currentUserPort) {
        return new TaskOrchestrationService(
                repository,
                new StubConversationManagementPort(),
                null,
                agentRunPort,
                artifactQueryPort,
                eventPort,
                currentUserPort
        );
    }

    private static boolean awaitStatus(FakeTaskRepository repository, String taskId, TaskStatus status)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (repository.findById(taskId).map(Task::getStatus).filter(status::equals).isPresent()) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static TaskEvent lastEvent(InMemoryTaskEventBus eventBus, String taskId) {
        List<TaskEvent> events = eventBus.history(taskId);
        return events.get(events.size() - 1);
    }

    private static final class FakeTaskRepository implements TaskRepositoryPort {
        private final Map<String, Task> tasks = new ConcurrentHashMap<>();

        @Override
        public Task save(Task task) {
            tasks.put(task.getTaskId(), task);
            return task;
        }

        @Override
        public Optional<Task> findById(String taskId) {
            return Optional.ofNullable(tasks.get(taskId));
        }

        @Override
        public List<Task> findByUserId(String userId, int limit) {
            return tasks.values().stream()
                    .filter(task -> task.getUserId().equals(userId))
                    .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void updateStatus(String taskId, TaskStatus status) {
            tasks.computeIfPresent(taskId, (ignored, task) -> task.transitionTo(status));
        }

        @Override
        public void updateRunId(String taskId, String runId) {
            tasks.computeIfPresent(taskId, (ignored, task) -> task.withRunId(runId));
        }

        @Override
        public void updateConversationId(String taskId, String conversationId) {
            tasks.computeIfPresent(taskId, (ignored, task) -> task.withConversationId(conversationId));
        }

        @Override
        public Optional<Task> findRunningByConversationId(String conversationId) {
            return tasks.values().stream()
                    .filter(t -> conversationId.equals(t.getConversationId()))
                    .filter(t -> t.getStatus() == TaskStatus.RUNNING)
                    .findFirst();
        }
    }

    private static final class StubConversationManagementPort implements ConversationManagementInboundPort {
        @Override
        public String create(String userId) {
            return "conversation-created";
        }

        @Override
        public List<ConversationRecord> listConversations(String userId) {
            return List.of();
        }

        @Override
        public void rename(String conversationId, String userId, String title) {
        }

        @Override
        public void delete(String conversationId, String userId) {
        }

        @Override
        public List<ConversationMessageRecord> listMessages(String conversationId, String userId) {
            return List.of();
        }
    }

    private static final class CompletingAgentChatPort implements ChatInboundPort {
        private StreamChatCommand command;

        @Override
        public void streamChat(StreamChatCommand command, StreamCallback callback) {
            this.command = command;
            callback.onRunStarted("run-chat-1");
            callback.onContent("Generated report");
            callback.onComplete();
        }

        @Override
        public void stopTask(String taskId) {
        }
    }

    private static final class ThrowingAgentChatPort implements ChatInboundPort {
        private final String message;

        private ThrowingAgentChatPort(String message) {
            this.message = message;
        }

        @Override
        public void streamChat(StreamChatCommand command, StreamCallback callback) {
            throw new IllegalStateException(message);
        }

        @Override
        public void stopTask(String taskId) {
        }
    }

    private static final class ErroringAgentChatPort implements ChatInboundPort {
        private final String message;

        private ErroringAgentChatPort(String message) {
            this.message = message;
        }

        @Override
        public void streamChat(StreamChatCommand command, StreamCallback callback) {
            callback.onRunStarted("run-chat-1");
            callback.onError(new IllegalStateException(message));
        }

        @Override
        public void stopTask(String taskId) {
        }
    }

    private static final class CountingChatPort implements ChatInboundPort {
        private final AtomicInteger stopTaskCalls = new AtomicInteger();

        @Override
        public void streamChat(StreamChatCommand command, StreamCallback callback) {
        }

        @Override
        public void stopTask(String taskId) {
            stopTaskCalls.incrementAndGet();
        }
    }

    private static final class MutableCurrentUserPort implements CurrentUserPort {
        private CurrentUser currentUser;

        private MutableCurrentUserPort(CurrentUser currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public Optional<CurrentUser> currentUser() {
            return Optional.ofNullable(currentUser);
        }
    }

    private static final class CountingTaskEventPort implements TaskEventPort {
        private final AtomicInteger historyCalls = new AtomicInteger();
        private final AtomicInteger subscribeCalls = new AtomicInteger();

        @Override
        public TaskEvent publish(String taskId, String type, String message, Map<String, Object> data) {
            return new TaskEvent(taskId, 1L, type, message, data, Instant.now());
        }

        @Override
        public List<TaskEvent> history(String taskId) {
            historyCalls.incrementAndGet();
            return List.of();
        }

        @Override
        public AutoCloseable subscribe(String taskId, Consumer<TaskEvent> listener) {
            subscribeCalls.incrementAndGet();
            return () -> {
            };
        }

        @Override
        public void complete(String taskId) {
        }
    }

    private static final class CountingArtifactQueryPort implements AgentArtifactQueryInboundPort {
        private final AtomicInteger listByRunIdCalls = new AtomicInteger();

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.empty();
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            listByRunIdCalls.incrementAndGet();
            return List.of(new AgentArtifact(
                    "artifact-1",
                    runId,
                    null,
                    "default",
                    "user-1",
                    AgentArtifactType.REPORT,
                    "Report",
                    "text/markdown",
                    "memory://artifact-1",
                    "preview",
                    null,
                    AgentArtifactScanStatus.CLEAN,
                    Instant.now()
            ));
        }

        @Override
        public AgentArtifactDownloadDecision downloadDecision(String artifactId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailingAgentRunPort extends SnapshotRequiredAgentRunPort {
        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            throw new AssertionError("chat-backed agent tasks should not call AgentRunInboundPort directly");
        }
    }

    private static class SnapshotRequiredAgentRunPort implements AgentRunInboundPort {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch polled = new CountDownLatch(1);
        private volatile String startedBy;
        private volatile String polledBy;

        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            if (command.currentUser() == null) {
                throw new IllegalStateException("missing user snapshot");
            }
            startedBy = command.currentUser().operator();
            started.countDown();
            return run(AgentRunStatus.RUNNING);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            throw new IllegalStateException("thread-local current user should not be used");
        }

        @Override
        public Optional<AgentRun> findRunById(String runId, CurrentUser currentUser) {
            if (currentUser == null) {
                throw new IllegalStateException("missing polling user snapshot");
            }
            polledBy = currentUser.operator();
            polled.countDown();
            return Optional.of(run(AgentRunStatus.SUCCEEDED));
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            return List.of();
        }

        @Override
        public AgentRun cancel(String runId) {
            return run(AgentRunStatus.CANCELLED);
        }

        @Override
        public AgentRun retry(String runId) {
            return run(AgentRunStatus.RETRYING);
        }

        @Override
        public AgentRun succeed(String runId) {
            return run(AgentRunStatus.SUCCEEDED);
        }

        @Override
        public AgentRun fail(String runId, String errorCode, String errorMessage) {
            return run(AgentRunStatus.FAILED);
        }

        private AgentRun run(AgentRunStatus status) {
            return new AgentRun(
                    "run-1",
                    "github-visual-project-intro-agent",
                    "version-1",
                    "default",
                    "admin",
                    "conversation-1",
                    AgentRunTriggerType.API,
                    "Generate Mermaid architecture",
                    status,
                    null,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    null,
                    null,
                    Instant.parse("2026-06-27T00:00:00Z"),
                    status.isFinished() ? Instant.parse("2026-06-27T00:00:01Z") : null);
        }
    }
}
