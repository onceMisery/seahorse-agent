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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class McpDiagnosticRedactor {

    static final String REDACTED = "[REDACTED]";

    private static final String TRUNCATED_PREFIX = "[truncated]\n";
    private static final String SECRET_KEY_PATTERN =
            "[A-Za-z0-9_-]*(?:api[_-]?key|access[_-]?key|access[_-]?token|refresh[_-]?token"
                    + "|client[_-]?secret|credential|mcp[_-]?stdio[_-]?e2e[_-]?secret|password|passwd"
                    + "|private[_-]?key|pwd|secret|token)[A-Za-z0-9_-]*";
    private static final Pattern AUTHORIZATION_SECRET = Pattern.compile(
            "(?i)((?:\\b|[\"'])authorization(?:\\b|[\"'])\\s*[:=]\\s*)([\"']?)(?:[A-Za-z]+\\s+)?([^\\s,\"']+)([\"']?)");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)((?:\\b|[\"'])" + SECRET_KEY_PATTERN + "(?:\\b|[\"'])\\s*[:=]\\s*)([\"']?)([^\\s,\"']+)([\"']?)");
    private static final Pattern BEARER_SECRET = Pattern.compile(
            "(?i)\\bBearer\\s+([A-Za-z0-9._~+\\-/]+=*)");
    private static final Pattern OPENAI_STYLE_SECRET = Pattern.compile(
            "\\bsk-[A-Za-z0-9][A-Za-z0-9._-]{6,}\\b");

    private McpDiagnosticRedactor() {
    }

    static String redact(String value) {
        String text = Objects.requireNonNullElse(value, "");
        if (text.isBlank()) {
            return text;
        }
        text = redactAuthorizationSecrets(text);
        text = redactKeyValueSecrets(text);
        text = BEARER_SECRET.matcher(text).replaceAll("Bearer " + REDACTED);
        text = OPENAI_STYLE_SECRET.matcher(text).replaceAll(REDACTED);
        return text;
    }

    static String redactAndTruncate(String value, int maxLength) {
        String redacted = redact(value);
        if (maxLength <= 0 || redacted.length() <= maxLength) {
            return redacted;
        }
        int suffixLength = Math.max(0, maxLength - TRUNCATED_PREFIX.length());
        return TRUNCATED_PREFIX + redacted.substring(redacted.length() - suffixLength);
    }

    private static String redactAuthorizationSecrets(String value) {
        Matcher matcher = AUTHORIZATION_SECRET.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + matcher.group(2) + REDACTED + matcher.group(4);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String redactKeyValueSecrets(String value) {
        Matcher matcher = KEY_VALUE_SECRET.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + matcher.group(2) + REDACTED + matcher.group(4);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
