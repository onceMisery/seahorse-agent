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

package com.miracle.ai.seahorse.agent.kernel.application.agent.output;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;

/**
 * Slice 1d：Markdown / Mermaid validator 共享的 attribute key 与决策决断辅助。
 *
 * <p>调用方可通过 {@link OutputValidationRequest#attributes()} 携带 {@value #ATTRIBUTE_STRICT}
 * 控制决策严格度：缺省 {@code true} 时违规返回 {@link OutputValidationDecision#BLOCK}，
 * 显式置为 {@code false} 时降级为 {@link OutputValidationDecision#WARN}。
 */
final class OutputStructuralValidationPolicy {

    /**
     * 请求 attribute key — 非严格模式下违规仅 WARN，不阻断生成。
     */
    static final String ATTRIBUTE_STRICT = "structuralStrict";

    private OutputStructuralValidationPolicy() {
    }

    static OutputValidationDecision decisionForViolation(OutputValidationRequest request) {
        if (request == null || request.attributes() == null) {
            return OutputValidationDecision.BLOCK;
        }
        Object explicit = request.attributes().get(ATTRIBUTE_STRICT);
        if (explicit instanceof Boolean strict && !strict) {
            return OutputValidationDecision.WARN;
        }
        if (explicit instanceof String s && "false".equalsIgnoreCase(s.trim())) {
            return OutputValidationDecision.WARN;
        }
        return OutputValidationDecision.BLOCK;
    }
}
