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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent-tool 绑定上的入参策略。
 *
 * <p>当前最小实现只支持两类约束：`required` 必填参数和 `allowed` 参数白名单。
 * 后续如需 JSON Schema、类型校验或资源 ACL，应在此类扩展，不把解析细节扩散到主策略流程。
 */
final class ToolArgumentPolicy {

    static final String KEY_REQUIRED = "required";
    static final String KEY_ALLOWED = "allowed";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Set<String> requiredArguments;
    private final Set<String> allowedArguments;

    private ToolArgumentPolicy(Set<String> requiredArguments, Set<String> allowedArguments) {
        this.requiredArguments = Set.copyOf(requiredArguments);
        this.allowedArguments = Set.copyOf(allowedArguments);
    }

    /**
     * 从绑定配置解析参数策略；空对象表示不限制入参。
     */
    static ToolArgumentPolicy parse(String policyJson) {
        String trimmed = policyJson == null ? "" : policyJson.trim();
        if (trimmed.isEmpty()) {
            return empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            if (root == null || root.isNull() || root.isEmpty()) {
                return empty();
            }
            if (!root.isObject()) {
                throw new IllegalArgumentException("argument policy must be a JSON object");
            }
            return new ToolArgumentPolicy(textSet(root.get(KEY_REQUIRED)), textSet(root.get(KEY_ALLOWED)));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("argument policy JSON is invalid", ex);
        }
    }

    /**
     * 校验工具入参是否满足必填和白名单约束。
     */
    Optional<Violation> validate(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        for (String requiredArgument : requiredArguments) {
            if (isMissing(safeArguments.get(requiredArgument))) {
                return Optional.of(new Violation(
                        ToolPolicyReasonCodes.TOOL_ARGUMENT_REQUIRED_MISSING,
                        "Required tool argument is missing: " + requiredArgument));
            }
        }
        if (!allowedArguments.isEmpty()) {
            for (String argumentName : safeArguments.keySet()) {
                if (!allowedArguments.contains(argumentName)) {
                    return Optional.of(new Violation(
                            ToolPolicyReasonCodes.TOOL_ARGUMENT_NOT_ALLOWED,
                            "Tool argument is not allowed: " + argumentName));
                }
            }
        }
        return Optional.empty();
    }

    private static ToolArgumentPolicy empty() {
        return new ToolArgumentPolicy(Set.of(), Set.of());
    }

    private static Set<String> textSet(JsonNode node) {
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("argument policy item must be an array");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("argument policy argument name must be a string");
            }
            String value = item.asText().trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.trim().isEmpty();
    }

    /**
     * 参数策略违规结果。
     *
     * @param reasonCode    稳定策略原因码
     * @param reasonMessage 人类可读违规说明
     */
    record Violation(String reasonCode, String reasonMessage) {
    }
}
