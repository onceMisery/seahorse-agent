package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves per-turn selected skill names into validated {@link SkillRuntimeBlock} instances.
 *
 * <p>Security: each name is re-validated server-side — the frontend selection is never trusted.
 * Only skills that are {@code enabled=true}, {@code status=ACTIVE}, and have a latest revision
 * are resolved.</p>
 */
public class ChatSelectedSkillResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ChatSelectedSkillResolver.class);

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
        String safeTenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        List<String> names = selectedSkillNames.stream()
                .limit(maxSelectedPerTurn)
                .toList();

        List<SkillRuntimeBlock> blocks = new ArrayList<>();
        for (String name : names) {
            Optional<SkillRuntimeBlock> block = resolveOne(safeTenantId, name);
            block.ifPresent(blocks::add);
        }
        return applyInjectionStrategy(blocks);
    }

    private Optional<SkillRuntimeBlock> resolveOne(String tenantId, String name) {
        Optional<AgentSkill> skillOpt = repository.findSkill(tenantId, name);
        if (skillOpt.isEmpty()) {
            LOG.warn("Per-turn selected skill not found: tenantId={}, name={}", tenantId, name);
            return Optional.empty();
        }
        AgentSkill skill = skillOpt.get();
        if (!skill.enabled()) {
            LOG.warn("Per-turn selected skill is disabled: tenantId={}, name={}", tenantId, name);
            return Optional.empty();
        }
        if (skill.status() != AgentSkillStatus.ACTIVE) {
            LOG.warn("Per-turn selected skill is not active: tenantId={}, name={}, status={}",
                    tenantId, name, skill.status());
            return Optional.empty();
        }
        String revisionId = skill.latestRevisionId();
        if (revisionId == null || revisionId.isBlank()) {
            LOG.warn("Per-turn selected skill has no revision: tenantId={}, name={}", tenantId, name);
            return Optional.empty();
        }
        Optional<AgentSkillRevision> revisionOpt = repository.findRevision(tenantId, revisionId);
        if (revisionOpt.isEmpty()) {
            LOG.warn("Per-turn selected skill revision not found: tenantId={}, name={}, revisionId={}",
                    tenantId, name, revisionId);
            return Optional.empty();
        }
        AgentSkillRevision revision = revisionOpt.get();
        return Optional.of(new SkillRuntimeBlock(
                skill.name(),
                revision.revisionId(),
                revision.contentHash(),
                skill.description(),
                skill.category(),
                SkillInjectMode.METADATA_AND_BODY,
                skill.allowedTools(),
                revision.content()));
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
