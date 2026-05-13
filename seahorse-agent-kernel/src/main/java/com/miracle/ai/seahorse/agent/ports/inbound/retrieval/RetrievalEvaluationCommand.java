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

import java.util.List;
import java.util.Objects;

/**
 * 检索评测运行命令。
 *
 * @param strategyName 策略名称，用于 A/B 或基线对比标识
 * @param topK         评测截断深度
 * @param options      本次评测默认检索策略参数
 * @param cases        评测样本集合
 */
public record RetrievalEvaluationCommand(
        String strategyName,
        int topK,
        RetrievalOptions options,
        List<RetrievalEvaluationCase> cases
) {

    public RetrievalEvaluationCommand {
        strategyName = Objects.requireNonNullElse(strategyName, "");
        topK = topK > 0 ? topK : 5;
        cases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
    }
}
