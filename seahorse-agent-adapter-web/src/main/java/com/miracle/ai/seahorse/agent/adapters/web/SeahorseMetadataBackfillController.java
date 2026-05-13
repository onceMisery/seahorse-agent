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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据历史回填管理 Web adapter。
 *
 * <p>Web 层只负责把管理动作转成入站命令；分页扫描、断点推进、Review/Quarantine
 * 统计都由 kernel 回填服务统一处理，避免控制器绕过治理边界。
 */
@RestController
@ConditionalOnBean(MetadataBackfillInboundPort.class)
public class SeahorseMetadataBackfillController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "system";

    private final MetadataBackfillInboundPort backfillPort;

    public SeahorseMetadataBackfillController(MetadataBackfillInboundPort backfillPort) {
        this.backfillPort = Objects.requireNonNull(backfillPort, "backfillPort must not be null");
    }

    @PostMapping("/knowledge-base/{kb-id}/metadata-backfill/jobs")
    public Map<String, Object> createJob(@PathVariable("kb-id") String kbId,
                                         @RequestBody(required = false) MetadataBackfillCreateRequest request,
                                         @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                backfillPort.createJob(toCommand(kbId, request, operator(userId))));
    }

    @GetMapping("/knowledge-base/{kb-id}/metadata-backfill/jobs")
    public Map<String, Object> pageJobs(@PathVariable("kb-id") String kbId,
                                        @RequestParam(value = "tenantId", required = false) String tenantId,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam(value = "current", defaultValue = "1") long current,
                                        @RequestParam(value = "size", defaultValue = "10") long size) {
        // 列表查询只拼装筛选条件，具体分页和状态口径由 kernel 统一处理。
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                backfillPort.pageJobs(new MetadataBackfillJobQuery(
                        tenantId, kbId, status(status), current, size)));
    }

    @GetMapping("/metadata-backfill/jobs/{job-id}")
    public Map<String, Object> getJob(@PathVariable("job-id") String jobId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, backfillPort.getJob(jobId));
    }

    @PostMapping("/metadata-backfill/jobs/{job-id}/run-next")
    public Map<String, Object> runNextBatch(@PathVariable("job-id") String jobId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, backfillPort.runNextBatch(jobId));
    }

    @PostMapping("/metadata-backfill/jobs/{job-id}/pause")
    public Map<String, Object> pause(@PathVariable("job-id") String jobId,
                                     @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, backfillPort.pause(jobId, operator(userId)));
    }

    @PostMapping("/metadata-backfill/jobs/{job-id}/resume")
    public Map<String, Object> resume(@PathVariable("job-id") String jobId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, backfillPort.resume(jobId, operator(userId)));
    }

    @PostMapping("/metadata-backfill/jobs/{job-id}/cancel")
    public Map<String, Object> cancel(@PathVariable("job-id") String jobId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, backfillPort.cancel(jobId, operator(userId)));
    }

    private MetadataBackfillCommand toCommand(String kbId,
                                              MetadataBackfillCreateRequest request,
                                              String operator) {
        MetadataBackfillCreateRequest safeRequest = request == null ? new MetadataBackfillCreateRequest() : request;
        return new MetadataBackfillCommand(
                safeRequest.getTenantId(),
                kbId,
                safeRequest.getPipelineId(),
                safeRequest.getBatchSize() == null ? 0 : safeRequest.getBatchSize(),
                operator,
                safeRequest.getMetadata());
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }

    private MetadataBackfillJobStatus status(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return MetadataBackfillJobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    }
}
