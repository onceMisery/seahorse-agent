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

import com.miracle.ai.seahorse.agent.adapters.ai.openai.EmbeddingPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillVectorIndexService;
import com.miracle.ai.seahorse.agent.kernel.application.chat.SkillSemanticMatcher;
import com.miracle.ai.seahorse.agent.kernel.config.EmbeddingModelDimensions;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Skill 向量索引和语义匹配自动配置。
 *
 * <p>依赖组件：
 * <ul>
 *   <li>EmbeddingModelPort：Embedding 模型</li>
 *   <li>AgentSkillRepositoryPort：Skill 仓储</li>
 *   <li>SkillVectorIndexRepositoryPort：向量索引仓储（可选，由具体的向量数据库适配器提供）</li>
 * </ul>
 *
 * <p>配置开关：
 * <pre>
 * seahorse.agent.skill.vector-index.enabled=true  # 默认 true
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentKernelAutoConfiguration.class
})
@ConditionalOnProperty(
        prefix = "seahorse.agent.skill.vector-index",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SeahorseAgentSkillVectorIndexAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseAgentSkillVectorIndexAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(EmbeddingModelPort.class)
    @ConditionalOnMissingBean
    public EmbeddingPort embeddingPort(EmbeddingModelPort embeddingModel, Environment environment) {
        // 默认配置：text-embedding-3-small 模型，1536 维
        String modelName = configuredEmbeddingModel(environment);
        int dimension = EmbeddingModelDimensions.resolveOrThrow(
                bindString(environment, "seahorse.agent.adapters.vector.dimension"),
                modelName,
                bindString(environment, "seahorse.agent.adapters.ai.embedding-model-dimensions"));

        LOG.info("Configuring EmbeddingPort adapter (model: {}, dimension: {})", modelName, dimension);
        return new EmbeddingPortAdapter(embeddingModel, modelName, dimension);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingPort noopEmbeddingPort() {
        LOG.warn("No embedding model available, using noop embedding port");
        return EmbeddingPort.noop();
    }

    @Bean
    @ConditionalOnBean({EmbeddingPort.class, SkillVectorIndexRepositoryPort.class, AgentSkillRepositoryPort.class})
    @ConditionalOnMissingBean
    public SkillVectorIndexService skillVectorIndexService(
            EmbeddingPort embeddingPort,
            SkillVectorIndexRepositoryPort vectorRepository,
            AgentSkillRepositoryPort skillRepository) {

        SkillVectorIndexService service = new SkillVectorIndexService(
                embeddingPort,
                vectorRepository,
                skillRepository
        );

        // 初始化向量集合
        try {
            service.initializeCollection();
        } catch (Exception ex) {
            LOG.error("Failed to initialize skill vector collection", ex);
        }

        LOG.info("Skill vector index service initialized");
        return service;
    }

    @Bean
    @ConditionalOnBean({EmbeddingPort.class, SkillVectorIndexRepositoryPort.class, AgentSkillRepositoryPort.class})
    @ConditionalOnMissingBean
    public SkillSemanticMatcher skillSemanticMatcher(
            EmbeddingPort embeddingPort,
            SkillVectorIndexRepositoryPort vectorRepository,
            AgentSkillRepositoryPort skillRepository) {

        LOG.info("Configuring semantic skill matcher");
        return new SkillSemanticMatcher(embeddingPort, vectorRepository, skillRepository);
    }

    private static String configuredEmbeddingModel(Environment environment) {
        String modelName = bindString(environment, "seahorse.agent.adapters.ai.embedding-model");
        return modelName.isBlank() ? "text-embedding-3-small" : modelName;
    }

    private static String bindString(Environment environment, String name) {
        String value = Binder.get(environment).bind(name, String.class).orElse("");
        if (!value.isBlank()) {
            return value;
        }
        return environment.getProperty(name.replace("seahorse.agent", "seahorse-agent"), "");
    }
}
