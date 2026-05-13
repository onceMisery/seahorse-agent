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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;

import java.util.Objects;

/**
 * 检索评测中的单个候选策略。
 *
 * @param strategyName 策略名称，用于报表展示和 winner 判定
 * @param topK         策略级截断深度，0 表示继承比较命令的 topK
 * @param options      策略级检索参数，样本级 options 仍可覆盖该默认值
 */
public record RetrievalEvaluationStrategy(
        String strategyName,
        int topK,
        RetrievalOptions options
) {

    public RetrievalEvaluationStrategy {
        strategyName = Objects.requireNonNullElse(strategyName, "");
        topK = Math.max(0, topK);
    }
}
