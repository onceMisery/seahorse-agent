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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 负责检索引擎相关的 observation 与 schema usage 记录。
 * <p>
 * 主检索引擎只保留编排、降级与后处理链职责，所有统计副作用都集中在这里。
 */
final class KernelRetrievalObservationSupport {

    private final FeatureActivationContext activationContext;
    private final ObservationPort observationPort;
    private final MetadataSchemaUsageReportRepositoryPort schemaUsageRepositoryPort;
    private final String eventMetadataFilterCompiled;
    private final String eventMetadataFilterRejected;
    private final String eventChannelCompleted;
    private final String eventMetadataGuard;
    private final String eventRetrievalEmpty;
    private final String metadataGuardProcessorName;

    KernelRetrievalObservationSupport(FeatureActivationContext activationContext,
                                      ObservationPort observationPort,
                                      MetadataSchemaUsageReportRepositoryPort schemaUsageRepositoryPort,
                                      String eventMetadataFilterCompiled,
                                      String eventMetadataFilterRejected,
                                      String eventChannelCompleted,
                                      String eventMetadataGuard,
                                      String eventRetrievalEmpty,
                                      String metadataGuardProcessorName) {
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.observationPort = observationPort;
        this.schemaUsageRepositoryPort = Objects.requireNonNullElseGet(schemaUsageRepositoryPort,
                MetadataSchemaUsageReportRepositoryPort::empty);
        this.eventMetadataFilterCompiled = Objects.requireNonNullElse(eventMetadataFilterCompiled, "");
        this.eventMetadataFilterRejected = Objects.requireNonNullElse(eventMetadataFilterRejected, "");
        this.eventChannelCompleted = Objects.requireNonNullElse(eventChannelCompleted, "");
        this.eventMetadataGuard = Objects.requireNonNullElse(eventMetadataGuard, "");
        this.eventRetrievalEmpty = Objects.requireNonNullElse(eventRetrievalEmpty, "");
        this.metadataGuardProcessorName = Objects.requireNonNullElse(metadataGuardProcessorName, "");
    }

    void recordChannelCompleted(SearchChannelFeature channel,
                                SearchContext context,
                                SearchChannelResult result,
                                Throwable ex,
                                long elapsedMs,
                                long timeoutMs,
                                boolean timedOut) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("channelName", channel == null ? "" : Objects.requireNonNullElse(channel.name(), ""));
            attributes.put("channelType",
                    channel == null || channel.channelType() == null ? "" : channel.channelType().name());
            attributes.put("status", timedOut ? "timeout" : (ex == null ? "success" : "failure"));
            attributes.put("success", Boolean.toString(ex == null));
            attributes.put("hitCount", Integer.toString(safeChunks(result).size()));
            attributes.put("elapsedMs", Long.toString(elapsedMs));
            attributes.put("timeoutMs", Long.toString(timeoutMs));
            attributes.put("timedOut", Boolean.toString(timedOut));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            observationPort.recordEvent(new ObservationEvent(eventChannelCompleted, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响主检索链路的降级与返回语义。
        }
    }

