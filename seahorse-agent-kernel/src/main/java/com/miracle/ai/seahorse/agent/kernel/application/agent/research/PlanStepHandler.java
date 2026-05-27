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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.List;
import java.util.Objects;

/**
 * PLAN 步骤：调用 LLM 将用户查询拆解为多个搜索子问题。
 */
public class PlanStepHandler implements ResearchStepHandler {

    private static final String PLAN_SYSTEM_PROMPT = """
            你是一个研究规划助手。根据用户的研究问题，生成 3-5 个搜索查询词，
            每行一个查询词，不要编号，不要解释。查询词应覆盖问题的不同角度。
            """;

    private final ChatModelPort chatModel;

    public PlanStepHandler(ChatModelPort chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel);
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.PLAN;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        String response = chatModel.chat(null, List.of(
                ChatMessage.system(PLAN_SYSTEM_PROMPT),
                ChatMessage.user(context.query())));

        if (response == null || response.isBlank()) {
            context.addSearchQuery(context.query());
            return;
        }

        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                context.addSearchQuery(trimmed);
            }
        }

        if (context.searchQueries().isEmpty()) {
            context.addSearchQuery(context.query());
        }
    }
}
