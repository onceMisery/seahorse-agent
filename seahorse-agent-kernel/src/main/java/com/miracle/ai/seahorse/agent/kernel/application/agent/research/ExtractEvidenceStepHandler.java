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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EXTRACT_EVIDENCE 步骤：从抓取的网页内容中提取关键证据。
 *
 * <p>调用 LLM 对每个来源的内容进行摘要和证据提取，生成 EvidenceItem 列表。
 */
public class ExtractEvidenceStepHandler implements ResearchStepHandler {

    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是一个证据提取助手。根据用户的研究问题和提供的网页内容，
            提取 1-3 条关键证据。每条证据格式为：
            CLAIM: [核心论点]
            QUOTE: [原文引用片段，不超过100字]
            SUMMARY: [一句话总结]

            如果内容与研究问题无关，回复 IRRELEVANT。
            """;

    private static final int MAX_CONTENT_CHARS = 3000;

    private final ChatModelPort chatModel;

    public ExtractEvidenceStepHandler(ChatModelPort chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.EXTRACT_EVIDENCE;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        AtomicInteger citationIndex = new AtomicInteger(1);

        for (WebSource source : context.sources()) {
            String content = context.getFetchedContent(source.sourceId());
            if (content == null || content.isBlank()) continue;

            String truncated = content.length() > MAX_CONTENT_CHARS
                    ? content.substring(0, MAX_CONTENT_CHARS) : content;

            String response = chatModel.chat(null, List.of(
                    ChatMessage.system(EXTRACT_SYSTEM_PROMPT),
                    ChatMessage.user("研究问题：" + context.query() + "\n\n网页内容（来源：" + source.url() + "）：\n" + truncated)));

            if (response == null || response.isBlank() || response.contains("IRRELEVANT")) continue;

            parseEvidence(response, source.sourceId(), citationIndex, context);
        }
    }

    private void parseEvidence(String response, String sourceId, AtomicInteger citationIndex, ResearchStepContext context) {
        String[] blocks = response.split("(?=CLAIM:)");
        for (String block : blocks) {
            String claim = extractField(block, "CLAIM:");
            String quote = extractField(block, "QUOTE:");
            String summary = extractField(block, "SUMMARY:");
            if (claim == null || claim.isBlank()) continue;

            context.addEvidence(new EvidenceItem(
                    SnowflakeIds.nextIdString(),
                    sourceId,
                    claim,
                    quote,
                    summary,
                    0.7,
                    citationIndex.getAndIncrement()));
        }
    }

    private String extractField(String block, String prefix) {
        int start = block.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = block.indexOf('\n', start);
        if (end < 0) end = block.length();
        return block.substring(start, end).trim();
    }
}
