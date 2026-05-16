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

package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨版本质量对比服务。
 *
 * <p>该服务不重新定义治理统计或检索评测口径，只负责组合既有对比能力，
 * 让管理端一次查询即可看到 metadata 治理与 retrieval 评测的双维度结果。
 */
public class KernelVersionQualityComparisonService implements VersionQualityComparisonInboundPort {

    private static final String EVENT_VERSION_COMPARE = "version.quality.compare.generated";

    private final MetadataQualityInboundPort metadataQualityPort;
    private final RetrievalEvaluationInboundPort retrievalEvaluationPort;
    private final ObservationPort observationPort;

    public KernelVersionQualityComparisonService(MetadataQualityInboundPort metadataQualityPort,
                                                 RetrievalEvaluationInboundPort retrievalEvaluationPort) {
        this(metadataQualityPort, retrievalEvaluationPort, null);
    }

    public KernelVersionQualityComparisonService(MetadataQualityInboundPort metadataQualityPort,
                                                 RetrievalEvaluationInboundPort retrievalEvaluationPort,
                                                 ObservationPort observationPort) {
        this.metadataQualityPort = Objects.requireNonNull(metadataQualityPort,
                "metadataQualityPort must not be null");
        this.retrievalEvaluationPort = Objects.requireNonNull(retrievalEvaluationPort,
                "retrievalEvaluationPort must not be null");
        this.observationPort = observationPort;
    }

    @Override
    public VersionQualityComparisonReport compare(VersionQualityComparisonCommand command) {
        VersionQualityComparisonCommand safeCommand = command == null
                ? new VersionQualityComparisonCommand("", "", 5,
                null, "", "", null, "", "", null)
                : command;
        MetadataQualityComparisonReport metadataComparison = metadataQualityPort.compare(
                safeCommand.tenantId(),
                safeCommand.knowledgeBaseId(),
                safeCommand.quarantineTopN(),
                normalizeSchemaVersion(safeCommand.baselineSchemaVersion()),
                safeCommand.baselineExtractorVersion(),
                safeCommand.baselineLlmPromptVersion(),
                normalizeSchemaVersion(safeCommand.candidateSchemaVersion()),
                safeCommand.candidateExtractorVersion(),
                safeCommand.candidateLlmPromptVersion());
        RetrievalEvaluationComparisonCommand retrievalCommand = safeCommand.retrievalComparison() == null
                ? new RetrievalEvaluationComparisonCommand("", 5, List.of(), List.of())
                : safeCommand.retrievalComparison();
        VersionQualityComparisonReport report = new VersionQualityComparisonReport(
                safeCommand.tenantId(),
                safeCommand.knowledgeBaseId(),
                metadataComparison,
                retrievalEvaluationPort.compare(retrievalCommand),
                Instant.now());
        recordComparison(report, safeCommand, retrievalCommand);
        return report;
    }

    private Integer normalizeSchemaVersion(Integer schemaVersion) {
        return schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
    }

    private void recordComparison(VersionQualityComparisonReport report,
                                  VersionQualityComparisonCommand command,
                                  RetrievalEvaluationComparisonCommand retrievalCommand) {
        if (observationPort == null || report == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", report.tenantId());
            attributes.put("knowledgeBaseId", report.knowledgeBaseId());
            if (command.baselineSchemaVersion() != null && command.baselineSchemaVersion() > 0) {
                attributes.put("baselineSchemaVersion", Integer.toString(command.baselineSchemaVersion()));
            }
            if (command.candidateSchemaVersion() != null && command.candidateSchemaVersion() > 0) {
                attributes.put("candidateSchemaVersion", Integer.toString(command.candidateSchemaVersion()));
            }
            // 只记录数量型低基数字段，避免把策略名或样本内容带入标签。
            attributes.put("retrievalStrategyCount", Integer.toString(retrievalCommand.strategies().size()));
            attributes.put("retrievalCaseCount", Integer.toString(retrievalCommand.cases().size()));
            attributes.put("metadataFieldDeltaCount",
                    Integer.toString(report.metadataQuality().fieldDeltas().size()));
            observationPort.recordEvent(new ObservationEvent(EVENT_VERSION_COMPARE, null, attributes));
        } catch (RuntimeException ignored) {
            // 组合对比接口属于管理查询，不应被观测失败反向打断。
        }
    }
}
