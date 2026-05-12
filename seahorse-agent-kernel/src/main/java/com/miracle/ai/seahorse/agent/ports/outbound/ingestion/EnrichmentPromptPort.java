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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import java.util.Locale;

/**
 * Chunk 级富化提示词端口。
 */
public interface EnrichmentPromptPort {

    String systemPrompt(String taskType);

    static EnrichmentPromptPort defaults() {
        return taskType -> switch (normalize(taskType)) {
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
