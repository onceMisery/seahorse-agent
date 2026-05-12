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

package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索通道共享上下文。
 * <p>
 * 检索阶段只持有 Seahorse 自有意图契约。
 */
@Data
@Builder
public class SearchContext {

    private String originalQuestion;

    private String rewrittenQuestion;

    private List<String> subQuestions;

    private List<SubQuestionIntent> intents;

    private int topK;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public String getMainQuestion() {
        return rewrittenQuestion != null ? rewrittenQuestion : originalQuestion;
    }
}
