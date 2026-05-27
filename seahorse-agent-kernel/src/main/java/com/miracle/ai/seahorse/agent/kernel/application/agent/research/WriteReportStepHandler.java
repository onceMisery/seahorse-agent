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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * WRITE_REPORT 步骤：基于综合分析结果撰写完整的 Markdown 研究报告。
 */
public class WriteReportStepHandler implements ResearchStepHandler {

    private static final String WRITE_SYSTEM_PROMPT = """
            你是一个研究报告撰写助手。根据提供的研究大纲、证据和来源信息，
            撰写一份完整的 Markdown 格式研究报告。要求：
            1. 包含标题、摘要、正文和参考来源
            2. 正文中使用 [1], [2] 等标注引用来源
            3. 末尾列出所有参考来源的标题和 URL
            4. 语言流畅，逻辑清晰
            """;

    private final ChatModelPort chatModel;

    public WriteReportStepHandler(ChatModelPort chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.WRITE_REPORT;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        String outline = Objects.requireNonNullElse(context.reportContent(), "");
        String evidenceText = context.evidence().stream()
                .map(e -> "[" + e.citationIndex() + "] " + e.claim())
                .collect(Collectors.joining("\n"));
        String sourcesText = context.sources().stream()
                .map(s -> "- " + s.title() + " (" + s.url() + ")")
                .collect(Collectors.joining("\n"));

        String prompt = "研究问题：" + context.query()
                + "\n\n研究大纲：\n" + outline
                + "\n\n证据：\n" + evidenceText
                + "\n\n来源：\n" + sourcesText;

        String report = chatModel.chat(null, List.of(
                ChatMessage.system(WRITE_SYSTEM_PROMPT),
                ChatMessage.user(prompt)));

        if (report != null && !report.isBlank()) {
            context.setReportContent(report);
        }
    }
}
