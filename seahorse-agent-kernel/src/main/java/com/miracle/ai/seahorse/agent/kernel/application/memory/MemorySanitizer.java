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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

class MemorySanitizer {

    SanitizedMemoryInput sanitize(String rawContent) {
        String content = Objects.requireNonNullElse(rawContent, "").trim();
        if (content.isBlank()) {
            return SanitizedMemoryInput.rejected("", "blank", List.of("blank"));
        }
        List<String> signals = new ArrayList<>();
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (!normalized.equals(content)) {
            signals.add("normalized_whitespace");
        }
        if (containsSensitiveCredential(normalized)) {
            signals.add("sensitive_credential");
            return SanitizedMemoryInput.rejected(normalized, "sensitive_credential", signals);
        }
        return SanitizedMemoryInput.accepted(normalized, signals);
    }

    boolean containsSensitiveCredential(String content) {
        String value = Objects.requireNonNullElse(content, "");
        String lower = value.toLowerCase(Locale.ROOT);
        return value.contains("密码")
                || value.contains("密钥")
                || value.contains("验证码")
                || value.contains("身份证")
                || value.contains("银行卡")
                || lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("access token")
                || lower.contains("refresh token")
                || lower.contains("secret");
    }
}
