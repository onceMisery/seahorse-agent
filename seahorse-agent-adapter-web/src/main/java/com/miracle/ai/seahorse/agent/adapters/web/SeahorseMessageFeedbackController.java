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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.SubmitMessageFeedbackCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生消息反馈 Web 入站适配器。
 *
 * <p> 用户 ID 优先从请求参数或 {@code X-User-Id} 请求头传入；未显式传入时使用当前登录用户。
 */
@RestController
public class SeahorseMessageFeedbackController {

    private final ObjectProvider<MessageFeedbackInboundPort> feedbackInboundPortProvider;
    private final ObjectProvider<FeedbackEvaluationCandidateQueryInboundPort> candidateQueryPortProvider;

    public SeahorseMessageFeedbackController(ObjectProvider<MessageFeedbackInboundPort> feedbackInboundPortProvider) {
        this(feedbackInboundPortProvider, null);
    }

    public SeahorseMessageFeedbackController(ObjectProvider<MessageFeedbackInboundPort> feedbackInboundPortProvider,
                                             ObjectProvider<FeedbackEvaluationCandidateQueryInboundPort>
                                                     candidateQueryPortProvider) {
        this.feedbackInboundPortProvider = feedbackInboundPortProvider;
        this.candidateQueryPortProvider = candidateQueryPortProvider;
    }

    @PostMapping("/conversations/messages/{messageId}/feedback")
    public Map<String, Object> submitFeedback(@PathVariable String messageId,
                                              @RequestBody MessageFeedbackRequest request,
                                              @RequestParam(required = false) String userId,
                                              @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                              String headerUserId) {
        MessageFeedbackRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        feedbackInboundPortProvider.getIfAvailable().submit(new SubmitMessageFeedbackCommand(
                messageId,
                resolveUserId(userId, headerUserId),
                requireVote(safeRequest.vote()),
                safeRequest.reason(),
                safeRequest.comment()));
        return Map.of("code", "0");
    }

    @GetMapping("/api/feedback/evaluation-candidates")
    public ApiResponse<Object> evaluationCandidates(@RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String runId,
                                                    @RequestParam(required = false) String reason,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size) {
        return ApiResponses.requireService(candidateQueryPortProvider, port -> port.page(
                new FeedbackEvaluationCandidateQuery(userId, runId, reason, current, size)));
    }

    private int requireVote(Integer vote) {
        if (vote == null) {
            throw new IllegalArgumentException("vote must not be null");
        }
        return vote;
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }
}
