package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillSecurityScanner {

    public static final int MAX_ARCHIVE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CONTENT_BYTES = 512 * 1024;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:^|[^a-z0-9_\\-])([a-z0-9_\\-]*(?:api[_-]?key|secret|token))\\s*[:=]\\s*([a-z0-9_\\-]{16,})");
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "your",
            "example",
            "sample",
            "placeholder",
            "dummy",
            "changeme",
            "replace",
            "todo");

    public SkillScanResult scanContent(String content) {
        List<String> blockReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (content == null || content.isBlank()) {
            blockReasons.add("content is blank");
            return new SkillScanResult(SkillScanDecision.BLOCK, blockReasons, warnings);
        }
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            blockReasons.add("content exceeds max size");
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (containsActualSecretLikeAssignment(lower)) {
            blockReasons.add("secret-like value is not allowed");
        }
        if (lower.contains("rm -rf") || lower.contains("curl | sh") || lower.contains("powershell -enc")) {
            warnings.add("dangerous command phrase detected");
        }
        SkillScanDecision decision = blockReasons.isEmpty()
                ? (warnings.isEmpty() ? SkillScanDecision.ALLOW : SkillScanDecision.WARN)
                : SkillScanDecision.BLOCK;
        return new SkillScanResult(decision, blockReasons, warnings);
    }

    private boolean containsActualSecretLikeAssignment(String lowerContent) {
        Matcher matcher = SECRET_ASSIGNMENT.matcher(lowerContent);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (!isPlaceholder(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlaceholder(String value) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(lowerValue::contains);
    }

    public SkillScanResult scanArchive(long archiveBytes, List<String> entryNames) {
        List<String> blockReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (archiveBytes > MAX_ARCHIVE_BYTES) {
            blockReasons.add("archive exceeds max size");
        }
        boolean hasSkill = false;
        for (String entryName : entryNames == null ? List.<String>of() : entryNames) {
            String name = entryName == null ? "" : entryName.replace('\\', '/');
            if (name.startsWith("/") || name.contains("../") || name.startsWith(".")) {
                blockReasons.add("archive contains unsafe entry path");
            }
            if (name.endsWith("SKILL.md")) {
                hasSkill = true;
            }
        }
        if (!hasSkill) {
            blockReasons.add("archive must contain SKILL.md");
        }
        return new SkillScanResult(blockReasons.isEmpty() ? SkillScanDecision.ALLOW : SkillScanDecision.BLOCK,
                blockReasons,
                warnings);
    }
}
