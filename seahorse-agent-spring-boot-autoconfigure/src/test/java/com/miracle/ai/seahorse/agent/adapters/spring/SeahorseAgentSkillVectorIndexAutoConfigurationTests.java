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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.chat.SkillSemanticMatcher;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentSkillVectorIndexAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentSkillVectorIndexAutoConfiguration.class))
            .withUserConfiguration(EmbeddingModelConfiguration.class);

    @Test
    void embeddingPortUsesConfiguredEmbeddingModelAndDimension() {
        contextRunner
                .withPropertyValues(
                        "seahorse.agent.adapters.ai.embedding-model=nomic-embed-text",
                        "seahorse.agent.adapters.ai.embedding-model-dimensions=")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingPort.class);
                    EmbeddingPort embeddingPort = context.getBean(EmbeddingPort.class);
                    assertThat(embeddingPort.modelName()).isEqualTo("nomic-embed-text");
                    assertThat(embeddingPort.dimension()).isEqualTo(768);
                });
    }

    @Test
    void shouldNotCreateSemanticMatcherWithNoopEmbeddingPort() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentSkillVectorIndexAutoConfiguration.class))
                .withUserConfiguration(VectorAndSkillRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EmbeddingPort.class);
                    assertThat(context).doesNotHaveBean(SkillSemanticMatcher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class EmbeddingModelConfiguration {

        @Bean
        EmbeddingModelPort embeddingModelPort() {
            return (modelId, text) -> List.of(1.0F, 0.0F);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class VectorAndSkillRepositoryConfiguration {

        @Bean
        SkillVectorIndexRepositoryPort skillVectorIndexRepositoryPort() {
            return SkillVectorIndexRepositoryPort.noop();
        }

        @Bean
        AgentSkillRepositoryPort agentSkillRepositoryPort() {
            return new AgentSkillRepositoryPort() {
                @Override
                public void saveSkill(AgentSkill skill) {
                }

                @Override
                public Optional<AgentSkill> findSkill(String tenantId, String name) {
                    return Optional.empty();
                }

                @Override
                public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
                    return new AgentSkillPage(List.of(), 0, size, current, 0);
                }

                @Override
                public void saveRevision(AgentSkillRevision revision) {
                }

                @Override
                public long nextRevisionNo(String tenantId, String skillName) {
                    return 1;
                }

                @Override
                public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
                    return Optional.empty();
                }

                @Override
                public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
                    return List.of();
                }

                @Override
                public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
                    return List.of();
                }

                @Override
                public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
                }
            };
        }
    }
}
