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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final class CapturingTemplateRepository implements RetrievalStrategyTemplateRepositoryPort {

        private final List<String> upsertKbIds = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();

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
    }
}
