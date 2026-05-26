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
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;

import java.util.Objects;

public class KernelFeedbackEvaluationCandidateQueryService implements FeedbackEvaluationCandidateQueryInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "权限不足";

    private final FeedbackEvaluationCandidateQueryPort queryPort;
    private final CurrentUserPort currentUserPort;

    public KernelFeedbackEvaluationCandidateQueryService(FeedbackEvaluationCandidateQueryPort queryPort,
                                                         CurrentUserPort currentUserPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public FeedbackEvaluationCandidatePage page(FeedbackEvaluationCandidateQuery query) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        if (!currentUser.hasRole(ADMIN_ROLE)) {
            throw new IllegalStateException(ACCESS_DENIED);
        }
        return queryPort.page(Objects.requireNonNull(query, "query must not be null"));
    }
}
