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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScoreFilters;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;

import java.util.List;
import java.util.Objects;

/**
 * 本地意图解析适配器。
 *
 * <p>该实现不做强意图分类，而是把每个子问题转为可检索输入。由于得分列表为空，
 * 后续检索会自动走全局向量检索通道，避免无意图树时主链路中断。
 */
public class LocalIntentResolutionAdapter implements IntentResolutionPort {

    @Override
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        if (rewriteResult == null) {
            return List.of();
        }
        List<String> subQuestions = Objects.requireNonNullElse(rewriteResult.subQuestions(), List.of());
        if (subQuestions.isEmpty()) {
            return singleIntent(rewriteResult.rewrittenQuestion());
        }
        return subQuestions.stream()
                .map(this::normalize)
                .filter(question -> !question.isBlank())
                .distinct()
                .map(question -> new SubQuestionIntent(question, List.of()))
                .toList();
    }

    @Override
    public boolean isSystemOnly(List<IntentScore> intentScores) {
        List<IntentNode> nodes = Objects.requireNonNullElse(intentScores, List.<IntentScore>of()).stream()
                        .filter(Objects::nonNull)
                        .map(IntentScore::getNode)
                        .filter(Objects::nonNull)
                        .toList();
        if (nodes.isEmpty()) {
            return false;
        }
        return nodes.stream().allMatch(node -> node.isSystem());
    }

    @Override
    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        List<IntentScore> scores = Objects.requireNonNullElse(subIntents, List.<SubQuestionIntent>of()).stream()
                .filter(Objects::nonNull)
                .map(SubQuestionIntent::intentScores)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
        return new IntentGroup(IntentScoreFilters.kb(scores), IntentScoreFilters.mcp(scores));
    }

    private List<SubQuestionIntent> singleIntent(String question) {
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.isBlank()) {
            return List.of();
        }
        return List.of(new SubQuestionIntent(normalizedQuestion, List.of()));
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
