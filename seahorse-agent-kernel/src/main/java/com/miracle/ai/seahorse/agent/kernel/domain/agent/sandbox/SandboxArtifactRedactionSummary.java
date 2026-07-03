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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SandboxArtifactRedactionSummary {

    public static final int MAX_JSON_LENGTH = 2048;

    private static final int SCHEMA_VERSION = 1;
    private static final String SCANNER_ID = "default-sandbox-artifact-scanner";

    private SandboxArtifactRedactionSummary() {
    }

    public static String clean(String reason, boolean contentScanned) {
        return of(SandboxArtifactScanStatus.CLEAN, reason, contentScanned, List.of());
    }

    public static String redacted(String reason, boolean contentScanned, Collection<String> categories) {
        return of(SandboxArtifactScanStatus.REDACTED, reason, contentScanned, categories);
    }

    public static String blocked(String reason, boolean contentScanned, Collection<String> categories) {
        return of(SandboxArtifactScanStatus.BLOCKED, reason, contentScanned, categories);
    }

    public static String defaultFor(SandboxArtifactScanStatus scanStatus, String reason) {
        return of(scanStatus, reason, false, List.of());
    }

    public static String normalize(String value, SandboxArtifactScanStatus scanStatus, String reason) {
        if (hasText(value)) {
            String trimmed = value.trim();
            if (trimmed.length() <= MAX_JSON_LENGTH) {
                return trimmed;
            }
            return of(scanStatus, "redaction summary truncated", false, List.of("SUMMARY_TRUNCATED"));
        }
        return defaultFor(scanStatus, reason);
    }

    public static String of(SandboxArtifactScanStatus scanStatus,
                            String reason,
                            boolean contentScanned,
                            Collection<String> categories) {
        SandboxArtifactScanStatus safeStatus = Objects.requireNonNullElse(scanStatus, SandboxArtifactScanStatus.BLOCKED);
        String safeReason = hasText(reason) ? reason.trim() : safeStatus.name();
        StringBuilder builder = new StringBuilder(256);
        builder.append('{')
                .append("\"schemaVersion\":").append(SCHEMA_VERSION).append(',')
                .append("\"scanner\":\"").append(SCANNER_ID).append("\",")
                .append("\"decision\":\"").append(safeStatus.name()).append("\",")
                .append("\"blocked\":").append(safeStatus == SandboxArtifactScanStatus.BLOCKED).append(',')
                .append("\"redacted\":").append(safeStatus == SandboxArtifactScanStatus.REDACTED).append(',')
                .append("\"contentScanned\":").append(contentScanned).append(',')
                .append("\"categories\":").append(categoriesJson(categories)).append(',')
                .append("\"reason\":\"").append(jsonEscape(safeReason)).append("\"")
                .append('}');
        String json = builder.toString();
        if (json.length() <= MAX_JSON_LENGTH) {
            return json;
        }
        return of(safeStatus, "redaction summary truncated", false, List.of("SUMMARY_TRUNCATED"));
    }

    private static String categoriesJson(Collection<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String category : categories) {
            if (!hasText(category)) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(jsonEscape(category.trim().toUpperCase(Locale.ROOT))).append('"');
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    private static String jsonEscape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
