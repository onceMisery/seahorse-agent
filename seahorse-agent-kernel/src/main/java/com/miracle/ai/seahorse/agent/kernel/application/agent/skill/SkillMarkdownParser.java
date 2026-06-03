package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillMarkdownDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class SkillMarkdownParser {

    public SkillMarkdownDocument parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("skill markdown must not be blank");
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("skill markdown must start with frontmatter");
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("skill frontmatter must be closed");
        }
        String frontmatterText = normalized.substring(4, end).trim();
        String body = normalized.substring(end + 4).trim();
        Map<String, Object> frontmatter = parseFrontmatter(frontmatterText);
        String name = value(frontmatter, "name");
        String description = value(frontmatter, "description");
        String license = optionalValue(frontmatter, "license");
        List<String> allowedTools = list(frontmatter, "allowed_tools");
        List<String> tags = list(frontmatter, "tags");
        frontmatter.put("name", AgentSkill.normalizeName(name));
        frontmatter.put("description", description);
        frontmatter.put("allowed_tools", allowedTools);
        frontmatter.put("tags", tags);
        return new SkillMarkdownDocument(name, description, license, allowedTools, tags, frontmatter, body, normalized);
    }

    private Map<String, Object> parseFrontmatter(String text) {
        Map<String, Object> values = new LinkedHashMap<>();
        String currentListKey = null;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                if (currentListKey == null) {
                    throw new IllegalArgumentException("frontmatter list item has no key");
                }
                mutableList(values, currentListKey).add(trimmed.substring(2).trim());
                continue;
            }
            int sep = line.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("invalid frontmatter line: " + line);
            }
            String key = line.substring(0, sep).trim();
            String value = line.substring(sep + 1).trim();
            if (value.isEmpty()) {
                values.put(key, new ArrayList<String>());
                currentListKey = key;
            } else {
                values.put(key, stripQuotes(value));
                currentListKey = null;
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<String> mutableList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            List<String> list = new ArrayList<>();
            values.put(key, list);
            return list;
        }
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        throw new IllegalArgumentException(key + " must be a list");
    }

    private List<String> list(Map<String, Object> values, String key) {
        List<String> raw = mutableList(values, key);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : raw) {
            if (item != null && !item.isBlank()) {
                normalized.add(item.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private String value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return text.trim();
    }

    private String optionalValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
