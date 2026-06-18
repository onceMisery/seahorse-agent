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

import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskUploadCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生入库任务 Web adapter。
 */
@RestController
public class SeahorseIngestionTaskController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "";

    private final ObjectProvider<IngestionTaskInboundPort> taskPortProvider;
    private final RateLimiterPort rateLimiterPort;
    private final int uploadRateLimitPermits;
    private final Duration uploadRateLimitWindow;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseIngestionTaskController(ObjectProvider<IngestionTaskInboundPort> taskPortProvider) {
        this(taskPortProvider, RateLimiterPort.noop(), 20, Duration.ofMinutes(1).toMillis(),
                AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseIngestionTaskController(ObjectProvider<IngestionTaskInboundPort> taskPortProvider,
                                           ObjectProvider<RateLimiterPort> rateLimiterPortProvider,
                                           @Value("${seahorse-agent.web.upload-rate-limit.permits:20}")
                                           int uploadRateLimitPermits,
                                           @Value("${seahorse-agent.web.upload-rate-limit.window-ms:60000}")
                                           long uploadRateLimitWindowMs,
                                           ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(taskPortProvider, rateLimiterPortProvider.getIfAvailable(RateLimiterPort::noop),
                uploadRateLimitPermits, uploadRateLimitWindowMs,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseIngestionTaskController(ObjectProvider<IngestionTaskInboundPort> taskPortProvider,
                                           RateLimiterPort rateLimiterPort,
                                           int uploadRateLimitPermits,
                                           long uploadRateLimitWindowMs,
                                           AdvancedFeatureGate advancedFeatureGate) {
        this.taskPortProvider = taskPortProvider;
        this.rateLimiterPort = Objects.requireNonNullElse(rateLimiterPort, RateLimiterPort.noop());
        this.uploadRateLimitPermits = Math.max(1, uploadRateLimitPermits);
        this.uploadRateLimitWindow = Duration.ofMillis(Math.max(1L, uploadRateLimitWindowMs));
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/ingestion/tasks")
    public ApiResponse<Object> create(@RequestBody IngestionTaskRequest request,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        return ApiResponses.requireServiceOrError(taskPortProvider,
                port -> port.execute(toCommand(request, operator(userId))));
    }

    @PostMapping(value = "/ingestion/tasks/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Object> upload(@RequestParam("pipelineId") String pipelineId,
                                      @RequestPart("file") MultipartFile file,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId)
            throws IOException {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        String operator = operator(userId);
        checkUploadRateLimit(operator);
        IngestionTaskUploadCommand command = new IngestionTaskUploadCommand(
                pipelineId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                operator);
        return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.upload(command));
    }

    @GetMapping("/ingestion/tasks/{id}")
    public ApiResponse<Object> get(@PathVariable String id) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.get(id));
    }

    @GetMapping("/ingestion/tasks/{id}/nodes")
    public ApiResponse<Object> nodes(@PathVariable String id) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.listNodes(id));
    }

    @PostMapping("/ingestion/tasks/{id}/retry")
    public ApiResponse<Object> retry(@PathVariable String id,
                                     @RequestBody(required = false) IngestionTaskRetryRequest request,
                                     @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        String fromNodeId = request == null ? null : request.getFromNodeId();
        if (fromNodeId == null || fromNodeId.isBlank()) {
            return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.retry(id, operator(userId)));
        }
        return ApiResponses.requireServiceOrError(taskPortProvider,
                port -> port.retry(id, fromNodeId.trim(), operator(userId)));
    }

    @PostMapping("/ingestion/tasks/{id}/rollback")
    public ApiResponse<Object> rollback(@PathVariable String id,
                                        @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.rollback(id, operator(userId)));
    }

    @GetMapping("/ingestion/tasks")
    public ApiResponse<Object> page(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                    @RequestParam(value = "status", required = false) String status) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INGESTION_TASK_MANAGEMENT);
        return ApiResponses.requireServiceOrError(taskPortProvider, port -> port.page(pageNo, pageSize, status));
    }

    private IngestionTaskCreateCommand toCommand(IngestionTaskRequest request, String operator) {
        IngestionTaskRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return new IngestionTaskCreateCommand(
                safeRequest.getPipelineId(),
                toSource(safeRequest.getSource()),
                safeRequest.getMetadata(),
                safeRequest.getVectorSpaceId(),
                operator);
    }

    private IngestionDocumentSource toSource(IngestionDocumentSourceRequest request) {
        IngestionDocumentSourceRequest safeRequest = Objects.requireNonNull(request, "source must not be null");
        return new IngestionDocumentSource(
                safeRequest.getType(),
                safeRequest.getLocation(),
                safeRequest.getFileName(),
                safeRequest.getCredentials());
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }

    private void checkUploadRateLimit(String operator) {
        RateLimitDecision decision = rateLimiterPort.tryAcquire(
                "upload", operator, uploadRateLimitPermits, uploadRateLimitWindow);
        if (!decision.allowed()) {
            throw new IllegalStateException("upload rate limit exceeded");
        }
    }
}
