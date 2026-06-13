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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ProfileSlotResolver {

    private static final List<String> PROFILE_SLOT_KEYS = List.of(
            "identity.occupation",
            "identity.name",
            "skills.tech_stack",
            "preferences.response_style");

    String resolve(MemoryItem item) {
        if (item == null) {
            return "";
        }
        return resolve(item.getType(), item.getContent(), item.getMetadataJson());
    }

    String resolve(String type, String content, String metadataJson) {
        List<String> slots = resolveAll(type, content, metadataJson);
        return slots.isEmpty() ? "" : slots.get(0);
    }

    List<String> resolveAll(String type, String content, String metadataJson) {
        String metadataSlot = metadataSlot(metadataJson);
        if (!metadataSlot.isBlank()) {
            return List.of(metadataSlot);
        }
        if (!isProfileLike(type)) {
            return List.of();
        }
        String safeContent = Objects.requireNonNullElse(content, "");
        String normalized = safeContent.toLowerCase(java.util.Locale.ROOT);
        List<String> slots = new ArrayList<>();
        if (normalized.startsWith("my name is ")) {
            addSlot(slots, "identity.name");
        }
        if (safeContent.startsWith("\u6211\u53eb")
                || safeContent.startsWith("\u6211\u7684\u540d\u5b57\u662f")
                || safeContent.startsWith("\u6211\u7684\u6635\u79f0\u662f")) {
            addSlot(slots, "identity.name");
        }
        if (normalized.startsWith("my tech stack is ")) {
            addSlot(slots, "skills.tech_stack");
        }
        if (safeContent.startsWith("\u6211\u7684\u6280\u672f\u6808\u662f")
                || safeContent.startsWith("\u6211\u4e3b\u8981\u4f7f\u7528")) {
            addSlot(slots, "skills.tech_stack");
        }
        if (containsAny(normalized, "occupation", "profession", "job", "student", "teacher")
                || containsAny(safeContent, "\u804c\u4e1a", "\u8eab\u4efd", "\u5de5\u4f5c",
                "\u5b66\u751f", "\u8001\u5e08", "\u6559\u5e08")) {
            addSlot(slots, "identity.occupation");
        }
        if (normalized.contains("i prefer ")
                || normalized.contains("i like concise answers")
                || normalized.contains("i like detailed answers")) {
            addSlot(slots, "preferences.response_style");
        }
        if (safeContent.contains("\u6211\u559c\u6b22\u7b80\u77ed\u56de\u7b54")
                || safeContent.contains("\u6211\u559c\u6b22\u8be6\u7ec6\u56de\u7b54")
                || safeContent.contains("\u6211\u504f\u597d\u7b80\u77ed\u56de\u7b54")
                || safeContent.contains("\u6211\u504f\u597d\u8be6\u7ec6\u56de\u7b54")) {
            addSlot(slots, "preferences.response_style");
        }
        if (isChineseResponseStylePreference(safeContent)) {
            addSlot(slots, "preferences.response_style");
        }
        return slots;
    }

    String correctionTargetSlot(MemoryItem correction) {
        String metadata = correction == null ? "" : Objects.requireNonNullElse(correction.getMetadataJson(), "");
        String targetKind = metadataValue(metadata, "targetKind");
        String targetKey = metadataValue(metadata, "targetKey");
        if ("PROFILE_SLOT".equalsIgnoreCase(targetKind) && !targetKey.isBlank()) {
            return targetKey;
        }
        return "";
    }

    private String metadataSlot(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "";
        }
        if (metadataContainsValue(metadata, "semanticKey", "profile:occupation")) {
            return "identity.occupation";
        }
        for (String slot : PROFILE_SLOT_KEYS) {
            if (metadataContainsValue(metadata, "semanticKey", slot)
                    || metadataContainsValue(metadata, "profileSlot", slot)) {
                return slot;
            }
        }
        return "";
    }

    private boolean isChineseResponseStylePreference(String content) {
        return containsAny(content,
                "\u6211\u559c\u6b22",
                "\u6211\u504f\u597d",
                "\u6211\u4e60\u60ef",
                "\u6211\u5e0c\u671b")
                && containsAny(content, "\u56de\u7b54", "\u56de\u590d");
    }

    private void addSlot(List<String> slots, String slot) {
        if (!slots.contains(slot)) {
            slots.add(slot);
        }
    }

    private boolean startsWithAny(String content, String... prefixes) {
        if (content == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && content.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean metadataContainsValue(String metadata, String key, String value) {
        return value.equals(metadataValue(metadata, key));
    }

    private String metadataValue(String metadata, String key) {
        if (metadata == null || metadata.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String compactPrefix = "\"" + key + "\":\"";
        int compactStart = metadata.indexOf(compactPrefix);
        if (compactStart >= 0) {
            int valueStart = compactStart + compactPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        String spacedPrefix = "\"" + key + "\": \"";
        int spacedStart = metadata.indexOf(spacedPrefix);
        if (spacedStart >= 0) {
            int valueStart = spacedStart + spacedPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        return "";
    }

    private boolean isProfileLike(String type) {
        String safeType = Objects.requireNonNullElse(type, "");
        return "PROFILE".equalsIgnoreCase(safeType)
                || "FACT".equalsIgnoreCase(safeType)
                || "PREFERENCE".equalsIgnoreCase(safeType);
    }

    private boolean containsAny(String content, String... needles) {
        if (content == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && content.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
