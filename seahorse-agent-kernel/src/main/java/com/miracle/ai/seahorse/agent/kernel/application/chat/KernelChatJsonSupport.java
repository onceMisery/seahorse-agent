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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * JSON 解析与文本规范化工具（从 {@link KernelChatInboundService} 提取的纯静态协作者）。
 * 按 §7 收敛原则外提，无字段依赖，调用方通过 static import 使用。
 */
final class KernelChatJsonSupport {

    private KernelChatJsonSupport() {
    }

    static String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return hasText(text) ? text.trim() : null;
    }

    static String firstText(JsonNode root, String primaryField, String secondaryField, String fallback) {
        String primary = text(root, primaryField);
        if (hasText(primary)) {
            return primary;
        }
        String secondary = text(root, secondaryField);
        return hasText(secondary) ? secondary : fallback;
    }

    static Double doubleValue(JsonNode root, String fieldName, Double fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        return value.asDouble();
    }

    static Integer intValue(JsonNode root, String fieldName, Integer fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isIntegralNumber()) {
            return fallback;
        }
        return value.asInt();
    }

    static Boolean booleanValue(JsonNode root, String fieldName, Boolean fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isBoolean()) {
            return fallback;
        }
        return value.asBoolean();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String stringValue(Object value) {
        if (value instanceof String text && hasText(text)) {
            return text.trim();
        }
        return null;
    }

    static void putTextIfPresent(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value.trim());
        }
    }

    static Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
