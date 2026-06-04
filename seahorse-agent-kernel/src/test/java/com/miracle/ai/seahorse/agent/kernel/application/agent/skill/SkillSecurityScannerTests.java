package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSecurityScannerTests {

    private final SkillSecurityScanner scanner = new SkillSecurityScanner();

    @Test
    void shouldAllowDocumentedSecretPlaceholders() {
        SkillScanResult result = scanner.scanContent("""
                User can set it in .env by uncommenting the line "GITHUB_TOKEN=your-github-token".
                """);

        assertEquals(SkillScanDecision.ALLOW, result.decision());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void shouldAllowBundledSkillResourceAppendixWithSecretPlaceholder() {
        SkillScanResult result = scanner.scanContent("""
                ---
                name: github-deep-research
                description: Conduct multi-round deep research on any GitHub Repo.
                ---

                # GitHub Deep Research Skill

                ## Skill Package Resources

                These bundled files are read-only reference material.

                ### scripts/github_api.py

                ```python
                token:
                    Optional GitHub personal access token for higher rate limits.
                    User can set it in .env by uncommenting the line "GITHUB_TOKEN=your-github-token".
                ```
                """);

        assertEquals(SkillScanDecision.ALLOW, result.decision());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void shouldBlockActualSecretLikeAssignments() {
        SkillScanResult result = scanner.scanContent("""
                GITHUB_TOKEN=ghp_1234567890abcdef1234567890abcdef1234
                """);

        assertEquals(SkillScanDecision.BLOCK, result.decision());
        assertTrue(result.reasons().contains("secret-like value is not allowed"));
    }
}
