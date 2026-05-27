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

import com.miracle.ai.seahorse.agent.kernel.application.agent.research.CitationVerifier;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.ExtractEvidenceStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.FetchStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.PlanStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.ResearchRunOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.ResearchStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.SearchStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.SynthesizeStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.VerifyCitationsStepHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.research.WriteReportStepHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTaskQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Research Web Agent 自动配置。
 *
 * <p>注册研究编排器及其 7 个步骤处理器，仅在 DurableTaskQueuePort 和 ChatModelPort 可用时生效。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentKernelAutoConfiguration.class,
        SeahorseAgentKernelAgentAutoConfiguration.class,
        SeahorseAgentRegistryRepositoryAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelResearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CitationVerifier seahorseCitationVerifier() {
        return new CitationVerifier();
    }

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public PlanStepHandler seahorsePlanStepHandler(ChatModelPort chatModel) {
        return new PlanStepHandler(chatModel);
    }

    @Bean
    @ConditionalOnBean(WebSearchPort.class)
    @ConditionalOnMissingBean
    public SearchStepHandler seahorseSearchStepHandler(WebSearchPort webSearch) {
        return new SearchStepHandler(webSearch);
    }

    @Bean
    @ConditionalOnBean(WebFetchPort.class)
    @ConditionalOnMissingBean
    public FetchStepHandler seahorseFetchStepHandler(WebFetchPort webFetch) {
        return new FetchStepHandler(webFetch);
    }

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public ExtractEvidenceStepHandler seahorseExtractEvidenceStepHandler(ChatModelPort chatModel) {
        return new ExtractEvidenceStepHandler(chatModel);
    }

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public SynthesizeStepHandler seahorseSynthesizeStepHandler(ChatModelPort chatModel) {
        return new SynthesizeStepHandler(chatModel);
    }

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public WriteReportStepHandler seahorseWriteReportStepHandler(ChatModelPort chatModel) {
        return new WriteReportStepHandler(chatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public VerifyCitationsStepHandler seahorseVerifyCitationsStepHandler(CitationVerifier verifier) {
        return new VerifyCitationsStepHandler(verifier);
    }

    @Bean
    @ConditionalOnBean(DurableTaskQueuePort.class)
    @ConditionalOnMissingBean
    public ResearchRunOrchestrator seahorseResearchRunOrchestrator(
            DurableTaskQueuePort taskQueue,
            ObjectProvider<AgentRunEventBufferPort> eventBuffer,
            ObjectProvider<ResearchStepHandler> stepHandlers) {
        return new ResearchRunOrchestrator(
                taskQueue,
                eventBuffer.getIfAvailable(AgentRunEventBufferPort::noop),
                stepHandlers.orderedStream().toList());
    }
}
