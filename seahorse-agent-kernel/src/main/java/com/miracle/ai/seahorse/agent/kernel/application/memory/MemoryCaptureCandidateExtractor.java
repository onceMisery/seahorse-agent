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
import java.util.Optional;

class MemoryCaptureCandidateExtractor {

    private String lastRejectionReason = "";

    Optional<MemoryCaptureCandidate> extract(String rawContent) {
        lastRejectionReason = "";
        List<String> signals = new ArrayList<>();
        String content = normalizeMemoryText(rawContent, signals);
        if (content.isBlank()) {
            return reject("blank");
        }

        PrefixRemoval removal = removeExplicitPrefix(content);
        String normalized = removal.content();
        signals.addAll(removal.signals());

        boolean explicitRemember = !normalized.equals(content);
        boolean profileStatement = startsWithAny(normalized, "我是", "我在", "我来自", "我负责", "我擅长");
        boolean preferenceStatement = startsWithAny(normalized, "我喜欢", "我偏好", "我习惯", "我常用");
        boolean personalFact = isPersonalFactStatement(normalized);

        if (profileStatement) {
            signals.add("profile_statement");
        }
        if (preferenceStatement) {
            signals.add("preference_statement");
        }
        if (personalFact) {
            signals.add("personal_fact");
        }
        if (!explicitRemember && !profileStatement && !preferenceStatement && !personalFact) {
            return reject("no_high_value_signal");
        }

        String candidate = normalized.replaceFirst("^[：:，,\\s]+", "").trim();
        String trimmedCandidate = removeLowValueSocialTail(candidate);
        if (!trimmedCandidate.equals(candidate)) {
            signals.add("trimmed_social_tail");
        }
        candidate = trimmedCandidate;

        if (candidate.length() < 2) {
            return reject("too_short");
        }
        if (candidate.length() > 120) {
            return reject("too_long");
        }
        if (looksLikeQuestion(candidate)) {
            return reject("question");
        }
        if (containsSensitiveCredential(candidate)) {
            return reject("sensitive_credential");
        }

        return Optional.of(new MemoryCaptureCandidate(
                candidate,
                inferMemoryType(candidate),
                removal.explicitImportant(),
                signals));
    }

    String lastRejectionReason() {
        return lastRejectionReason;
    }

    private Optional<MemoryCaptureCandidate> reject(String reason) {
        lastRejectionReason = reason;
        return Optional.empty();
    }

    private PrefixRemoval removeExplicitPrefix(String content) {
        String normalized = content
                .replaceFirst("^(请|帮我|麻烦你)?记住[：:，,\\s]*", "")
                .replaceFirst("^以后(都|请)?按[这此]个来[：:，,\\s]*", "");
        if (normalized.equals(content)) {
            return new PrefixRemoval(normalized, false, List.of());
        }
        List<String> signals = new ArrayList<>();
        signals.add("explicit_memory_request");
        if (content.startsWith("以后")) {
            signals.add("explicit_important");
        }
        return new PrefixRemoval(normalized, content.startsWith("以后"), signals);
    }

    private String normalizeMemoryText(String rawContent, List<String> signals) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isBlank()) {
            return "";
        }
        String before = content;
        content = content.replaceAll("\\s+", " ");
        content = content.replaceFirst("^我\\s*是\\s*", "我是");
        content = content.replaceFirst("^我\\s*在\\s*", "我在");
        content = content.replaceFirst("^我\\s*来自\\s*", "我来自");
        content = content.replaceFirst("^我\\s*负责\\s*", "我负责");
        content = content.replaceFirst("^我\\s*擅长\\s*", "我擅长");
        content = content.replaceFirst("^我\\s*喜欢\\s*", "我喜欢");
        content = content.replaceFirst("^我\\s*偏好\\s*", "我偏好");
        content = content.replaceFirst("^我\\s*习惯\\s*", "我习惯");
        content = content.replaceFirst("^我\\s*常用\\s*", "我常用");
        content = content.replaceFirst("^我\\s*的\\s*", "我的");
        content = content.replaceAll("(?<=\\p{IsHan}) (?=\\p{IsHan})", "");
        content = content.trim();
        if (!content.equals(before)) {
            signals.add("normalized_chinese_whitespace");
        }
        return content;
    }

    private String removeLowValueSocialTail(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
                .replaceFirst("[，,。；;\\s]*(很高兴认识你|认识你很高兴|谢谢|感谢)[。！!\\s]*$", "")
                .trim();
    }

    private boolean looksLikeQuestion(String content) {
        return content.endsWith("?") || content.endsWith("？")
                || content.contains("是什么") || content.contains("怎么") || content.contains("如何");
    }

    private boolean isPersonalFactStatement(String content) {
        return startsWithAny(content,
                "我的职业是", "我的身份是", "我的工作是", "我的专业是", "我的学校是",
                "我的公司是", "我的岗位是", "我的角色是", "我的职责是", "我的行业是",
                "我的系统是", "我的开发环境是", "我的名字是", "我的昵称是");
    }

    private boolean containsSensitiveCredential(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return content.contains("密码")
                || content.contains("密钥")
                || content.contains("验证码")
                || content.contains("身份证")
                || content.contains("银行卡")
                || lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("access token")
                || lower.contains("refresh token")
                || lower.contains("secret");
    }

    private String inferMemoryType(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (content.contains("喜欢") || content.contains("偏好") || content.contains("习惯")
                || content.contains("常用") || lower.contains("prefer") || lower.contains("like")) {
            return "PREFERENCE";
        }
        if (startsWithAny(content, "我是", "我在", "我来自", "我负责", "我擅长")) {
            return "PROFILE";
        }
        return "FACT";
    }

    private boolean startsWithAny(String content, String... prefixes) {
        if (content == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (content.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record PrefixRemoval(String content, boolean explicitImportant, List<String> signals) {
    }
}
