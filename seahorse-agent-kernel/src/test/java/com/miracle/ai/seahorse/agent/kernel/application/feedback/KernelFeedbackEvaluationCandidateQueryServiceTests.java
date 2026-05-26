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

package com.miracle.ai.seahorse.agent.kernel.application.feedback;

import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelFeedbackEvaluationCandidateQueryServiceTests {

    @Test
    void shouldAllowAdminToQueryFeedbackEvaluationCandidates() {
        CapturingCandidateQueryPort queryPort = new CapturingCandidateQueryPort();
        KernelFeedbackEvaluationCandidateQueryService service = new KernelFeedbackEvaluationCandidateQueryService(
                queryPort,
                currentUser("admin-1", "admin"));

        FeedbackEvaluationCandidatePage page = service.page(new FeedbackEvaluationCandidateQuery(
                "user-1",
                "run-1",
                "INCORRECT",
                1L,
                20L));

        assertEquals(0L, page.total());
        assertEquals("run-1", queryPort.lastQuery.runId());
        assertEquals("INCORRECT", queryPort.lastQuery.reason());
    }

    @Test
    void shouldRejectNonAdminFeedbackEvaluationCandidateQuery() {
        KernelFeedbackEvaluationCandidateQueryService service = new KernelFeedbackEvaluationCandidateQueryService(
                new CapturingCandidateQueryPort(),
                currentUser("user-1", "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.page(new FeedbackEvaluationCandidateQuery(null, null, null, 1L, 20L)));

        assertEquals("权限不足", error.getMessage());
    }

    private static CurrentUserPort currentUser(String userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, userId, role, null));
    }

    private static final class CapturingCandidateQueryPort implements FeedbackEvaluationCandidateQueryPort {

        private FeedbackEvaluationCandidateQuery lastQuery;

        @Override
        public FeedbackEvaluationCandidatePage page(FeedbackEvaluationCandidateQuery query) {
            this.lastQuery = query;
            return new FeedbackEvaluationCandidatePage(List.of(), 0L, query.size(), query.current(), 0L);
        }
    }
}
