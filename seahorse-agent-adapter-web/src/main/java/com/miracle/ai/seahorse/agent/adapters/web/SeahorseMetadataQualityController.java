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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 元数据治理质量报表 Web 适配器。
 *
 * <p>控制器只暴露报表查询契约，字段覆盖率、低置信度和待处理隔离项的统计口径
 * 由 kernel 入站端口统一约束。
 */
@RestController
@ConditionalOnBean(MetadataQualityInboundPort.class)
public class SeahorseMetadataQualityController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final MetadataQualityInboundPort qualityPort;

    public SeahorseMetadataQualityController(MetadataQualityInboundPort qualityPort) {
        this.qualityPort = Objects.requireNonNull(qualityPort, "qualityPort must not be null");
    }

    @GetMapping("/knowledge-base/{kb-id}/metadata-quality/report")
    public Map<String, Object> report(@PathVariable("kb-id") String kbId,
                                      @RequestParam String tenantId,
                                      @RequestParam(value = "schemaVersion", required = false) Integer schemaVersion,
                                      @RequestParam(value = "extractorVersion", required = false) String extractorVersion,
                                      @RequestParam(value = "llmPromptVersion", required = false) String llmPromptVersion,
                                      @RequestParam(defaultValue = "5") int topN) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                qualityPort.report(tenantId, kbId, topN, schemaVersion, extractorVersion, llmPromptVersion));
    }

    @GetMapping("/knowledge-base/{kb-id}/metadata-quality/compare")
    public Map<String, Object> compare(@PathVariable("kb-id") String kbId,
                                       @RequestParam String tenantId,
                                       @RequestParam(value = "baselineSchemaVersion", required = false) Integer baselineSchemaVersion,
                                       @RequestParam(value = "baselineExtractorVersion", required = false) String baselineExtractorVersion,
                                       @RequestParam(value = "baselineLlmPromptVersion", required = false) String baselineLlmPromptVersion,
                                       @RequestParam(value = "candidateSchemaVersion", required = false) Integer candidateSchemaVersion,
                                       @RequestParam(value = "candidateExtractorVersion", required = false) String candidateExtractorVersion,
                                       @RequestParam(value = "candidateLlmPromptVersion", required = false) String candidateLlmPromptVersion,
                                       @RequestParam(defaultValue = "5") int topN) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, qualityPort.compare(
                tenantId,
                kbId,
                topN,
                baselineSchemaVersion,
                baselineExtractorVersion,
                baselineLlmPromptVersion,
                candidateSchemaVersion,
                candidateExtractorVersion,
                candidateLlmPromptVersion));
    }
}
