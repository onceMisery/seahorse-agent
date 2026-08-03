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
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.SubmitMessageFeedbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;

import java.util.Objects;

/**
 * 消息反馈与评估候选查询的组合入站实现。
 *
 * <p>将消息反馈（{@link KernelMessageFeedbackService}）与反馈评估候选查询
 * （{@link KernelFeedbackEvaluationCandidateQueryService}）聚合到同一入站用例端口，
 * 供 Web 适配器通过契约依赖。</p>
 */
public class KernelMessageFeedbackFacade implements MessageFeedbackInboundPort {

    private final KernelMessageFeedbackService feedbackService;
    private final KernelFeedbackEvaluationCandidateQueryService candidateQueryService;

    public KernelMessageFeedbackFacade(KernelMessageFeedbackService feedbackService,
                                       KernelFeedbackEvaluationCandidateQueryService candidateQueryService) {
        this.feedbackService = Objects.requireNonNull(feedbackService, "feedbackService must not be null");
        this.candidateQueryService = Objects.requireNonNull(
                candidateQueryService, "candidateQueryService must not be null");
    }

    @Override
    public void submit(SubmitMessageFeedbackCommand command) {
        feedbackService.submit(command);
    }

    @Override
    public FeedbackEvaluationCandidatePage page(FeedbackEvaluationCandidateQuery query) {
        return candidateQueryService.page(query);
    }
}
