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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationType;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Slice 3 续 cut 9：将 ingestion 请求拼装为 outbound {@link MemoryOperation} 的构造器。
 *
 * <p>原 facade 私有方法 {@code buildOperation} / {@code inferOperationType} /
 * {@code inferTargetKind} / {@code inferTargetKey} / {@code requestMap} 合并到本 service。
 * 对外只暴露 {@link #build(String, String, MemoryIngestionCommand, MemoryWriteRequest, String)}
 * 单一入口。
 *
 * <p>决策优先级（保持与 facade 行为完全一致）：
 * <ol>
 *     <li>显式 review apply directive → 直接使用 directive.requestedAction / targetKind / targetKey。</li>
 *     <li>{@link OccupationCorrection#extract(String)} 命中 → UPDATE PROFILE_SLOT。</li>
 *     <li>否则 sanitizer + preFilter + semanticClassifier 链式判定 ADD / UPDATE / REVIEW / IGNORE。</li>
 * </ol>
 *
 * <p>三个 helper（sanitizer / preFilter / semanticClassifier）保持纯 kernel application 类型，
 * 不引入任何 outbound port 依赖。
 */
public final class MemoryOperationBuilder {

    private static final String TARGET_KIND_PROFILE_SLOT = "PROFILE_SLOT";
    private static final String TARGET_KEY_IDENTITY_OCCUPATION = "identity.occupation";
    private static final String TARGET_KIND_SHORT_TERM_MEMORY = "SHORT_TERM_MEMORY";
    private static final String TARGET_KIND_NONE = "NONE";

    private final MemorySanitizer memorySanitizer;
    private final MemoryPreFilter memoryPreFilter;
    private final MemorySemanticClassifier memorySemanticClassifier;

    public MemoryOperationBuilder(MemorySanitizer memorySanitizer,
                                  MemoryPreFilter memoryPreFilter,
                                  MemorySemanticClassifier memorySemanticClassifier) {
        this.memorySanitizer = Objects.requireNonNull(memorySanitizer, "memorySanitizer must not be null");
        this.memoryPreFilter = Objects.requireNonNull(memoryPreFilter, "memoryPreFilter must not be null");
        this.memorySemanticClassifier = Objects.requireNonNull(memorySemanticClassifier,
                "memorySemanticClassifier must not be null");
    }

    /**
     * 拼装并返回 ingestion 对应的 {@link MemoryOperation}（policyVersion 取
     * {@link MemoryValueAssessor#POLICY_VERSION}，时间戳 {@link Instant#now()}）。
     */
    public MemoryOperation build(String operationId,
                                 String tenantId,
                                 MemoryIngestionCommand command,
                                 MemoryWriteRequest request,
                                 String content) {
        MemoryReviewApplyDirective directive = command == null ? null : command.reviewApplyDirective();
        MemoryOperationType operationType = inferOperationType(directive, content);
        return new MemoryOperation(
                operationId,
                request.userId(),
                tenantId,
                operationType,
                inferTargetKind(operationType, directive, content),
                inferTargetKey(operationType, directive, content),
                requestMap(command, request, content),
                MemoryValueAssessor.POLICY_VERSION,
                Instant.now());
    }

    private MemoryOperationType inferOperationType(MemoryReviewApplyDirective directive, String content) {
        if (directive != null) {
            return switch (directive.requestedAction()) {
                case ADD -> MemoryOperationType.ADD;
                case UPDATE -> MemoryOperationType.UPDATE;
                case DELETE -> MemoryOperationType.DELETE;
                case REVIEW -> MemoryOperationType.REVIEW;
                case IGNORE -> MemoryOperationType.IGNORE;
            };
        }
        if (OccupationCorrection.extract(content) != null) {
            return MemoryOperationType.UPDATE;
        }
        SanitizedMemoryInput sanitized = memorySanitizer.sanitize(content);
        if (sanitized.rejected()) {
            return MemoryOperationType.IGNORE;
        }
        MemoryPreFilterResult preFilterResult = memoryPreFilter.filter(sanitized.content());
        if (!preFilterResult.accepted()) {
            return MemoryOperationType.IGNORE;
        }
        MemoryClassificationResult classification = memorySemanticClassifier.classify(sanitized.content());
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            return MemoryOperationType.UPDATE;
        }
        if (classification.action() == MemoryIngestionAction.ADD) {
            return MemoryOperationType.ADD;
        }
        if (classification.action() == MemoryIngestionAction.REVIEW) {
            return MemoryOperationType.REVIEW;
        }
        return MemoryOperationType.IGNORE;
    }

    private static String inferTargetKind(MemoryOperationType operationType,
                                          MemoryReviewApplyDirective directive,
                                          String content) {
        if (directive != null && !isBlank(directive.targetKind())) {
            return directive.targetKind();
        }
        if (operationType == MemoryOperationType.UPDATE || OccupationCorrection.extract(content) != null) {
            return TARGET_KIND_PROFILE_SLOT;
        }
        if (operationType == MemoryOperationType.ADD) {
            return TARGET_KIND_SHORT_TERM_MEMORY;
        }
        return TARGET_KIND_NONE;
    }

    private static String inferTargetKey(MemoryOperationType operationType,
                                         MemoryReviewApplyDirective directive,
                                         String content) {
        if (directive != null && !isBlank(directive.targetKey())) {
            return directive.targetKey();
        }
        if (operationType == MemoryOperationType.UPDATE || OccupationCorrection.extract(content) != null) {
            return TARGET_KEY_IDENTITY_OCCUPATION;
        }
        return "";
    }

    private static Map<String, Object> requestMap(MemoryIngestionCommand command,
                                                  MemoryWriteRequest request,
                                                  String content) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source", command == null ? "" : command.source());
        values.put("conversationId", Objects.requireNonNullElse(request.conversationId(), ""));
        values.put("messageId", Objects.requireNonNullElse(request.messageId(), ""));
        values.put("role", request.message() == null ? "" : request.message().getRole().name());
        values.put("content", Objects.requireNonNullElse(content, ""));
        return values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
