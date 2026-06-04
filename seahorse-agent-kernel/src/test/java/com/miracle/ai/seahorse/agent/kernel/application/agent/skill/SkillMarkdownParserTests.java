package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillMarkdownDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMarkdownParserTests {

    private final SkillMarkdownParser parser = new SkillMarkdownParser();

    @Test
    void shouldParseFoldedFrontmatterDescription() {
        SkillMarkdownDocument document = parser.parse("""
                ---
                name: bootstrap
                description: >-
                  Generate a personalized SOUL.md through a warm, adaptive onboarding conversation.
                  Trigger when the user wants to create, set up, or initialize their AI partner's
                  identity.
                ---

                # Bootstrap Soul
                """);

        assertEquals("bootstrap", document.name());
        assertEquals("""
                Generate a personalized SOUL.md through a warm, adaptive onboarding conversation. Trigger when the user wants to create, set up, or initialize their AI partner's identity.""",
                document.description());
        assertTrue(document.body().contains("# Bootstrap Soul"));
    }

    @Test
    void shouldParseExistingListFrontmatter() {
        SkillMarkdownDocument document = parser.parse("""
                ---
                name: research-helper
                description: Research helper
                allowed_tools:
                  - web_search
                  - web_fetch
                tags:
                  - research
                ---

                # Research helper
                """);

        assertEquals("research-helper", document.name());
        assertEquals(2, document.allowedTools().size());
        assertTrue(document.allowedTools().contains("web_search"));
        assertTrue(document.tags().contains("research"));
    }
}