    void recordMetadataFilterRejected(String tenantId,
                                      String knowledgeBaseId,
                                      MetadataSchema schema,
                                      RetrievalFilter filter,
                                      RuntimeException ex) {
        if (filter == null || filter.metadataConditions().isEmpty()) {
            return;
        }
        try {
            List<String> fieldKeys = filter.metadataConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            schemaUsageRepositoryPort.recordRejected(
                    tenantId,
                    knowledgeBaseId,
                    schema == null ? null : schema.schemaVersion(),
                    fieldKeys,
                    metadataFilterRejectReason(ex));
            if (observationPort == null) {
                return;
            }
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", Objects.requireNonNullElse(tenantId, ""));
            attributes.put("knowledgeBaseId", Objects.requireNonNullElse(knowledgeBaseId, ""));
            attributes.put("schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()));
            attributes.put("fieldCount", Integer.toString(fieldKeys.size()));
            attributes.put("success", "false");
            attributes.put("reason", metadataFilterRejectReason(ex));
            attributes.put("exception", ex.getClass().getSimpleName());
            observationPort.recordEvent(new ObservationEvent(eventMetadataFilterRejected, null, attributes));
        } catch (RuntimeException ignored) {
            // usage 记录失败不能篡改 filter compiler 的拒绝语义。
        }
    }

    void recordMetadataFilterUsage(String tenantId,
                                   String knowledgeBaseId,
                                   MetadataSchema schema,
                                   CompiledMetadataFilter compiledFilter) {
        if (compiledFilter == null || compiledFilter.sourceFilter().metadataConditions().isEmpty()) {
            return;
        }
        try {
            List<String> fieldKeys = compiledFilter.sourceFilter().metadataConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            List<String> guardOnlyFieldKeys = compiledFilter.guardOnlyConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            schemaUsageRepositoryPort.recordCompiled(
                    tenantId,
                    knowledgeBaseId,
                    schema == null ? null : schema.schemaVersion(),
                    fieldKeys,
                    guardOnlyFieldKeys);
            if (observationPort == null) {
                return;
            }
            observationPort.recordEvent(new ObservationEvent(eventMetadataFilterCompiled, null, Map.of(
                    "tenantId", Objects.requireNonNullElse(tenantId, ""),
                    "knowledgeBaseId", Objects.requireNonNullElse(knowledgeBaseId, ""),
                    "schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()),
                    "fieldCount", Integer.toString(fieldKeys.size()),
                    "guardOnlyCount", Integer.toString(guardOnlyFieldKeys.size()),
                    "warningCount", Integer.toString(compiledFilter.warnings().size()))));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响已编译过滤条件继续参与检索。
        }
    }

    void recordMetadataGuardCompleted(SearchResultPostProcessorFeature processor,
                                      SearchContext context,
                                      int inputCount,
                                      int outputCount,
                                      long elapsedMs,
                                      Exception ex) {
        if (observationPort == null
                || !metadataGuardProcessorName.equals(Objects.requireNonNullElse(processor.name(), ""))) {
            return;
        }
        try {
            int filteredCount = Math.max(inputCount - outputCount, 0);
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("inputCount", Integer.toString(inputCount));
            attributes.put("outputCount", Integer.toString(outputCount));
            attributes.put("filteredCount", Integer.toString(filteredCount));
            attributes.put("reason", filteredCount > 0 ? "metadata_or_acl_filtered" : "none");
            attributes.put("success", Boolean.toString(ex == null));
            attributes.put("elapsedMs", Long.toString(elapsedMs));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            observationPort.recordEvent(new ObservationEvent(eventMetadataGuard, null, attributes));
        } catch (RuntimeException ignored) {
            // 后处理 guard 的观测失败不能影响检索结果返回。
        }
    }

    void recordRetrievalEmpty(SearchContext context,
                              String stage,
                              String reason,
                              int channelCount,
                              int candidateCount) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("stage", Objects.requireNonNullElse(stage, ""));
            attributes.put("reason", Objects.requireNonNullElse(reason, ""));
            attributes.put("channelCount", Integer.toString(channelCount));
            attributes.put("candidateCount", Integer.toString(candidateCount));
            attributes.put("topK", Integer.toString(context == null ? 0 : context.getTopK()));
            attributes.put("filterApplied", Boolean.toString(context != null && context.getFilter() != null));
            observationPort.recordEvent(new ObservationEvent(eventRetrievalEmpty, null, attributes));
        } catch (RuntimeException ignored) {
            // 空结果事件只用于观测，不得改变主流程返回值。
        }
    }

    private String tenantId(SearchContext context) {
        RetrievalFilter filter = context == null ? null : context.getFilter();
        if (filter != null && filter.system() != null && !filter.system().tenantId().isBlank()) {
            return filter.system().tenantId();
        }
        return Objects.requireNonNullElse(activationContext.tenantId(), "");
    }

    private String knowledgeBaseId(SearchContext context) {
        RetrievalFilter filter = context == null ? null : context.getFilter();
        if (filter == null || filter.system() == null || filter.system().knowledgeBaseIds().isEmpty()) {
            return "";
        }
        return Objects.requireNonNullElse(filter.system().knowledgeBaseIds().get(0), "");
    }

    private String metadataFilterRejectReason(RuntimeException ex) {
        String message = Objects.requireNonNullElse(ex.getMessage(), "");
        if (message.contains("not registered")) {
            return "UNREGISTERED_FIELD";
        }
        if (message.contains("not filterable")) {
            return "NOT_FILTERABLE";
        }
        if (message.contains("not allowed")) {
            return "OPERATOR_NOT_ALLOWED";
        }
        if (message.contains("exceeds limit")) {
            return "CONDITION_LIMIT_EXCEEDED";
        }
        return "INVALID_FILTER";
    }

    private List<RetrievedChunk> safeChunks(SearchChannelResult result) {
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.getChunks(), List.of());
    }
}
