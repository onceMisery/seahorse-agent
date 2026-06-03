package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import java.util.List;
import java.util.Map;

public record SkillMarkdownDocument(String name,
                                    String description,
                                    String license,
                                    List<String> allowedTools,
                                    List<String> tags,
                                    Map<String, Object> frontmatter,
                                    String body,
                                    String content) {

    public SkillMarkdownDocument {
        name = AgentSkill.normalizeName(name);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        description = description.trim();
        license = license == null || license.isBlank() ? null : license.trim();
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        tags = tags == null ? List.of() : List.copyOf(tags);
        frontmatter = frontmatter == null ? Map.of() : Map.copyOf(frontmatter);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("skill body must not be blank");
        }
        body = body.trim();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
