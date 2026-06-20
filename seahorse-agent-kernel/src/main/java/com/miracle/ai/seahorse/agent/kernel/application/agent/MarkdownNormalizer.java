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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalizes final markdown output before it is streamed back to clients.
 */
public class MarkdownNormalizer {

    public String normalizeFinalMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String normalizedLineEndings = content.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalizedLineEndings.length() + 64);
        int offset = 0;
        while (offset < normalizedLineEndings.length()) {
            int artifactStart = indexOfArtifactStart(normalizedLineEndings, offset);
            if (artifactStart < 0) {
                result.append(normalizeMarkdownWithoutArtifacts(normalizedLineEndings.substring(offset)));
                break;
            }

            result.append(normalizeMarkdownWithoutArtifacts(normalizedLineEndings.substring(offset, artifactStart)));
            int artifactEnd = indexOfArtifactEnd(normalizedLineEndings, artifactStart);
            if (artifactEnd < 0) {
                result.append(normalizedLineEndings.substring(artifactStart));
                break;
            }
            result.append(normalizedLineEndings, artifactStart, artifactEnd);
            offset = artifactEnd;
        }
        return result.toString().trim();
    }

    private int indexOfArtifactStart(String content, int fromIndex) {
        return content.toLowerCase(Locale.ROOT).indexOf("<artifact", fromIndex);
    }

    private int indexOfArtifactEnd(String content, int artifactStart) {
        int closeTag = content.toLowerCase(Locale.ROOT).indexOf("</artifact>", artifactStart);
        if (closeTag < 0) {
            return -1;
        }
        return closeTag + "</artifact>".length();
    }

    private String normalizeMarkdownWithoutArtifacts(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = normalizeMermaidFenceOpenings(content);
        StringBuilder builder = new StringBuilder(normalized.length() + 64);
        int offset = 0;
        int openingFence = normalized.indexOf("```", offset);
        while (openingFence >= 0) {
            appendNormalizedMarkdownText(builder, normalized.substring(offset, openingFence));

            int closingFence = findClosingFence(normalized, openingFence + 3);
            if (closingFence < 0) {
                appendNormalizedCodeBlock(builder, normalized.substring(openingFence));
                offset = normalized.length();
                break;
            }

            appendNormalizedCodeBlock(builder, normalized.substring(openingFence, closingFence + 3));
            offset = closingFence + 3;
            openingFence = normalized.indexOf("```", offset);
        }
        appendNormalizedMarkdownText(builder, normalized.substring(offset));
        return builder.toString().trim();
    }

    private String normalizeMermaidFenceOpenings(String content) {
        String normalized = content;
        normalized = normalized.replaceAll("```\\s*```\\s*(?i:mermaid)\\b", "```\n\n```mermaid");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:flowchart)\\b", "```mermaid\nflowchart");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:graph)\\b", "```mermaid\ngraph");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:sequenceDiagram)\\b", "```mermaid\nsequenceDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:classDiagram)\\b", "```mermaid\nclassDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:stateDiagram-v2)\\b", "```mermaid\nstateDiagram-v2");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:erDiagram)\\b", "```mermaid\nerDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:gantt)\\b", "```mermaid\ngantt");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:journey)\\b", "```mermaid\njourney");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:pie)\\b", "```mermaid\npie");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:mindmap)\\b", "```mermaid\nmindmap");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:timeline)\\b", "```mermaid\ntimeline");
        return normalized;
    }

    private void appendNormalizedMarkdownText(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String normalized = normalizeMarkdownTextSegment(content).trim();
        if (normalized.isEmpty()) {
            return;
        }
        ensureBlankLineBefore(builder);
        builder.append(normalized);
    }

    private String normalizeMarkdownTextSegment(String content) {
        String normalized = content;
        normalized = normalized.replaceAll("([^\\n])---(?=#{1,6}\\s*\\S)", "$1\n\n---\n\n");
        normalized = normalized.replaceAll("^---(?=#{1,6}\\s*\\S)", "---\n\n");
        normalized = normalized.replaceAll("([^\\n])---(?=\\*)", "$1\n\n---\n\n");
        normalized = normalized.replaceAll("([^#\\n])(?=#{1,6}\\S)", "$1\n\n");
        normalized = normalized.replaceAll("(?m)^(#{1,6})(\\S)", "$1 $2");
        normalized = separateGeneratedReportHeadings(normalized);
        normalized = normalized.replaceAll("(?m)^(#{1,6}\\s+[^\\n|#]+)\\|", "$1\n\n|");
        normalized = normalized.replaceAll("(?m)^(#{1,6}\\s+[^\\n`#]+)```", "$1\n\n```");
        normalized = normalized.replaceAll("(?<!\\n)(\\d+\\.\\s+\\*\\*)", "\n$1");
        normalized = separateGeneratedReportListItems(normalized);
        normalized = splitCompressedListItemsInLines(normalized);
        normalized = normalized.replaceAll("(\\|[^\\n|]+\\|)(?=\\|)", "$1\n");
        normalized = normalized.replaceAll("(?<!\\n)(- \\*\\*)", "\n$1");
        normalized = normalized.replaceAll("(\\]\\([^\\n)]+\\))(?=\\*)", "$1\n\n");
        normalized = normalized.replaceAll("([^\\n])```", "$1\n```");
        normalized = normalized.replaceAll("```(?=---)", "```\n");
        normalized = normalized.replaceAll("```(?=#{1,6}\\s*\\S)", "```\n\n");
        normalized = normalized.replaceAll("(?m)([^\\n])\\n(#{1,6}\\s+)", "$1\n\n$2");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized;
    }

    private void appendNormalizedCodeBlock(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ensureBlankLineBefore(builder);
        builder.append(normalizeCodeBlockSegment(content));
    }

    private String normalizeCodeBlockSegment(String content) {
        String normalized = ensureCodeBlockClosingFenceOnOwnLine(content);
        if (isMermaidCodeBlock(normalized)) {
            return normalizeMermaidCodeBlock(normalized);
        }
        return normalized;
    }

    private String ensureCodeBlockClosingFenceOnOwnLine(String content) {
        int closingFence = content.lastIndexOf("```");
        if (closingFence > 0 && content.charAt(closingFence - 1) != '\n') {
            return content.substring(0, closingFence) + "\n" + content.substring(closingFence);
        }
        return content;
    }

    private boolean isMermaidCodeBlock(String content) {
        if (content.length() < "```mermaid".length()
                || !content.regionMatches(true, 0, "```mermaid", 0, "```mermaid".length())) {
            return false;
        }
        if (content.length() == "```mermaid".length()) {
            return true;
        }
        char next = content.charAt("```mermaid".length());
        return Character.isWhitespace(next);
    }

    private String normalizeMermaidCodeBlock(String content) {
        int bodyStart = content.indexOf('\n');
        if (bodyStart < 0) {
            return content;
        }
        String openingFence = content.substring(0, bodyStart + 1);
        String bodyWithClosingFence = content.substring(bodyStart + 1);
        int closingFence = bodyWithClosingFence.lastIndexOf("```");
        String body = closingFence >= 0
                ? bodyWithClosingFence.substring(0, closingFence)
                : bodyWithClosingFence;
        String closing = closingFence >= 0
                ? bodyWithClosingFence.substring(closingFence)
                : "";

        String normalizedBody = normalizeMermaidBody(body).stripTrailing();
        if (normalizedBody.isEmpty()) {
            return openingFence + closing;
        }
        return openingFence + normalizedBody + "\n" + closing;
    }

    private String normalizeMermaidBody(String body) {
        String[] lines = body.split("\n", -1);
        String diagramType = "";
        List<String> normalizedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (startsWithIgnoreCase(trimmed, "flowchart ") || startsWithIgnoreCase(trimmed, "graph ")) {
                diagramType = "flowchart";
                normalizedLines.addAll(splitFlowchartMermaidLine(trimmed));
            } else if (startsWithIgnoreCase(trimmed, "sequenceDiagram")) {
                diagramType = "sequence";
                normalizedLines.addAll(splitSequenceMermaidLine(trimmed));
            } else if ("flowchart".equals(diagramType)) {
                normalizedLines.addAll(splitMermaidStatements(splitCompressedFlowchartStatements(trimmed)));
            } else if ("sequence".equals(diagramType)) {
                normalizedLines.addAll(splitMermaidStatements(splitCompressedSequenceStatements(trimmed)));
            } else {
                normalizedLines.add(trimmed);
            }
        }
        return String.join("\n", normalizedLines);
    }

    private List<String> splitFlowchartMermaidLine(String line) {
        String[] parts = line.split("\\s+", 3);
        if (parts.length <= 2) {
            return List.of(line);
        }
        List<String> lines = new ArrayList<>();
        lines.add(parts[0] + " " + parts[1]);
        lines.addAll(splitMermaidStatements(splitCompressedFlowchartStatements(parts[2])));
        return lines;
    }

    private List<String> splitSequenceMermaidLine(String line) {
        String keyword = "sequenceDiagram";
        if (line.length() == keyword.length()) {
            return List.of(line);
        }
        List<String> lines = new ArrayList<>();
        lines.add(keyword);
        lines.addAll(splitMermaidStatements(splitCompressedSequenceStatements(line.substring(keyword.length()).trim())));
        return lines;
    }

    private String splitCompressedFlowchartStatements(String line) {
        String normalized = line.replaceAll(
                "\\s+(?=(?:style|classDef|class|linkStyle|click|subgraph|direction|end)\\b)",
                "\n");
        normalized = normalized.replaceAll(
                "\\s+(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:\\[[^\\]]*]|\\([^)]*\\)|\\{[^}]*}|>[^\\n<]*]|\\(\\([^)]*\\)\\))?\\s*(?:<-->|<-.->|<==>|-->|---|==>|-.->|--[ox]|[ox]--|~~~))",
                "\n");
        return splitStandaloneFlowchartNodes(normalized);
    }

    private String splitCompressedSequenceStatements(String line) {
        String normalized = line.replaceAll(
                "\\s+(?=(?:participant|actor|create|activate|deactivate|destroy|loop|alt|else|opt|par|and|critical|option|break|rect|end|box)\\b|Note\\s+(?:over|left|right)\\b)",
                "\n");
        return normalized.replaceAll(
                "\\s+(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:-->>|->>|-->|->|--x|-x|--\\)|-\\))\\s*[A-Za-z_][A-Za-z0-9_]*)",
                "\n");
    }

    private List<String> splitMermaidStatements(String content) {
        String[] lines = content.split("\n", -1);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String splitStandaloneFlowchartNodes(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder builder = new StringBuilder(content.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(splitStandaloneFlowchartNodesInLine(lines[i]));
        }
        return builder.toString();
    }

    private String splitStandaloneFlowchartNodesInLine(String line) {
        if (line.isBlank() || line.startsWith("style ") || line.startsWith("classDef ")
                || line.startsWith("class ") || line.startsWith("linkStyle ") || line.startsWith("click ")) {
            return line;
        }
        StringBuilder builder = new StringBuilder(line.length() + 16);
        int groupingDepth = 0;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (isOpeningMermaidGrouping(current)) {
                groupingDepth++;
            } else if (isClosingMermaidGrouping(current) && groupingDepth > 0) {
                groupingDepth--;
            }
            if (groupingDepth == 0 && Character.isWhitespace(current)
                    && shouldBreakBeforeStandaloneFlowchartNode(line, i)) {
                builder.append('\n');
                while (i + 1 < line.length() && Character.isWhitespace(line.charAt(i + 1))) {
                    i++;
                }
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private boolean shouldBreakBeforeStandaloneFlowchartNode(String line, int whitespaceIndex) {
        int candidateIndex = whitespaceIndex + 1;
        while (candidateIndex < line.length() && Character.isWhitespace(line.charAt(candidateIndex))) {
            candidateIndex++;
        }
        if (!isFlowchartNodeDefinitionAt(line, candidateIndex)) {
            return false;
        }
        String before = line.substring(0, whitespaceIndex).trim();
        int operatorIndex = lastFlowchartEdgeOperatorIndex(before);
        if (operatorIndex < 0) {
            return true;
        }
        return hasFlowchartTargetAfterOperator(before.substring(operatorIndex));
    }

    private boolean isFlowchartNodeDefinitionAt(String line, int index) {
        if (index >= line.length() || !isMermaidIdentifierStart(line.charAt(index))) {
            return false;
        }
        int cursor = index + 1;
        while (cursor < line.length() && isMermaidIdentifierPart(line.charAt(cursor))) {
            cursor++;
        }
        return cursor < line.length() && isOpeningMermaidGrouping(line.charAt(cursor));
    }

    private int lastFlowchartEdgeOperatorIndex(String value) {
        int result = -1;
        for (String operator : flowchartEdgeOperators()) {
            result = Math.max(result, value.lastIndexOf(operator));
        }
        return result;
    }

    private boolean hasFlowchartTargetAfterOperator(String value) {
        int cursor = 0;
        while (cursor < value.length() && !Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        String afterOperator = value.substring(cursor).trim();
        if (afterOperator.startsWith("|")) {
            int endLabel = afterOperator.indexOf('|', 1);
            if (endLabel >= 0) {
                afterOperator = afterOperator.substring(endLabel + 1).trim();
            }
        }
        return !afterOperator.isEmpty();
    }

    private List<String> flowchartEdgeOperators() {
        return List.of("<-.->", "<-->", "<==>", "-.->", "-->", "---", "==>", "--x", "--o", "x--", "o--", "~~~");
    }

    private boolean isMermaidIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isMermaidIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private boolean isOpeningMermaidGrouping(char value) {
        return value == '[' || value == '(' || value == '{';
    }

    private boolean isClosingMermaidGrouping(char value) {
        return value == ']' || value == ')' || value == '}';
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private int findClosingFence(String content, int offset) {
        return content.indexOf("```", offset);
    }

    private void ensureBlankLineBefore(StringBuilder builder) {
        if (builder.isEmpty()) {
            return;
        }
        int length = builder.length();
        if (builder.charAt(length - 1) != '\n') {
            builder.append("\n\n");
            return;
        }
        if (length < 2 || builder.charAt(length - 2) != '\n') {
            builder.append('\n');
        }
    }

    private String separateGeneratedReportHeadings(String content) {
        String normalized = content;
        for (String marker : generatedReportHeadingMarkers()) {
            normalized = ensureBreakAfterMarker(normalized, marker);
        }
        return normalized;
    }

    private String separateGeneratedReportListItems(String content) {
        String normalized = content;
        for (String marker : generatedReportListMarkers()) {
            normalized = normalized.replace(marker + "-", marker + "\n\n- ");
        }
        normalized = normalized.replaceAll("([：:]\\s*)-\\s*(?=[\\p{IsHan}A-Za-z])", "$1\n\n- ");
        normalized = normalized.replaceAll("(?m)^-(?!\\s)(?=[\\p{IsHan}])", "- ");
        return normalized;
    }

    private String splitCompressedListItemsInLines(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder builder = new StringBuilder(content.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(splitCompressedListItemsInLine(lines[i]));
        }
        return builder.toString();
    }

    private String splitCompressedListItemsInLine(String line) {
        if (!line.startsWith("- ")) {
            return line;
        }
        StringBuilder builder = new StringBuilder(line.length() + 32);
        builder.append("- ");
        for (int i = 2; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '-' && i + 1 < line.length() && isListItemStart(line.charAt(i + 1))) {
                builder.append('\n').append("- ");
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private boolean isListItemStart(char value) {
        return isCjk(value) || Character.isUpperCase(value) || Character.isDigit(value);
    }

    private boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return Character.UnicodeScript.HAN.equals(script);
    }

    private List<String> generatedReportListMarkers() {
        return List.of(
                "### 6.2高性能",
                "### 1.事件驱动模型",
                "### 2.数据结构实现",
                "### 3.持久化机制",
                "### 1.数据结构支持",
                "### 2.高可用架构",
                "### 3.模块扩展",
                "### 4. AI集成",
                "### 项目介绍视觉图",
                "### 长文 Markdown草稿",
                "### 演示文稿结构",
                "### 前端设计版式",
                "### 8.2演示文稿结构（ppt_generation）已生成10页演示文稿结构，包含：",
                "### 8.3前端设计版式草案（frontend_design）已生成 HTML/CSS版式草案，包含：");
    }

    private String ensureBreakAfterMarker(String content, String marker) {
        StringBuilder builder = new StringBuilder(content.length() + 64);
        int offset = 0;
        int index = content.indexOf(marker, offset);
        while (index >= 0) {
            int afterMarker = index + marker.length();
            builder.append(content, offset, afterMarker);
            if (afterMarker < content.length() && content.charAt(afterMarker) != '\n') {
                builder.append("\n\n");
            }
            offset = afterMarker;
            index = content.indexOf(marker, offset);
        }
        builder.append(content, offset, content.length());
        return builder.toString();
    }

    private List<String> generatedReportHeadingMarkers() {
        return List.of(
                "# Redis project intro",
                "## 一、项目概览",
                "## 二、架构设计",
                "## 三、架构图",
                "## 四、流程图",
                "## 五、核心逻辑",
                "## 六、重点特性",
                "## 七、关键文件证据表",
                "## 八、生成图片引用",
                "## 九、生成稿件摘要",
                "## 九、生成稿件和版式产物摘要",
                "## 十、总结",
                "## 八、生成稿件摘要",
                "## 九、总结",
                "### 关键用途",
                "### 核心定位",
                "### 关键优势",
                "### 核心架构组件",
                "### 架构层次说明",
                "### 架构要点",
                "### 命令执行流程",
                "### 持久化流程",
                "### 1.事件驱动模型",
                "### 2.数据结构实现",
                "### 3.持久化机制",
                "### 1.数据结构支持",
                "### 2.高可用架构",
                "### 3.模块扩展",
                "### 4. AI集成",
                "### 项目介绍视觉图",
                "### 长文 Markdown草稿",
                "### 演示文稿结构",
                "### 前端设计版式",
                "### 5.1单线程事件循环",
                "### 5.2数据结构实现逻辑",
                "### 5.3持久化逻辑",
                "### 6.1丰富的数据结构",
                "### 6.2高性能",
                "### 6.3高可用与扩展性",
                "### 6.4扩展能力",
                "### 6.5 AI与搜索",
                "### 新闻稿摘要",
                "### 演示文稿摘要",
                "### 前端设计摘要");
    }
}
