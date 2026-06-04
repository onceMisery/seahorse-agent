package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves per-turn selected skill names into validated {@link SkillRuntimeBlock} instances.
 *
 * <p>Security: each name is re-validated server-side — the frontend selection is never trusted.
 * Only skills that are {@code enabled=true}, {@code status=ACTIVE}, and have a latest revision
 * are resolved.</p>
 */
public class ChatSelectedSkillResolver {

    private static final int DEFAULT_DIRECT_INJECT_THRESHOLD = 12000;
    private static final int DEFAULT_MAX_SELECTED_PER_TURN = 5;

    private final AgentSkillRepositoryPort repository;
    private final int directInjectThreshold;
    private final int maxSelectedPerTurn;

    public ChatSelectedSkillResolver(AgentSkillRepositoryPort repository) {
        this(repository, DEFAULT_DIRECT_INJECT_THRESHOLD, DEFAULT_MAX_SELECTED_PER_TURN);
    }

    public ChatSelectedSkillResolver(AgentSkillRepositoryPort repository,
                                      int directInjectThreshold,
                                      int maxSelectedPerTurn) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.directInjectThreshold = directInjectThreshold <= 0
                ? DEFAULT_DIRECT_INJECT_THRESHOLD : directInjectThreshold;
        this.maxSelectedPerTurn = maxSelectedPerTurn <= 0
                ? DEFAULT_MAX_SELECTED_PER_TURN : maxSelectedPerTurn;
    }

    /**
     * Resolve selected skill names into runtime blocks.
     *
     * @param tenantId            current tenant (may be null, defaults to "default")
     * @param selectedSkillNames  skill names chosen by the user in the chat input
     * @return validated skill runtime blocks, ready for merging with version-bound skills
     */
    public List<SkillRuntimeBlock> resolve(String tenantId, List<String> selectedSkillNames) {
        if (selectedSkillNames == null || selectedSkillNames.isEmpty()) {
            return List.of();
        }
        if (selectedSkillNames.size() > maxSelectedPerTurn) {
            throw new IllegalArgumentException(
                    "Too many skills selected: " + selectedSkillNames.size()
                            + " (maximum " + maxSelectedPerTurn + ")");
        }
        String safeTenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;

        List<SkillRuntimeBlock> blocks = new ArrayList<>();
        for (String name : selectedSkillNames) {
            blocks.add(resolveOne(safeTenantId, name));
        }
        return applyInjectionStrategy(blocks);
    }

    private SkillRuntimeBlock resolveOne(String tenantId, String name) {
        AgentSkill skill = repository.findSkill(tenantId, name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected skill not found: " + name));
        if (!skill.enabled()) {
            throw new IllegalArgumentException(
                    "Selected skill is disabled: " + name);
        }
        if (skill.status() != AgentSkillStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Selected skill is not active: " + name + " (status=" + skill.status() + ")");
        }
        String revisionId = skill.latestRevisionId();
        if (revisionId == null || revisionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Selected skill has no revision: " + name);
        }
        AgentSkillRevision revision = repository.findRevision(tenantId, revisionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected skill revision not found: " + name + " (revisionId=" + revisionId + ")"));
        return new SkillRuntimeBlock(
                skill.name(),
                revision.revisionId(),
                revision.contentHash(),
                skill.description(),
                skill.category(),
                SkillInjectMode.METADATA_AND_BODY,
                skill.allowedTools(),
                revision.content());
    }

    /**
     * Apply budget-driven injection strategy: if total content exceeds the threshold
     * or skill count is too high, switch all blocks to {@code METADATA_ONLY}.
     */
    private List<SkillRuntimeBlock> applyInjectionStrategy(List<SkillRuntimeBlock> blocks) {
        if (blocks.size() > 3) {
            return toMetadataOnly(blocks);
        }
        int totalContentLength = blocks.stream()
                .mapToInt(b -> b.content() == null ? 0 : b.content().length())
                .sum();
        if (totalContentLength > directInjectThreshold) {
            return toMetadataOnly(blocks);
        }
        return blocks;
    }

    private List<SkillRuntimeBlock> toMetadataOnly(List<SkillRuntimeBlock> blocks) {
        return blocks.stream()
                .map(b -> new SkillRuntimeBlock(
                        b.name(),
                        b.revisionId(),
                        b.contentHash(),
                        b.description(),
                        b.category(),
                        SkillInjectMode.METADATA_ONLY,
                        b.allowedTools(),
                        b.content()))
                .toList();
    }
}
