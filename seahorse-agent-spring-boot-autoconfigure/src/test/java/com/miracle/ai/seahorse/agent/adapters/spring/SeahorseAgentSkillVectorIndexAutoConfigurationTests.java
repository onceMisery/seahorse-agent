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

import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import java.util.List;
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

    @Configuration(proxyBeanMethods = false)
    static class EmbeddingModelConfiguration {

        @Bean
        EmbeddingModelPort embeddingModelPort() {
            return (modelId, text) -> List.of(1.0F, 0.0F);
        }
    }
}
