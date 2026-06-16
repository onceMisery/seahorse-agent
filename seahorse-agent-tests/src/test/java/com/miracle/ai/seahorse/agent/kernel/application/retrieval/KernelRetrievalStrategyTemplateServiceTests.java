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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyPromotionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationComparisonRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelRetrievalStrategyTemplateServiceTests {

    @Test
    void shouldExposeDefaultKnowledgeBaseRetrievalTemplates() {
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService();

        List<RetrievalStrategyTemplate> templates = service.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank");
        assertThat(templates.get(0).options().enableKeyword()).isFalse();
        assertThat(templates.get(1).options().enableKeyword()).isTrue();
        assertThat(templates.get(1).options().channelSettings()).containsKey("channelWeights");
        assertThat(templates.get(1).options().channelSettings().get("channelWeights").toString())
                .contains("IntentDirectedSearch");
        assertThat(templates.get(2).options().enableRerank()).isTrue();
        assertThat(templates.get(2).options().rerankModel()).isEmpty();
    }

    @Test
    void shouldOverrideDefaultTemplateByTemplateKey() {
        RetrievalStrategyTemplate override = new RetrievalStrategyTemplate(
                "hybrid_rrf",
                "知识库定制混合检索",
                "覆盖默认 RRF 参数",
                RetrievalOptions.builder()
                        .finalTopK(8)
                        .enableVector(true)
                        .enableIntentDirected(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .channelSettings(Map.of("rrfK", 80))
                        .build());
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(
                kbId -> List.of(override));

        List<RetrievalStrategyTemplate> templates = service.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank");
        assertThat(templates.get(1).displayName()).isEqualTo("知识库定制混合检索");
        assertThat(templates.get(1).options().finalTopK()).isEqualTo(8);
        assertThat(templates.get(1).options().channelSettings()).containsEntry("rrfK", 80);
    }

    @Test
    void shouldAppendNewRepositoryTemplatesAfterDefaults() {
        RetrievalStrategyTemplate custom = new RetrievalStrategyTemplate(
                "keyword_precise",
                "关键词精确优先",
                "知识库自定义模板",
                RetrievalOptions.builder()
                        .finalTopK(3)
                        .enableVector(false)
                        .enableIntentDirected(false)
                        .enableKeyword(true)
                        .enableRrf(false)
                        .enableRerank(false)
                        .build());
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(
                kbId -> List.of(custom));

        List<RetrievalStrategyTemplate> templates = service.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank", "keyword_precise");
        assertThat(templates.get(3).options().enableKeyword()).isTrue();
    }

    @Test
    void shouldIgnoreNullOrBlankRepositoryTemplates() {
        RetrievalStrategyTemplateRepositoryPort repositoryPort = kbId -> Arrays.asList(
                null,
                new RetrievalStrategyTemplate(" ", "非法模板", "空 key 会被忽略", RetrievalOptions.defaults(5)));
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(repositoryPort);

        List<RetrievalStrategyTemplate> templates = service.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank");
    }

    @Test
    void shouldTreatNullRepositoryResultAsEmpty() {
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(kbId -> null);

        List<RetrievalStrategyTemplate> templates = service.listTemplates("kb-1");

        assertThat(templates).extracting(RetrievalStrategyTemplate::templateKey)
                .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank");
    }

    @Test
    void shouldDelegateTemplateUpsertAndDeleteToRepository() {
        CapturingTemplateRepository repository = new CapturingTemplateRepository();
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(repository);
        RetrievalStrategyTemplatePayload payload = new RetrievalStrategyTemplatePayload(
                "keyword_precise",
                "关键词精确优先",
                "优先使用关键词通道",
                RetrievalOptions.builder().finalTopK(3).enableKeyword(true).build(),
                20,
                true);

        RetrievalStrategyTemplate saved = service.upsertTemplate("kb-1", payload);
        boolean deleted = service.deleteTemplate("kb-1", "keyword_precise");

        assertThat(saved.templateKey()).isEqualTo("keyword_precise");
        assertThat(repository.upsertKbIds).containsExactly("kb-1");
        assertThat(repository.deletedKeys).containsExactly("kb-1:keyword_precise");
        assertThat(deleted).isTrue();
    }

    @Test
    void shouldPromoteWinningComparisonTemplateAndWriteAudit() {
        CapturingTemplateRepository repository = new CapturingTemplateRepository();
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(
                repository,
                comparisonRepository(passingComparison("candidate")),
                auditLedger(auditRepository),
                Clock.fixed(Instant.parse("2026-06-15T01:02:03Z"), ZoneOffset.UTC));
        RetrievalStrategyTemplatePayload payload = new RetrievalStrategyTemplatePayload(
                "candidate",
                "Candidate hybrid",
                "Promoted after comparison comparison-1",
                RetrievalOptions.builder().finalTopK(8).enableKeyword(true).enableRrf(true).build(),
                1,
                true);

        RetrievalStrategyTemplate promoted = service.promoteTemplateFromComparison("kb-1",
                new RetrievalStrategyPromotionCommand(
                        "tenant-a",
                        "dataset-1",
                        "comparison-1",
                        "admin-a",
                        payload,
                        "daily regression passed"));

        assertThat(promoted.templateKey()).isEqualTo("candidate");
        assertThat(promoted.recommended()).isTrue();
        assertThat(repository.promotedPayloads).containsExactly(payload);
        assertThat(auditRepository.saved)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.tenantId()).isEqualTo("tenant-a");
                    assertThat(event.eventType()).isEqualTo(AuditEventType.RETRIEVAL_STRATEGY_PROMOTED);
                    assertThat(event.actorId()).isEqualTo("admin-a");
                    assertThat(event.resourceType()).isEqualTo("RETRIEVAL_STRATEGY_TEMPLATE");
                    assertThat(event.resourceId()).isEqualTo("kb-1:candidate");
                    assertThat(event.redactedPayload()).contains("\"comparisonId\":\"comparison-1\"");
                    assertThat(event.redactedPayload()).contains("\"datasetId\":\"dataset-1\"");
                });
    }

    @Test
    void shouldRejectPromotionWhenComparisonWinnerDoesNotMatchTemplate() {
        CapturingTemplateRepository repository = new CapturingTemplateRepository();
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(
                repository,
                comparisonRepository(passingComparison("other")),
                auditLedger(auditRepository),
                Clock.fixed(Instant.parse("2026-06-15T01:02:03Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.promoteTemplateFromComparison("kb-1",
                new RetrievalStrategyPromotionCommand(
                        "tenant-a",
                        "dataset-1",
                        "comparison-1",
                        "admin-a",
                        payload("candidate", 8),
                        "winner mismatch")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("winner");
        assertThat(repository.promotedPayloads).isEmpty();
        assertThat(auditRepository.saved).isEmpty();
    }

    @Test
    void shouldRejectPromotionWhenWinnerRegressesAgainstBaseline() {
        CapturingTemplateRepository repository = new CapturingTemplateRepository();
        KernelRetrievalStrategyTemplateService service = new KernelRetrievalStrategyTemplateService(
                repository,
                comparisonRepository(regressingComparison()),
                null,
                Clock.fixed(Instant.parse("2026-06-15T01:02:03Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.promoteTemplateFromComparison("kb-1",
                new RetrievalStrategyPromotionCommand(
                        "tenant-a",
                        "dataset-1",
                        "comparison-1",
                        "admin-a",
                        payload("candidate", 8),
                        "recall regressed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("promotion gates");
        assertThat(repository.promotedPayloads).isEmpty();
    }

    private RetrievalEvaluationComparisonRepositoryPort comparisonRepository(
            RetrievalEvaluationComparisonRecord comparison) {
        return new RetrievalEvaluationComparisonRepositoryPort() {
            @Override
            public RetrievalEvaluationComparisonRecord saveComparison(String knowledgeBaseId, String datasetId,
                                                                     RetrievalEvaluationComparisonReport report) {
                return comparison;
            }

            @Override
            public List<RetrievalEvaluationComparisonSummary> listComparisons(String knowledgeBaseId, String datasetId,
                                                                              int limit) {
                return List.of(comparison.summary());
            }

            @Override
            public Optional<RetrievalEvaluationComparisonRecord> findComparison(String knowledgeBaseId,
                                                                                String datasetId,
                                                                                String comparisonId) {
                if (comparison.knowledgeBaseId().equals(knowledgeBaseId)
                        && comparison.datasetId().equals(datasetId)
                        && comparison.comparisonId().equals(comparisonId)) {
                    return Optional.of(comparison);
                }
                return Optional.empty();
            }
        };
    }

    private RetrievalEvaluationComparisonRecord passingComparison(String winner) {
        return new RetrievalEvaluationComparisonRecord(
                "comparison-1",
                "kb-1",
                "dataset-1",
                new RetrievalEvaluationComparisonReport(
                        "baseline",
                        winner,
                        List.of(
                                report("baseline", 0.80D, 0.70D, 0.60D, 0.20D),
                                report("candidate", 0.90D, 0.80D, 0.70D, 0.10D),
                                report("other", 0.91D, 0.81D, 0.71D, 0.10D)),
                        List.of()),
                Instant.EPOCH);
    }

    private RetrievalEvaluationComparisonRecord regressingComparison() {
        return new RetrievalEvaluationComparisonRecord(
                "comparison-1",
                "kb-1",
                "dataset-1",
                new RetrievalEvaluationComparisonReport(
                        "baseline",
                        "candidate",
                        List.of(
                                report("baseline", 0.90D, 0.70D, 0.60D, 0.20D),
                                report("candidate", 0.80D, 0.80D, 0.70D, 0.10D)),
                        List.of()),
                Instant.EPOCH);
    }

    private RetrievalEvaluationReport report(String strategyName,
                                             double recall,
                                             double precision,
                                             double mrr,
                                             double emptyRecallRate) {
        return new RetrievalEvaluationReport(
                strategyName, 8, 3, 3, recall, precision, mrr, mrr, emptyRecallRate, 10D, 10D, List.of());
    }

    private RetrievalStrategyTemplatePayload payload(String templateKey, int finalTopK) {
        return new RetrievalStrategyTemplatePayload(
                templateKey,
                "Template " + templateKey,
                "desc-" + templateKey,
                RetrievalOptions.builder().finalTopK(finalTopK).enableKeyword(true).build(),
                10,
                true);
    }

    private KernelAuditLedgerService auditLedger(RecordingAuditEventRepository repository) {
        return new KernelAuditLedgerService(
                repository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
    }

    private static final class CapturingTemplateRepository implements RetrievalStrategyTemplateRepositoryPort {

        private final List<String> upsertKbIds = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private final List<RetrievalStrategyTemplatePayload> promotedPayloads = new ArrayList<>();

        @Override
        public List<RetrievalStrategyTemplate> listTemplates(String kbId) {
            return List.of();
        }

        @Override
        public RetrievalStrategyTemplate upsertTemplate(String kbId, RetrievalStrategyTemplatePayload payload) {
            upsertKbIds.add(kbId);
            return payload.toTemplate();
        }

        @Override
        public boolean deleteTemplate(String kbId, String templateKey) {
            deletedKeys.add(kbId + ":" + templateKey);
            return true;
        }

        @Override
        public RetrievalStrategyTemplate promoteRecommendedTemplate(String kbId,
                                                                    RetrievalStrategyTemplatePayload payload) {
            promotedPayloads.add(payload);
            return new RetrievalStrategyTemplate(
                    payload.templateKey(),
                    payload.displayName(),
                    payload.description(),
                    payload.options(),
                    true);
        }
    }

    private static final class RecordingAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> saved = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            saved.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return saved.stream().filter(event -> event.auditId().equals(auditId)).findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(saved, saved.size(), saved.size(), 1, 1);
        }
    }
}
