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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthContributorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentVectorAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class
})
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentSreAdapterHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "seahorseVectorStoreSreHealthContributor")
    public SreHealthContributorPort seahorseVectorStoreSreHealthContributor(
            ObjectProvider<VectorSearchPort> vectorSearchPort,
            Environment environment) {
        return () -> runtimeAdapterItem(
                "vector-store",
                vectorSearchPort.getIfAvailable(),
                "VectorSearchPort",
                "seahorse-agent.adapters.vector.type",
                resolveSeahorseAgentProperty(environment, "seahorse-agent.adapters.vector.type", "milvus"),
                true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "seahorseKeywordSearchSreHealthContributor")
    public SreHealthContributorPort seahorseKeywordSearchSreHealthContributor(
            ObjectProvider<KeywordSearchPort> keywordSearchPort,
            Environment environment) {
        return () -> runtimeAdapterItem(
                "keyword-search",
                keywordSearchPort.getIfAvailable(),
                "KeywordSearchPort",
                "seahorse-agent.adapters.keyword-search.type",
                resolveSeahorseAgentProperty(environment, "seahorse-agent.adapters.keyword-search.type", "jdbc"),
                false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "seahorseKeywordIndexSreHealthContributor")
    public SreHealthContributorPort seahorseKeywordIndexSreHealthContributor(
            ObjectProvider<KeywordIndexPort> keywordIndexPort,
            Environment environment) {
        String indexType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.keyword-index.type", "jdbc");
        String mode = resolveSeahorseAgentProperty(environment, "seahorse-agent.adapters.keyword-index.mode", "sync");
        return () -> runtimeAdapterItem(
                "keyword-index",
                keywordIndexPort.getIfAvailable(),
                "KeywordIndexPort",
                "seahorse-agent.adapters.keyword-index.type",
                indexType,
                false,
                "seahorse-agent.adapters.keyword-index.mode=" + mode);
    }

    @Bean
    @ConditionalOnMissingBean(name = "seahorseAiModelSreHealthContributor")
    public SreHealthContributorPort seahorseAiModelSreHealthContributor(
            ObjectProvider<ChatModelPort> chatModelPort,
            ObjectProvider<StreamingChatModelPort> streamingChatModelPort,
            Environment environment) {
        return () -> {
            Object chat = chatModelPort.getIfAvailable();
            Object streaming = streamingChatModelPort.getIfAvailable();
            String configuredType = resolveSeahorseAgentProperty(environment,
                    "seahorse-agent.adapters.ai.type", "openai-compatible");
            String evidence = "seahorse-agent.adapters.ai.type=" + configuredType;
            if (chat != null && streaming != null) {
                return new SreHealthItem("ai-model", SreHealthStatus.GREEN,
                        "ChatModelPort and StreamingChatModelPort are available",
                        evidence + "; bean=" + chat.getClass().getName());
            }
            if (chat != null || streaming != null) {
                return new SreHealthItem("ai-model", SreHealthStatus.WARN,
                        "Only partial AI model adapter is available", evidence);
            }
            return new SreHealthItem("ai-model", SreHealthStatus.WARN,
                    "No ChatModelPort or StreamingChatModelPort bean available", evidence);
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "seahorseObjectStorageSreHealthContributor")
    public SreHealthContributorPort seahorseObjectStorageSreHealthContributor(
            ObjectProvider<ObjectStoragePort> objectStoragePort,
            Environment environment) {
        return () -> runtimeAdapterItem(
                "object-storage",
                objectStoragePort.getIfAvailable(),
                "ObjectStoragePort",
                "seahorse-agent.adapters.storage.type",
                resolveSeahorseAgentProperty(environment, "seahorse-agent.adapters.storage.type", "local"),
                false);
    }

    private static SreHealthItem runtimeAdapterItem(
            String contributorName,
            Object adapter,
            String portName,
            String propertyName,
            String configuredType,
            boolean warnOnNoop) {
        return runtimeAdapterItem(contributorName, adapter, portName, propertyName, configuredType, warnOnNoop, null);
    }

    private static SreHealthItem runtimeAdapterItem(
            String contributorName,
            Object adapter,
            String portName,
            String propertyName,
            String configuredType,
            boolean warnOnNoop,
            String extraEvidence) {
        String safeType = textOr(configuredType, "unspecified");
        String propertyEvidence = propertyName + "=" + safeType;
        String evidenceRef = extraEvidence == null ? propertyEvidence : propertyEvidence + "; " + extraEvidence;
        if (adapter == null) {
            return new SreHealthItem(contributorName, SreHealthStatus.WARN,
                    "No " + portName + " bean is available for configured adapter type " + safeType,
                    evidenceRef);
        }

        String adapterClass = adapter.getClass().getName();
        SreHealthStatus status = warnOnNoop && isNoop(safeType, adapterClass)
                ? SreHealthStatus.WARN
                : SreHealthStatus.GREEN;
        String message = portName + " runtime adapter is " + adapterClass;
        return new SreHealthItem(contributorName, status, message, evidenceRef + "; bean=" + adapterClass);
    }

    private static boolean isNoop(String configuredType, String adapterClass) {
        return "noop".equals(configuredType.toLowerCase(Locale.ROOT))
                || adapterClass.toLowerCase(Locale.ROOT).contains(".noop")
                || adapterClass.toLowerCase(Locale.ROOT).contains("noop");
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String resolveSeahorseAgentProperty(Environment environment, String canonicalName, String fallback) {
        String value = environment.getProperty(canonicalName);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(canonicalName.replace("seahorse-agent.", "seahorse.agent."));
        }
        return textOr(value, fallback);
    }
}
