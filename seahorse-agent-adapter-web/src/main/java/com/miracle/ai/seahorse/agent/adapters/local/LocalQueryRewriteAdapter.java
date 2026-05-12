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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 本地确定性问题改写适配器。
 *
 * <p>默认配置不额外调用大模型，避免引入额外延迟；它保留原问题语义，并按常见中英文问题分隔符生成子问题，
 * 保证 RAG 主链路在未配置外部改写模型时仍能进入检索阶段。
 */
public class LocalQueryRewriteAdapter implements QueryRewritePort {

    private static final String QUESTION_SPLIT_REGEX = "[\\r\\n]+|[？?；;]";

    @Override
    public RewriteResult rewriteWithSplit(String question, List<ChatMessage> history) {
        String rewrittenQuestion = normalize(question);
        List<String> subQuestions = splitSubQuestions(rewrittenQuestion);
        return new RewriteResult(rewrittenQuestion, subQuestions);
    }

    private List<String> splitSubQuestions(String question) {
        if (question.isBlank()) {
            return List.of();
        }
        List<String> subQuestions = Arrays.stream(question.split(QUESTION_SPLIT_REGEX))
                .map(this::normalize)
                .filter(candidate -> !candidate.isBlank())
                .distinct()
                .toList();
        if (subQuestions.isEmpty()) {
            return List.of(question);
        }
        return subQuestions;
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
