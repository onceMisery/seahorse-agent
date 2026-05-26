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

package com.miracle.ai.seahorse.agent.ports.outbound.feedback;

import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;

import java.util.List;

public interface FeedbackEvaluationCandidateQueryPort {

    FeedbackEvaluationCandidatePage page(FeedbackEvaluationCandidateQuery query);

    static FeedbackEvaluationCandidateQueryPort empty() {
        return query -> new FeedbackEvaluationCandidatePage(
                List.of(),
                0L,
                query == null ? FeedbackEvaluationCandidateQuery.DEFAULT_PAGE_SIZE : query.size(),
                query == null ? FeedbackEvaluationCandidateQuery.DEFAULT_CURRENT : query.current(),
                0L);
    }
}
