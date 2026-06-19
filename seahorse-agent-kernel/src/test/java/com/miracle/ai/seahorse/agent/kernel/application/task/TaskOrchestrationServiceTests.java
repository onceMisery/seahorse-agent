/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.application.task;

import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
