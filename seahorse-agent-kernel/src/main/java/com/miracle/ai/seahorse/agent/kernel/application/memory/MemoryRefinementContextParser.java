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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slice 3 续 cut 3：将 sanitized content 切分为 reference/target zones + source spans 的
 * 解析逻辑从 {@code DefaultMemoryEnginePort} 拆出。
 *
 * <p>职责范围：
 * <ul>
 *     <li>识别 {@code [turns]} / {@code [source_spans]} 区块。</li>
 *     <li>按 {@code turn_N:} 行头切分 turn block 并匹配对应 source span。</li>
 *     <li>按 options.refinerTargetZoneTurnCount 划分 reference zone 与 target zone。</li>
 *     <li>从 target zone 的 source spans 提取 user message id 列表。</li>
 * </ul>
 *
 * <p>无任何 outbound port 依赖，纯字符串解析；和 facade 完全保持行为一致：原 11 个私有方法
 * （contextBlockTurns / splitContextTurns / splitContextSourceSpans / contextBlockTurn /
 * contextTurnIndex / parsePositiveInt / joinContextTurnBlocks / sourceMessageIdsFromSpans /
 * sourceMessageIdFromSpan / refinementContextZones）合并为单一入口 {@link #parse(String)}。
 *
 * <p>面向外部使用的输出结构 {@link Zones} 替代原 facade 的 {@code MemoryRefinementContextZones}
 * record，accessor 名称保持 {@code referenceZone / targetZone / targetSourceMessageIds} 不变。
 */
public final class MemoryRefinementContextParser {

    /**
     * 解析后输出：reference zone（早期上下文，参考用）、target zone（最近 N 轮，refiner 重点处理）、
     * target zone 范围内涉及的 user message id 列表。
     */
    public record Zones(String referenceZone,
                        String targetZone,
                        List<String> targetSourceMessageIds) {

        public Zones(String referenceZone, String targetZone) {
            this(referenceZone, targetZone, List.of());
        }

        public Zones {
            referenceZone = Objects.requireNonNullElse(referenceZone, "");
            targetZone = Objects.requireNonNullElse(targetZone, "");
            targetSourceMessageIds = targetSourceMessageIds == null
                    ? List.of()
                    : List.copyOf(targetSourceMessageIds.stream()
                    .filter(messageId -> messageId != null && !messageId.isBlank())
                    .distinct()
                    .toList());
        }
    }

    private record ContextBlockTurn(int turnIndex, String turnBlock, String sourceSpan) {

        private ContextBlockTurn {
            turnBlock = Objects.requireNonNullElse(turnBlock, "");
            sourceSpan = Objects.requireNonNullElse(sourceSpan, "");
        }
    }

    private static final Pattern CONTEXT_TURN_HEADER_PATTERN = Pattern.compile("\\bturn_\\d+:");
    private static final Pattern CONTEXT_TURN_INDEX_PATTERN = Pattern.compile("\\bturn_(\\d+):");
    private static final Pattern CONTEXT_SOURCE_SPAN_PATTERN = Pattern.compile(
            "\\bspan_(\\d+):\\s*(.*?)(?=\\s+span_\\d+:\\s*|\\s*$)", Pattern.DOTALL);

    private final int targetZoneTurnCount;

    public MemoryRefinementContextParser(int targetZoneTurnCount) {
        this.targetZoneTurnCount = targetZoneTurnCount;
    }

    /**
     * 解析 sanitized content，得到 reference/target zones 与 target zone source message ids。
     *
     * <ul>
     *     <li>空内容 → 全空 zones。</li>
     *     <li>无 {@code [turns]} 块 → reference 空、target = sanitizedContent、ids 空。</li>
     *     <li>正常 → 按 {@code targetZoneTurnCount} 切分最后 N 个 turn 为 target zone。</li>
     * </ul>
     */
    public Zones parse(String sanitizedContent) {
        if (isBlank(sanitizedContent)) {
            return new Zones("", "");
        }
        List<ContextBlockTurn> turns = contextBlockTurns(sanitizedContent);
        if (turns.isEmpty()) {
            return new Zones("", sanitizedContent);
        }
        int targetStart = Math.max(0, turns.size() - targetZoneTurnCount);
        List<ContextBlockTurn> targetTurns = turns.subList(targetStart, turns.size());
        return new Zones(
                joinContextTurnBlocks(turns.subList(0, targetStart)),
                joinContextTurnBlocks(targetTurns),
                sourceMessageIdsFromSpans(targetTurns));
    }

    private static List<ContextBlockTurn> contextBlockTurns(String content) {
        String normalized = Objects.requireNonNullElse(content, "").replace("\r\n", "\n").replace('\r', '\n');
        int turnsStart = normalized.indexOf("[turns]");
        if (turnsStart < 0) {
            return List.of();
        }
        int bodyStart = turnsStart + "[turns]".length();
        int sourceSpansStart = normalized.indexOf("[source_spans]", bodyStart);
        String turnsBody = sourceSpansStart > bodyStart
                ? normalized.substring(bodyStart, sourceSpansStart)
                : normalized.substring(bodyStart);
        Map<Integer, String> sourceSpans = sourceSpansStart > bodyStart
                ? splitContextSourceSpans(normalized.substring(sourceSpansStart + "[source_spans]".length()))
                : Map.of();
        return splitContextTurns(turnsBody, sourceSpans);
    }

    private static List<ContextBlockTurn> splitContextTurns(String turnsBody, Map<Integer, String> sourceSpans) {
        List<ContextBlockTurn> turns = new ArrayList<>();
        Matcher matcher = CONTEXT_TURN_HEADER_PATTERN.matcher(Objects.requireNonNullElse(turnsBody, ""));
        int currentStart = -1;
        while (matcher.find()) {
            if (currentStart >= 0) {
                String block = turnsBody.substring(currentStart, matcher.start()).trim();
                if (!block.isBlank()) {
                    turns.add(contextBlockTurn(block, sourceSpans));
                }
            }
            currentStart = matcher.start();
        }
        if (currentStart >= 0) {
            String block = turnsBody.substring(currentStart).trim();
            if (!block.isBlank()) {
                turns.add(contextBlockTurn(block, sourceSpans));
            }
        }
        return List.copyOf(turns);
    }

    private static Map<Integer, String> splitContextSourceSpans(String spansBody) {
        Map<Integer, String> spans = new LinkedHashMap<>();
        Matcher matcher = CONTEXT_SOURCE_SPAN_PATTERN.matcher(Objects.requireNonNullElse(spansBody, ""));
        while (matcher.find()) {
            int index = parsePositiveInt(matcher.group(1));
            if (index > 0) {
                spans.put(index, ("span_" + index + ": " + matcher.group(2).trim()).trim());
            }
        }
        return Map.copyOf(spans);
    }

    private static ContextBlockTurn contextBlockTurn(String block, Map<Integer, String> sourceSpans) {
        int index = contextTurnIndex(block);
        return new ContextBlockTurn(
                index,
                block,
                index <= 0 ? "" : Objects.requireNonNullElse(sourceSpans.get(index), ""));
    }

    private static int contextTurnIndex(String block) {
        Matcher matcher = CONTEXT_TURN_INDEX_PATTERN.matcher(Objects.requireNonNullElse(block, ""));
        if (!matcher.find()) {
            return 0;
        }
        return parsePositiveInt(matcher.group(1));
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String joinContextTurnBlocks(List<ContextBlockTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        String turnBlocks = String.join("\n\n", turns.stream()
                .map(ContextBlockTurn::turnBlock)
                .toList());
        List<String> sourceSpans = turns.stream()
                .map(ContextBlockTurn::sourceSpan)
                .filter(span -> !span.isBlank())
                .toList();
        if (sourceSpans.isEmpty()) {
            return turnBlocks;
        }
        return turnBlocks + "\n\n[source_spans]\n" + String.join("\n", sourceSpans);
    }

    private static List<String> sourceMessageIdsFromSpans(List<ContextBlockTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(ContextBlockTurn::sourceSpan)
                .map(MemoryRefinementContextParser::sourceMessageIdFromSpan)
                .filter(messageId -> !isBlank(messageId))
                .distinct()
                .toList();
    }

    private static String sourceMessageIdFromSpan(String sourceSpan) {
        if (isBlank(sourceSpan)) {
            return "";
        }
        String span = sourceSpan.trim();
        int separatorIndex = span.indexOf(':');
        String body = separatorIndex >= 0 ? span.substring(separatorIndex + 1) : span;
        int assistantSeparatorIndex = body.indexOf("->");
        String userMessageId = assistantSeparatorIndex >= 0 ? body.substring(0, assistantSeparatorIndex) : body;
        return userMessageId.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
