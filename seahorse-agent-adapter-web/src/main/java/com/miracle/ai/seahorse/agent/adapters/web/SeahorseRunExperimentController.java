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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentReport;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
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

@RestController
public class SeahorseRunExperimentController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    @NonNull
    private final ObjectProvider<RunExperimentInboundPort> runExperimentPortProvider;
    private final ObjectProvider<ConversationBranchInboundPort> branchPortProvider;
    @NonNull
    private final ObjectMapper objectMapper;

    public SeahorseRunExperimentController(ObjectProvider<RunExperimentInboundPort> runExperimentPortProvider) {
        this(runExperimentPortProvider, null, new ObjectMapper());
    }

    public SeahorseRunExperimentController(
            ObjectProvider<RunExperimentInboundPort> runExperimentPortProvider,
            ObjectProvider<ConversationBranchInboundPort> branchPortProvider) {
        this(runExperimentPortProvider, branchPortProvider, new ObjectMapper());
    }

    @Autowired
    public SeahorseRunExperimentController(
            ObjectProvider<RunExperimentInboundPort> runExperimentPortProvider,
            ObjectProvider<ConversationBranchInboundPort> branchPortProvider,
            ObjectMapper objectMapper) {
        this.runExperimentPortProvider = Objects.requireNonNull(
                runExperimentPortProvider,
                "runExperimentPortProvider must not be null");
        this.branchPortProvider = branchPortProvider;
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @PostMapping({"/run-experiments", "/api/run-experiments"})
    public Map<String, Object> create(@RequestBody RunExperimentRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, runExperimentPort().create(command(
                resolveUserId(userId, headerUserId),
                request)));
    }

    @GetMapping({"/run-experiments/{id}", "/api/run-experiments/{id}"})
    public Map<String, Object> get(@PathVariable Long id,
                                   @RequestParam(required = false) String userId,
                                   @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                   String headerUserId) {
        return runExperimentPort()
                .findById(resolveUserId(userId, headerUserId), id)
                .<Map<String, Object>>map(details -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, details))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    @GetMapping({"/run-experiments/{id}/report", "/api/run-experiments/{id}/report"})
    public Map<String, Object> report(@PathVariable Long id,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        RunExperimentReport report = runExperimentPort().exportReport(resolveUserId(userId, headerUserId), id);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, report);
    }

    @PostMapping({"/run-experiments/{id}/cancel", "/api/run-experiments/{id}/cancel"})
    public Map<String, Object> cancel(@PathVariable Long id,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        return Map.of(
                KEY_CODE,
                SUCCESS_CODE,
                KEY_DATA,
                runExperimentPort().cancel(resolveUserId(userId, headerUserId), id));
    }

    @PostMapping({
            "/run-experiments/{id}/trials/{trialId}/score",
            "/api/run-experiments/{id}/trials/{trialId}/score"
    })
    public Map<String, Object> scoreTrial(@PathVariable Long id,
                                          @PathVariable Long trialId,
                                          @RequestBody RunExperimentTrialScoreRequest request,
                                          @RequestParam(required = false) String userId,
                                          @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                          String headerUserId) {
        return Map.of(
                KEY_CODE,
                SUCCESS_CODE,
                KEY_DATA,
                runExperimentPort().scoreTrial(
                        resolveUserId(userId, headerUserId),
                        id,
                        trialId,
                        scoreJson(request)));
    }

    @PostMapping({
            "/run-experiments/{id}/trials/{trialId}/fork-to-branch",
            "/api/run-experiments/{id}/trials/{trialId}/fork-to-branch"
    })
    public Map<String, Object> forkTrialToBranch(@PathVariable Long id,
                                                 @PathVariable Long trialId,
                                                 @RequestParam(required = false) String userId,
                                                 @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID,
                                                         required = false)
                                                 String headerUserId) {
        String safeUserId = resolveUserId(userId, headerUserId);
        RunExperimentDetails details = runExperimentPort()
                .findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run experiment not found"));
        RunExperimentTrialRecord trial = details.getTrials()
                .stream()
                .filter(candidate -> Objects.equals(candidate.getId(), trialId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("run experiment trial not found"));
        if (trial.getOutputMessageId() == null) {
            throw new IllegalArgumentException("run experiment trial has no output message");
        }
        return Map.of(
                KEY_CODE,
                SUCCESS_CODE,
                KEY_DATA,
                Map.of(
                        "trialId", trialId,
                        "outputMessageId", trial.getOutputMessageId(),
                        "branch", branchPort().switchBranch(
                                String.valueOf(details.getExperiment().getConversationId()),
                                safeUserId,
                                trial.getOutputMessageId())));
    }

    private RunExperimentCommand command(String userId, RunExperimentRequest request) {
        RunExperimentRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return RunExperimentCommand.builder()
                .userId(userId)
                .conversationId(safeRequest.getConversationId())
                .baseLeafMessageId(safeRequest.getBaseLeafMessageId())
                .name(safeRequest.getName())
                .runProfileIds(safeRequest.getRunProfileIds())
                .build();
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }

    private String scoreJson(RunExperimentTrialScoreRequest request) {
        RunExperimentTrialScoreRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.getScoreJson() != null && !safeRequest.getScoreJson().isBlank()) {
            return safeRequest.getScoreJson().trim();
        }
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(safeRequest.getScore(), Map.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid run experiment score", ex);
        }
    }

    private RunExperimentInboundPort runExperimentPort() {
        RunExperimentInboundPort port = runExperimentPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("RunExperimentInboundPort is not configured");
        }
        return port;
    }

    private ConversationBranchInboundPort branchPort() {
        ConversationBranchInboundPort port = branchPortProvider == null ? null : branchPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("ConversationBranchInboundPort is not configured");
        }
        return port;
    }
}
