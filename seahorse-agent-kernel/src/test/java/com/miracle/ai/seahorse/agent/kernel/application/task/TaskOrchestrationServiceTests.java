/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

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
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
