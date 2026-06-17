package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillMarkdownDocument;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanResult;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentSkillManagementService implements AgentSkillManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";

    private final AgentSkillRepositoryPort repository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final SkillMarkdownParser parser;
    private final SkillSecurityScanner scanner;
    private final ObjectMapper objectMapper;
    private final SkillVectorIndexService vectorIndexService;

    public KernelAgentSkillManagementService(AgentSkillRepositoryPort repository,
                                             CurrentUserPort currentUserPort,
                                             Clock clock) {
        this(repository, currentUserPort, clock, null);
    }

    public KernelAgentSkillManagementService(AgentSkillRepositoryPort repository,
                                             CurrentUserPort currentUserPort,
                                             Clock clock,
                                             SkillVectorIndexService vectorIndexService) {
        this(repository, currentUserPort, clock, new SkillMarkdownParser(), new SkillSecurityScanner(),
                new ObjectMapper(), vectorIndexService);
    }

    public KernelAgentSkillManagementService(AgentSkillRepositoryPort repository,
                                             CurrentUserPort currentUserPort,
                                             Clock clock,
                                             SkillMarkdownParser parser,
                                             SkillSecurityScanner scanner,
                                             ObjectMapper objectMapper) {
        this(repository, currentUserPort, clock, parser, scanner, objectMapper, null);
    }

    public KernelAgentSkillManagementService(AgentSkillRepositoryPort repository,
                                             CurrentUserPort currentUserPort,
                                             Clock clock,
                                             SkillMarkdownParser parser,
                                             SkillSecurityScanner scanner,
                                             ObjectMapper objectMapper,
                                             SkillVectorIndexService vectorIndexService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.vectorIndexService = vectorIndexService;
    }

    @Override
    public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.page(defaultTenant(tenantId), current, size, keyword);
    }

    @Override
    public Optional<AgentSkill> find(String tenantId, String name) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.findSkill(defaultTenant(tenantId), AgentSkill.normalizeName(name));
    }

    @Override
    public AgentSkill createCustom(String tenantId, String markdown) {
        CurrentUser user = admin();
        String safeTenant = defaultTenant(tenantId);
        SkillMarkdownDocument document = parser.parse(markdown);
        if (repository.findSkill(safeTenant, document.name()).isPresent()) {
            throw new IllegalArgumentException("skill already exists");
        }
        Instant now = clock.instant();
        AgentSkill skill = new AgentSkill(
                document.name(),
                safeTenant,
                AgentSkillCategory.CUSTOM,
                AgentSkillSource.MANUAL,
                AgentSkillStatus.ACTIVE,
                true,
                null,
                document.description(),
                document.tags(),
                document.allowedTools(),
                operator(user),
                operator(user),
                now,
                now);
        AgentSkillRevision revision = newRevision(safeTenant, document, 1L, user, now);
        repository.saveRevision(revision);
        AgentSkill saved = skill.withRevision(revision.revisionId(), now, operator(user));
        repository.saveSkill(saved);
        indexSkill(saved, revision);
        return saved;
    }

    @Override
    public AgentSkill updateCustom(String tenantId, String name, String markdown) {
        CurrentUser user = admin();
        String safeTenant = defaultTenant(tenantId);
        AgentSkill existing = load(safeTenant, name);
        requireCustom(existing);
        SkillMarkdownDocument document = parser.parse(markdown);
        if (!existing.name().equals(document.name())) {
            throw new IllegalArgumentException("skill name cannot be changed");
        }
        Instant now = clock.instant();
        long revisionNo = repository.nextRevisionNo(safeTenant, existing.name());
        AgentSkillRevision revision = newRevision(safeTenant, document, revisionNo, user, now);
        repository.saveRevision(revision);
        AgentSkill updated = new AgentSkill(existing.name(), safeTenant, existing.category(), existing.source(),
                existing.status(), existing.enabled(), revision.revisionId(), document.description(), document.tags(),
                document.allowedTools(), existing.createdBy(), operator(user), existing.createdAt(), now);
        repository.saveSkill(updated);
        indexSkill(updated, revision);
        return updated;
    }

    @Override
    public AgentSkill enable(String tenantId, String name) {
        CurrentUser user = admin();
        AgentSkill skill = load(defaultTenant(tenantId), name).withEnabled(true, clock.instant(), operator(user));
        repository.saveSkill(skill);
        if (skill.latestRevisionId() != null && !skill.latestRevisionId().isBlank()) {
            repository.findRevision(skill.tenantId(), skill.latestRevisionId())
                    .ifPresent(revision -> indexSkill(skill, revision));
        }
        return skill;
    }

    @Override
    public AgentSkill disable(String tenantId, String name) {
        CurrentUser user = admin();
        AgentSkill skill = load(defaultTenant(tenantId), name).withEnabled(false, clock.instant(), operator(user));
        repository.saveSkill(skill);
        deleteSkillIndex(skill);
        return skill;
    }

    @Override
    public AgentSkill deleteCustom(String tenantId, String name) {
        CurrentUser user = admin();
        AgentSkill skill = load(defaultTenant(tenantId), name);
        requireCustom(skill);
        AgentSkill deleted = skill.deleted(clock.instant(), operator(user));
        repository.saveSkill(deleted);
        deleteSkillIndex(deleted);
        return deleted;
    }

    @Override
    public List<AgentSkillRevision> history(String tenantId, String name) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.listRevisions(defaultTenant(tenantId), AgentSkill.normalizeName(name));
    }

    @Override
    public AgentSkill rollbackCustom(String tenantId, String name, String revisionId) {
        CurrentUser user = admin();
        String safeTenant = defaultTenant(tenantId);
        AgentSkill skill = load(safeTenant, name);
        requireCustom(skill);
        AgentSkillRevision source = repository.findRevision(safeTenant, revisionId)
                .orElseThrow(() -> new IllegalArgumentException("revision not found"));
        return updateCustom(safeTenant, skill.name(), source.content());
    }

    @Override
    public AgentSkill install(String tenantId, String markdown) {
        return createCustom(tenantId, markdown);
    }

    public AgentSkill importPublic(String tenantId, String markdown, String operator) {
        String safeTenant = defaultTenant(tenantId);
        SkillMarkdownDocument document = parser.parse(markdown);
        Instant now = clock.instant();
        Optional<AgentSkill> existing = repository.findSkill(safeTenant, document.name());
        long revisionNo = repository.nextRevisionNo(safeTenant, document.name());
        String hash = contentHash(document.content());
        if (existing.isPresent() && existing.get().latestRevisionId() != null) {
            Optional<AgentSkillRevision> latest = repository.findRevision(safeTenant, existing.get().latestRevisionId());
            if (latest.isPresent() && latest.get().contentHash().equals(hash)) {
                return existing.get();
            }
        }
        AgentSkillRevision revision = newRevision(safeTenant, document, revisionNo, operator, now);
        repository.saveRevision(revision);
        AgentSkill skill = existing.orElseGet(() -> new AgentSkill(document.name(), safeTenant, AgentSkillCategory.PUBLIC,
                AgentSkillSource.BUILT_IN, AgentSkillStatus.ACTIVE, true, null, document.description(), document.tags(),
                document.allowedTools(), operator, operator, now, now));
        AgentSkill updated = new AgentSkill(skill.name(), safeTenant, AgentSkillCategory.PUBLIC, AgentSkillSource.BUILT_IN,
                AgentSkillStatus.ACTIVE, skill.enabled(), revision.revisionId(), document.description(), document.tags(),
                document.allowedTools(), skill.createdBy(), operator, skill.createdAt(), now);
        repository.saveSkill(updated);
        indexSkill(updated, revision);
        return updated;
    }

    private void indexSkill(AgentSkill skill, AgentSkillRevision revision) {
        if (vectorIndexService != null) {
            vectorIndexService.indexSkillAsync(skill, revision);
        }
    }

    private void deleteSkillIndex(AgentSkill skill) {
        if (vectorIndexService != null) {
            vectorIndexService.deleteIndexAsync(skill.tenantId(), skill.name());
        }
    }

    private AgentSkillRevision newRevision(String tenantId,
                                           SkillMarkdownDocument document,
                                           long revisionNo,
                                           CurrentUser user,
                                           Instant now) {
        return newRevision(tenantId, document, revisionNo, operator(user), now);
    }

    private AgentSkillRevision newRevision(String tenantId,
                                           SkillMarkdownDocument document,
                                           long revisionNo,
                                           String operator,
                                           Instant now) {
        SkillScanResult scanResult = scanner.scanContent(document.content());
        if (scanResult.decision() == SkillScanDecision.BLOCK) {
            throw new IllegalArgumentException("skill scan blocked: " + scanResult.reasons());
        }
        String hash = contentHash(document.content());
        return new AgentSkillRevision(
                "skillrev_" + document.name().replace('-', '_') + "_" + revisionNo,
                document.name(),
                tenantId,
                revisionNo,
                hash,
                document.content(),
                toJson(document.frontmatter()),
                scanResult.decision(),
                toJson(scanResult),
                operator,
                now);
    }

    private AgentSkill load(String tenantId, String name) {
        return repository.findSkill(tenantId, AgentSkill.normalizeName(name))
                .orElseThrow(() -> new IllegalArgumentException("skill not found"));
    }

    private void requireCustom(AgentSkill skill) {
        if (skill.category() != AgentSkillCategory.CUSTOM) {
            throw new IllegalArgumentException("public skills are read-only");
        }
    }

    private CurrentUser admin() {
        CurrentUser user = currentUserPort.requireCurrentUser();
        currentUserPort.requireRole(ADMIN_ROLE);
        return user;
    }

    private String operator(CurrentUser user) {
        return user == null ? "system" : String.valueOf(user.userId());
    }

    private String defaultTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? AgentDefinition.DEFAULT_TENANT_ID : tenantId.trim();
    }

    private String contentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash skill content", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize skill json", ex);
        }
    }
}
