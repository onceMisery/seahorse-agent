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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SYNTHESIZE 步骤：综合所有证据，生成研究结论大纲。
 */
public class SynthesizeStepHandler implements ResearchStepHandler {

    private static final String SYNTHESIZE_SYSTEM_PROMPT = """
            你是一个研究综合分析助手。根据提供的证据列表，生成一份结构化的研究结论大纲。
            大纲格式：
            1. [结论1]
            2. [结论2]
            ...
            每个结论后标注支撑证据编号，如 [1][2]。
            """;

    private final ChatModelPort chatModel;

    public SynthesizeStepHandler(ChatModelPort chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.SYNTHESIZE;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        if (context.evidence().isEmpty()) return;

        String evidenceSummary = context.evidence().stream()
                .map(e -> "[" + e.citationIndex() + "] " + e.claim() + " — " + Objects.requireNonNullElse(e.summary(), ""))
                .collect(Collectors.joining("\n"));

        String response = chatModel.chat(null, List.of(
                ChatMessage.system(SYNTHESIZE_SYSTEM_PROMPT),
                ChatMessage.user("研究问题：" + context.query() + "\n\n证据列表：\n" + evidenceSummary)));

        // 综合结果暂存到 context，供 WRITE_REPORT 使用
        if (response != null && !response.isBlank()) {
            context.setReportContent(response);
        }
    }
}
