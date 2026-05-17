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

import com.miracle.ai.seahorse.agent.adapters.local.ClasspathPromptTemplateAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalDocumentFetcherAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIngestionNodeLogAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentGuidanceAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentResolutionAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalQueryRewriteAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRagPromptAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRetrievalContextFormatAdapter;
import com.miracle.ai.seahorse.agent.adapters.parser.tika.TikaDocumentParserAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 本地基础适配器自动配置。
 *
 * <p>这些 Bean 不依赖外部 SDK，主要承担默认开发体验；独立配置后，主 native 配置只负责聚合兼容入口。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentLocalAdapterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.parser", name = "type", havingValue = "tika",
            matchIfMissing = true)
    @ConditionalOnMissingBean(DocumentParserPort.class)
    public TikaDocumentParserAdapter seahorseTikaDocumentParserAdapter() {
        return new TikaDocumentParserAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.rewrite", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(QueryRewritePort.class)
    public LocalQueryRewriteAdapter seahorseLocalQueryRewriteAdapter() {
        return new LocalQueryRewriteAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.intent", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(IntentResolutionPort.class)
    public LocalIntentResolutionAdapter seahorseLocalIntentResolutionAdapter() {
        return new LocalIntentResolutionAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.guidance", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(IntentGuidancePort.class)
    public LocalIntentGuidanceAdapter seahorseLocalIntentGuidanceAdapter() {
        return new LocalIntentGuidanceAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.prompt", name = "type", havingValue = "classpath",
            matchIfMissing = true)
    @ConditionalOnMissingBean(PromptTemplatePort.class)
    public ClasspathPromptTemplateAdapter seahorseClasspathPromptTemplateAdapter() {
        return new ClasspathPromptTemplateAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.rag-prompt", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(RagPromptPort.class)
    public LocalRagPromptAdapter seahorseLocalRagPromptAdapter() {
        return new LocalRagPromptAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.retrieval-context", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(RetrievalContextFormatPort.class)
    public LocalRetrievalContextFormatAdapter seahorseLocalRetrievalContextFormatAdapter() {
        return new LocalRetrievalContextFormatAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(IngestionNodeLogPort.class)
    public LocalIngestionNodeLogAdapter seahorseLocalIngestionNodeLogAdapter() {
        return new LocalIngestionNodeLogAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(SchedulerPort.class)
    public SpringCronSchedulerPort seahorseSpringCronSchedulerPort() {
        return new SpringCronSchedulerPort();
    }

    @Bean
    @ConditionalOnMissingBean(LocalDocumentFetcherAdapter.class)
    public LocalDocumentFetcherAdapter seahorseLocalDocumentFetcherAdapter(
            ObjectProvider<ObjectStoragePort> objectStoragePort) {
        return new LocalDocumentFetcherAdapter(objectStoragePort.getIfAvailable());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CompositeDocumentFetcherPort.class)
    public CompositeDocumentFetcherPort seahorseCompositeDocumentFetcherPort(
            List<DocumentFetcherPort> documentFetcherPorts) {
        return new CompositeDocumentFetcherPort(documentFetcherPorts);
    }
}
