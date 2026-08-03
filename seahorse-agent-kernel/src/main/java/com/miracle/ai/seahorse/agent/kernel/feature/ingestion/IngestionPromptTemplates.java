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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import java.util.Locale;

/**
 * Package-private ingestion prompt template collaborator.
 *
 * <p>Owns the default document-enhancement and chunk-enrichment system prompts.
 * These are internal kernel templates with no external or replaceable boundary,
 * so they are not Ports; consumers call the static template methods directly.</p>
 */
final class IngestionPromptTemplates {

    private IngestionPromptTemplates() {
    }

    static String enhancementPrompt(String taskType) {
        return switch (normalize(taskType)) {
            case "context_enhance" -> """
                    你是文档整理专家。请对以下可能存在格式问题的文档内容进行整理：
                    1. 修复明显的格式错误（表格错位、段落混乱）
                    2. 保持原文核心信息完整
                    3. 保持专业术语准确性
                    4. 直接输出整理后的文本，不要添加任何解释
                    """;
            case "keywords" -> """
                    从文本中提取 5-15 个最重要的关键词/短语。
                    优先选择：专业术语、核心概念、重要实体名称。
                    输出格式：JSON 数组，如 ["关键词1", "关键词2"]
                    只输出 JSON，不要其他内容。
                    """;
            case "questions" -> """
                    根据文本内容生成 3-5 个有价值的问题，帮助读者理解核心内容。
                    输出格式：JSON 数组，如 ["问题1", "问题2"]
                    只输出 JSON，不要其他内容。
                    """;
            case "metadata" -> """
                    从文本中提取重要的结构化信息，整理为 JSON 对象。
                    字段尽量使用英文键名，值类型使用 string/number/array/object。
                    只输出 JSON，不要其他内容。
                    """;
            default -> "";
        };
    }

    static String enrichmentPrompt(String taskType) {
        return switch (normalize(taskType)) {
            case "keywords" -> """
                    从文本片段中提取 3-8 个关键词/短语。
                    输出格式：JSON 数组，如 ["关键词1", "关键词2"]
                    只输出 JSON，不要其他内容。
                    """;
            case "summary" -> """
                    请用 1-3 句话对文本片段进行摘要，保持关键信息完整。
                    直接输出摘要文本，不要添加标题或解释。
                    """;
            case "metadata" -> """
                    从文本片段中抽取可结构化的信息，输出 JSON 对象。
                    只输出 JSON，不要其他内容。
                    """;
            default -> "";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
