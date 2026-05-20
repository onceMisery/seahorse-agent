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

import java.util.Objects;
import java.util.regex.Pattern;

record OccupationCorrection(String incorrectValue, String correctValue) {

    private static final Pattern OCCUPATION_CORRECTION_PATTERN = Pattern
            .compile("^我不是(.{1,20}?)(?:了)?[，,。\\s]*(?:我)?(?:现在|目前)?是(.{1,20})$");

    OccupationCorrection {
        incorrectValue = Objects.requireNonNullElse(incorrectValue, "").trim();
        correctValue = Objects.requireNonNullElse(correctValue, "").trim();
    }

    static OccupationCorrection extract(String rawContent) {
        String content = normalizeUserFactText(rawContent);
        java.util.regex.Matcher matcher = OCCUPATION_CORRECTION_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String incorrect = normalizeOccupationValue(matcher.group(1));
        String correct = normalizeOccupationValue(matcher.group(2));
        if (incorrect.isBlank() || correct.isBlank()) {
            return null;
        }
        return new OccupationCorrection(incorrect, correct);
    }

    static String normalizeOccupationValue(String value) {
        String normalized = Objects.requireNonNullElse(value, "")
                .replaceAll("[。！!，,；;\\s]+$", "")
                .trim();
        if (normalized.startsWith("一名") || normalized.startsWith("一位")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.contains("学生")) {
            return "学生";
        }
        if (normalized.contains("老师") || normalized.contains("教师")) {
            return "老师";
        }
        return normalized;
    }

    static String normalizeUserFactText(String rawContent) {
        String content = Objects.requireNonNullElse(rawContent, "").trim();
        content = content.replaceAll("\\s+", " ");
        content = content.replaceAll("(?<=\\p{IsHan}) (?=\\p{IsHan})", "");
        return content;
    }
}
