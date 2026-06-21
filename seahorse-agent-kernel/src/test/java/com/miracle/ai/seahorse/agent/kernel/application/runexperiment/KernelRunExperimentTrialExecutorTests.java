/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.runexperiment;

import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class KernelRunExperimentTrialExecutorTests {

    @Test
    void shouldExecuteBaseLeafWithRunProfileAndAppendAssistantBranch() {
        InMemoryBranchRepository branchRepository = new InMemoryBranchRepository();
        branchRepository.messages.add(message("1", null, "user", "previous context"));
        branchRepository.messages.add(message("202", 1L, "user", "Explain this code"));
        branchRepository.messages.add(message("203", 202L, "assistant", "old answer"));
        InMemoryRunProfileRepository profileRepository = new InMemoryRunProfileRepository();
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        RecordingExecutor executor = new RecordingExecutor();
        KernelRunExperimentTrialExecutor trialExecutor =
                new KernelRunExperimentTrialExecutor(executor, branchRepository, profileRepository, snapshotRepository);

        RunExperimentTrialExecutionResult result = trialExecutor.execute(RunExperimentTrialExecutionRequest.builder()
                .userId("100")
                .experimentId(1L)
                .trialId(10L)
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .runProfileId(12L)
                .experimentName("Profile compare")
                .build());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(301L, result.getOutputMessageId());
        assertEquals("Explain this code", executor.request.question());
        assertEquals("agentscope", executor.request.executorEngine());
        assertIterableEquals(List.of("filesystem.read_file"), executor.request.allowedToolIds());
        assertEquals("previous context", executor.request.history().get(0).getContent());
        assertEquals("assistant", branchRepository.appended.getRole());
        assertEquals("generated answer", branchRepository.appended.getContent());
        assertEquals(202L, branchRepository.appended.getParentId());
        assertEquals("run-exp-1-trial-10", branchRepository.appended.getAgentRunId());
        assertEquals("run-exp-1-trial-10", result.getRunId());
        assertEquals("run-exp-1-trial-10", snapshotRepository.saved.getRunId());
        assertEquals(101L, snapshotRepository.saved.getConversationId());
        assertEquals(202L, snapshotRepository.saved.getBranchLeafMessageId());
        assertEquals(12L, snapshotRepository.saved.getRunProfileId());
        assertEquals("agentscope", snapshotRepository.saved.getExecutorEngine());
        assertEquals("{\"studio\":true}", snapshotRepository.saved.getExecutorConfigJson());
        assertEquals("{\"experimentId\":1,\"trialId\":10,\"experimentName\":\"Profile compare\"}",
                snapshotRepository.saved.getTraceContextJson());
        assertEquals("""
                {"runProfileId":12,"executorEngine":"agentscope","roleCardId":99,"allowedToolIds":["filesystem.read_file"],"baseLeafMessageId":202}
                """.trim(), snapshotRepository.saved.getSnapshotJson());
    }

    @Test
    void shouldRegenerateFromParentUserPromptWhenBaseLeafIsAssistantMessage() {
        InMemoryBranchRepository branchRepository = new InMemoryBranchRepository();
        branchRepository.messages.add(message("1", null, "user", "previous context"));
        branchRepository.messages.add(message("202", 1L, "user", "Explain this code"));
        branchRepository.messages.add(message("203", 202L, "assistant", "old answer"));
        InMemoryRunProfileRepository profileRepository = new InMemoryRunProfileRepository();
        RecordingRunContextSnapshotRepository snapshotRepository = new RecordingRunContextSnapshotRepository();
        RecordingExecutor executor = new RecordingExecutor();
        KernelRunExperimentTrialExecutor trialExecutor =
                new KernelRunExperimentTrialExecutor(executor, branchRepository, profileRepository, snapshotRepository);

        RunExperimentTrialExecutionResult result = trialExecutor.execute(RunExperimentTrialExecutionRequest.builder()
                .userId("100")
                .experimentId(1L)
                .trialId(10L)
                .conversationId(101L)
                .baseLeafMessageId(203L)
                .runProfileId(12L)
                .experimentName("Profile compare")
                .build());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("Explain this code", executor.request.question());
        assertEquals(1, executor.request.history().size());
        assertEquals("previous context", executor.request.history().get(0).getContent());
        assertEquals(202L, branchRepository.appended.getParentId());
        assertEquals(203L, snapshotRepository.saved.getBranchLeafMessageId());
        assertEquals("""
                {"runProfileId":12,"executorEngine":"agentscope","roleCardId":99,"allowedToolIds":["filesystem.read_file"],"baseLeafMessageId":203}
                """.trim(), snapshotRepository.saved.getSnapshotJson());
    }

    private static ConversationMessageRecord message(String id, Long parentId, String role, String content) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(id);
        record.setConversationId("101");
        record.setUserId("100");
        record.setParentId(parentId);
        record.setRole(role);
        record.setContent(content);
        return record;
    }

    private static final class RecordingExecutor implements ReActExecutorPort {

        private AgentLoopRequest request;

        @Override
        public AgentLoopResult execute(AgentLoopRequest request) {
            this.request = request;
            return new AgentLoopResult("generated answer", List.of(AgentStep.finalAnswer("generated answer")), false);
        }

        @Override
        public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String engineId() {
            return "agentscope";
        }
    }

    private static final class InMemoryBranchRepository implements ConversationBranchRepositoryPort {

        private final List<ConversationMessageRecord> messages = new ArrayList<>();
        private ConversationMessageRecord appended;

        @Override
        public Long appendMessage(ConversationMessageRecord record) {
            record.setId("301");
            appended = record;
            messages.add(record);
            return 301L;
        }

        @Override
        public List<ConversationMessageRecord> listSiblings(String conversationId, String userId, Long parentId) {
            return messages.stream()
                    .filter(message -> parentId.equals(message.getParentId()))
                    .toList();
        }

        @Override
        public List<ConversationMessageRecord> listTree(String conversationId, String userId) {
            return List.copyOf(messages);
        }

        @Override
        public void setActivePath(String conversationId, String userId, Set<Long> activeIds) {
        }

        @Override
        public ConversationBranchCursor upsertCursor(String conversationId, String userId, Long leafMessageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ConversationBranchCursor> findCursor(String conversationId, String userId) {
            return Optional.empty();
        }
    }

    private static final class InMemoryRunProfileRepository implements RunProfileRepositoryPort {

        @Override
        public List<RunProfileRecord> listByUser(String userId) {
            return List.of();
        }

        @Override
        public Optional<RunProfileRecord> findById(String userId, Long id) {
            RunProfileRecord record = new RunProfileRecord();
            record.setId(id);
            record.setUserId(userId);
            record.setExecutorEngine("agentscope");
            record.setExecutorConfigJson("{\"studio\":true}");
            record.setRoleCardId(99L);
            return Optional.of(record);
        }

        @Override
        public Long save(RunProfileRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceTools(Long profileId, List<RunProfileToolBindingRecord> tools) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RunProfileToolBindingRecord> listTools(Long profileId) {
            return List.of(
                    RunProfileToolBindingRecord.builder()
                            .profileId(profileId)
                            .toolId("filesystem.read_file")
                            .enabled(1)
                            .build(),
                    RunProfileToolBindingRecord.builder()
                            .profileId(profileId)
                            .toolId("danger.disabled")
                            .enabled(0)
                            .build());
        }

        @Override
        public void disableAll(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEnabled(String userId, Long id, boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String userId, Long id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingRunContextSnapshotRepository implements RunContextSnapshotRepositoryPort {

        private RunContextSnapshotRecord saved;

        @Override
        public Long save(RunContextSnapshotRecord record) {
            saved = record;
            return 401L;
        }

        @Override
        public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
            return Optional.ofNullable(saved)
                    .filter(snapshot -> snapshot.getRunId().equals(runId));
        }
    }
}
