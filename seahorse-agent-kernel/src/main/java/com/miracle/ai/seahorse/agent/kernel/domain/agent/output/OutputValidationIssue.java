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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

import java.util.Objects;

/**
 * 单条输出校验问题。
 *
 * @param code     问题编码，例如 {@code JSON_REQUIRED_FIELD_MISSING}
 * @param path     问题路径，例如 JSON pointer 或 Markdown 章节路径，可为空
 * @param message  人类可读问题信息
 * @param decision 该问题应触发的决策
 */
public record OutputValidationIssue(
        String code,
        String path,
        String message,
        OutputValidationDecision decision) {

    public OutputValidationIssue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        path = Objects.requireNonNullElse(path, "");
        message = Objects.requireNonNullElse(message, "");
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
