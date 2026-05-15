package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldQuality;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssueSeverity;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchemaMissingException;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MetadataValidatorNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "metadata_validator";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_KB_ID = "kbId";
    private static final String KEY_DOC_ID = "docId";
    private static final String KEY_EXTRACTOR_VERSION = "extractorVersion";
    private static final String KEY_METADATA_EXTRACTION_CONTEXT = "metadataExtractionContext";
    private static final String KEY_BACKFILL_JOB_ID = "backfillJobId";
    private static final String KEY_REQUIRE_SCHEMA = "requireSchema";
    private static final String KEY_REQUIRE_METADATA_SCHEMA = "requireMetadataSchema";
    private static final String EVENT_VALIDATION_COMPLETED = "metadata.validation.completed";

    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataExtractionResultRepositoryPort resultRepositoryPort;
    private final MetadataReviewQueuePort reviewQueuePort;
    private final MetadataQuarantinePort quarantinePort;
    private final MetadataCanonicalWritePort canonicalWritePort;
    private final ObservationPort observationPort;

    public MetadataValidatorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort,
                                        MetadataExtractionResultRepositoryPort resultRepositoryPort,
                                        MetadataReviewQueuePort reviewQueuePort,
                                        MetadataQuarantinePort quarantinePort,
                                        MetadataCanonicalWritePort canonicalWritePort) {
        this(schemaRegistryPort, resultRepositoryPort, reviewQueuePort, quarantinePort, canonicalWritePort, null);
    }

    public MetadataValidatorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort,
                                        MetadataExtractionResultRepositoryPort resultRepositoryPort,
                                        MetadataReviewQueuePort reviewQueuePort,
                                        MetadataQuarantinePort quarantinePort,
                                        MetadataCanonicalWritePort canonicalWritePort,
                                        ObservationPort observationPort) {
        this.schemaRegistryPort = Objects.requireNonNullElse(schemaRegistryPort, MetadataSchemaRegistryPort.empty());
        this.resultRepositoryPort = Objects.requireNonNullElse(resultRepositoryPort,
                MetadataExtractionResultRepositoryPort.noop());
        this.reviewQueuePort = Objects.requireNonNullElse(reviewQueuePort, MetadataReviewQueuePort.noop());
        this.quarantinePort = Objects.requireNonNullElse(quarantinePort, MetadataQuarantinePort.noop());
        this.canonicalWritePort = Objects.requireNonNullElse(canonicalWritePort, MetadataCanonicalWritePort.noop());
        this.observationPort = observationPort;
    }

    @Override
    public String name() {
        return NODE_TYPE;
    }

    @Override
    public String nodeType() {
        return NODE_TYPE;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        long startedAt = System.nanoTime();
        try {
            MetadataSchema schema = resolveSchema(safeContext, config);
            if (schema.empty() && requiresSchema(safeContext, config)) {
                throw new MetadataSchemaMissingException(
                        firstText(schema.tenantId(), firstText(setting(config, KEY_TENANT_ID),
                                metadataText(safeContext, KEY_TENANT_ID))),
                        firstText(schema.knowledgeBaseId(), firstText(setting(config, KEY_KB_ID),
                                metadataText(safeContext, KEY_KB_ID))));
            }
            ValidationIdentity identity = identity(safeContext, config, schema);
            MetadataValidationResult result = validate(schema, safeContext);
            safeContext.setMetadataValidationResult(result);
            safeContext.setMetadataIssues(result.issues());
            String resultId = persist(identity, schema, safeContext, result);
            if (MetadataValidationDecision.QUARANTINE.equals(result.decision())) {
                safeContext.setSkipIndexerWrite(true);
                quarantinePort.quarantine(new MetadataQuarantineItem(identity.tenantId(), identity.kbId(),
                        identity.docId(), safeContext.getTaskId(), NODE_TYPE, "METADATA_QUARANTINE",
                        firstIssue(result.issues()), snapshot(safeContext)));
                recordValidationEvent(identity, schema, safeContext, result, true, null, elapsedMillis(startedAt));
                return NodeResult.terminate("metadata quarantined");
            }
            if (MetadataValidationDecision.REVIEW_REQUIRED.equals(result.decision())) {
                reviewQueuePort.enqueue(new MetadataReviewItem(identity.tenantId(), identity.kbId(), identity.docId(),
                        firstText(resultId, safeContext.getTaskId()), "METADATA_REVIEW_REQUIRED", firstIssue(result.issues()),
                        result.acceptedMetadata(), reviewContext(safeContext, result)));
                // 需要人工复核的元数据不能直接写入 canonical metadata 或继续进入索引链路。
                safeContext.setSkipIndexerWrite(true);
                recordValidationEvent(identity, schema, safeContext, result, true, null, elapsedMillis(startedAt));
                return NodeResult.terminate("metadata review required");
            }
            mergeAcceptedMetadata(safeContext, result.acceptedMetadata());
            canonicalWritePort.writeDocumentMetadata(identity.docId(), result.acceptedMetadata());
            recordValidationEvent(identity, schema, safeContext, result, true, null, elapsedMillis(startedAt));
            return NodeResult.ok("metadata decision=" + result.decision());
        } catch (Exception ex) {
            recordValidationFailure(safeContext, config, ex, elapsedMillis(startedAt));
            return NodeResult.fail(ex);
        }
    }

    private void recordValidationEvent(ValidationIdentity identity,
                                       MetadataSchema schema,
                                       IngestionContext context,
                                       MetadataValidationResult result,
                                       boolean success,
                                       Exception ex,
                                       long durationMs) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", identity.tenantId());
            attributes.put("knowledgeBaseId", identity.kbId());
            attributes.put("schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()));
            attributes.put("extractorVersion", extractorVersion(context));
            attributes.put("decision", result == null ? "ERROR" : result.decision().name());
            attributes.put("acceptedFieldCount", Integer.toString(result == null ? 0 : result.acceptedMetadata().size()));
            attributes.put("rejectedFieldCount", Integer.toString(result == null ? 0 : result.rejectedMetadata().size()));
            attributes.put("issueCount", Integer.toString(result == null ? 0 : result.issues().size()));
            attributes.put("durationMs", Long.toString(durationMs));
            attributes.put("success", Boolean.toString(success));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            observationPort.recordEvent(new ObservationEvent(EVENT_VALIDATION_COMPLETED, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响元数据校验、复核或隔离主流程。
        }
    }

    private void recordValidationFailure(IngestionContext context, NodeConfig config, Exception ex, long durationMs) {
        ValidationIdentity identity = new ValidationIdentity(
                firstText(setting(config, KEY_TENANT_ID), metadataText(context, KEY_TENANT_ID)),
                firstText(setting(config, KEY_KB_ID), metadataText(context, KEY_KB_ID)),
                firstText(setting(config, KEY_DOC_ID), firstText(metadataText(context, KEY_DOC_ID), context.getTaskId())));
        recordValidationEvent(identity, null, context, null, false, ex, durationMs);
    }

    private MetadataValidationResult validate(MetadataSchema schema, IngestionContext context) {
        Map<String, Object> normalized = new LinkedHashMap<>(Objects.requireNonNullElse(
                context.getNormalizedMetadata(), Map.of()));
        Map<String, Object> accepted = new LinkedHashMap<>();
        Map<String, Object> rejected = new LinkedHashMap<>();
        List<MetadataIssue> issues = new ArrayList<>(Objects.requireNonNullElse(context.getMetadataIssues(),
                List.of()));
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            MetadataFieldDescriptor field = schema.find(entry.getKey()).orElse(null);
            if (field == null) {
                rejected.put(entry.getKey(), entry.getValue());
                issues.add(MetadataIssue.warn(entry.getKey(), NODE_TYPE, "UNREGISTERED_FIELD", "字段未注册"));
                continue;
            }
            accepted.put(field.fieldKey(), entry.getValue());
        }
        for (MetadataFieldDescriptor field : schema.fields()) {
            if (field.required() && !accepted.containsKey(field.fieldKey())) {
                issues.add(MetadataIssue.error(field.fieldKey(), NODE_TYPE, "REQUIRED_FIELD_MISSING", "必填字段缺失"));
            }
        }
        for (MetadataFieldQuality quality : Objects.requireNonNullElse(context.getMetadataFieldQualities(),
                List.<MetadataFieldQuality>of())) {
            MetadataFieldDescriptor field = schema.find(quality.fieldKey()).orElse(null);
            if (field == null || !accepted.containsKey(field.fieldKey())) {
                continue;
            }
            if (!trustedSource(field, quality.sourceType())) {
                issues.add(untrustedSourceIssue(field));
            }
            if (quality.confidence() < field.minConfidence()) {
                issues.add(MetadataIssue.warn(field.fieldKey(), NODE_TYPE, "LOW_CONFIDENCE", "字段置信度低于阈值"));
            }
        }
        MetadataValidationDecision decision = decision(issues);
        return new MetadataValidationResult(decision, issues, accepted, rejected);
    }

    private boolean trustedSource(MetadataFieldDescriptor field, String sourceType) {
        if (field.trustedSources().isEmpty()) {
            return true;
        }
        return field.trustedSources().stream()
                .anyMatch(source -> source.equalsIgnoreCase(Objects.requireNonNullElse(sourceType, "")));
    }

    private boolean strictTrustedSource(MetadataFieldDescriptor field) {
        // 权限/安全字段来源不可信时必须隔离；普通业务字段仍可进入人工复核。
        String normalized = field.fieldKey()
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return field.required()
                || field.backendMapping().guardOnly()
                || normalized.contains("acl")
                || normalized.contains("security")
                || normalized.contains("permission");
    }

    private MetadataIssue untrustedSourceIssue(MetadataFieldDescriptor field) {
        String message = "字段来源不在 Schema 可信来源内";
        if (strictTrustedSource(field)) {
            return MetadataIssue.error(field.fieldKey(), NODE_TYPE, "UNTRUSTED_METADATA_SOURCE", message);
        }
        return MetadataIssue.warn(field.fieldKey(), NODE_TYPE, "UNTRUSTED_METADATA_SOURCE", message);
    }

    private MetadataValidationDecision decision(List<MetadataIssue> issues) {
        boolean hasError = issues.stream().anyMatch(issue -> MetadataIssueSeverity.ERROR.equals(issue.severity()));
        if (hasError) {
            return MetadataValidationDecision.QUARANTINE;
        }
        boolean hasWarn = issues.stream().anyMatch(issue -> MetadataIssueSeverity.WARN.equals(issue.severity()));
        return hasWarn ? MetadataValidationDecision.REVIEW_REQUIRED : MetadataValidationDecision.ACCEPT;
    }

    private void mergeAcceptedMetadata(IngestionContext context, Map<String, Object> acceptedMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(context.getMetadata(), Map.of()));
        metadata.putAll(acceptedMetadata);
        metadata.put("acceptedMetadata", acceptedMetadata);
        context.setMetadata(metadata);
    }

    private String persist(ValidationIdentity identity,
                           MetadataSchema schema,
                           IngestionContext context,
                           MetadataValidationResult result) {
        return resultRepositoryPort.saveAndReturnId(new MetadataExtractionRecord(identity.tenantId(), identity.kbId(), identity.docId(),
                context.getTaskId(), schema.schemaVersion(), extractorVersion(context), result.decision(),
                context.getNormalizedMetadata(), result.acceptedMetadata(), context.getMetadataFieldQualities(),
                result.issues(), context.getMetadataCandidates()));
    }

    private String extractorVersion(IngestionContext context) {
        String candidateVersion = Objects.requireNonNullElse(context.getMetadataCandidates(), List.<com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate>of())
                .stream()
                .findFirst()
                .map(com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate::extractorVersion)
                .orElse("");
        // 无候选值的隔离/失败场景仍需保留任务上下文中的抽取器版本，便于审计和幂等重跑。
        return firstText(candidateVersion, metadataText(context, KEY_EXTRACTOR_VERSION));
    }

    private Map<String, Object> snapshot(IngestionContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("metadata", Objects.requireNonNullElse(context.getMetadata(), Map.of()));
        snapshot.put("normalizedMetadata", Objects.requireNonNullElse(context.getNormalizedMetadata(), Map.of()));
        snapshot.put("issues", Objects.requireNonNullElse(context.getMetadataIssues(), List.of()));
        return snapshot;
    }

    private Map<String, Object> reviewContext(IngestionContext context, MetadataValidationResult result) {
        Map<String, Object> reviewContext = new LinkedHashMap<>();
        // 复核上下文只用于管理端展示证据，避免污染可写回的 suggestedMetadata。
        reviewContext.put("issues", result.issues());
        reviewContext.put("fieldQualities", Objects.requireNonNullElse(context.getMetadataFieldQualities(), List.of()));
        reviewContext.put("rawCandidates", Objects.requireNonNullElse(context.getMetadataCandidates(), List.of()));
        reviewContext.put("rejectedMetadata", result.rejectedMetadata());
        addExtractionContext(context, reviewContext);
        return reviewContext;
    }

    private void addExtractionContext(IngestionContext context, Map<String, Object> reviewContext) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(KEY_METADATA_EXTRACTION_CONTEXT);
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return;
        }
        Map<String, Object> extractionContext = new LinkedHashMap<>();
        map.forEach((key, item) -> extractionContext.put(String.valueOf(key), item));
        reviewContext.put("extractionContext", extractionContext);
    }

    private MetadataSchema resolveSchema(IngestionContext context, NodeConfig config) {
        if (context.getMetadataSchema() != null) {
            return context.getMetadataSchema();
        }
        MetadataSchema schema = schemaRegistryPort.loadSchema(
                firstText(setting(config, KEY_TENANT_ID), metadataText(context, KEY_TENANT_ID)),
                firstText(setting(config, KEY_KB_ID), metadataText(context, KEY_KB_ID)));
        context.setMetadataSchema(schema);
        return schema;
    }

    private boolean requiresSchema(IngestionContext context, NodeConfig config) {
        // 历史回填必须绑定 Schema；普通入库仍允许通过配置逐步启用治理节点。
        return hasText(metadataText(context, KEY_BACKFILL_JOB_ID))
                || flag(setting(config, KEY_REQUIRE_SCHEMA))
                || flag(setting(config, KEY_REQUIRE_METADATA_SCHEMA))
                || flag(metadataText(context, KEY_REQUIRE_METADATA_SCHEMA));
    }

    private ValidationIdentity identity(IngestionContext context, NodeConfig config, MetadataSchema schema) {
        return new ValidationIdentity(
                firstText(schema.tenantId(), firstText(setting(config, KEY_TENANT_ID), metadataText(context, KEY_TENANT_ID))),
                firstText(schema.knowledgeBaseId(), firstText(setting(config, KEY_KB_ID), metadataText(context, KEY_KB_ID))),
                firstText(setting(config, KEY_DOC_ID), firstText(metadataText(context, KEY_DOC_ID), context.getTaskId())));
    }

    private String setting(NodeConfig config, String key) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode value = settings == null ? null : settings.get(key);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String metadataText(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private String firstIssue(List<MetadataIssue> issues) {
        return issues.stream().findFirst().map(MetadataIssue::message).orElse("");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean flag(String value) {
        return Boolean.parseBoolean(Objects.requireNonNullElse(value, "false"));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record ValidationIdentity(String tenantId, String kbId, String docId) {
    }
}
