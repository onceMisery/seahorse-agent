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

package com.miracle.ai.seahorse.agent.adapters.vector.milvus;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import io.milvus.v2.client.MilvusClientV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus Skill 向量索引自动配置。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentSkillVectorIndexAutoConfiguration")
public class MilvusSkillVectorAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MilvusSkillVectorAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(MilvusClientV2.class)
    @ConditionalOnMissingBean(SkillVectorIndexRepositoryPort.class)
    public SkillVectorIndexRepositoryPort milvusSkillVectorIndexRepository(MilvusClientV2 milvusClient) {
        LOG.info("Configuring Milvus skill vector index repository");
        return new MilvusSkillVectorIndexAdapter(milvusClient);
    }
}
