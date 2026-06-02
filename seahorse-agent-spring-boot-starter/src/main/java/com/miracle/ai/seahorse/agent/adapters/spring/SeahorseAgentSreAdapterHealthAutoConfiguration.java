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
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentVectorAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
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
                environment.getProperty("seahorse-agent.adapters.vector.type", "milvus"),
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
                environment.getProperty("seahorse-agent.adapters.keyword-search.type", "jdbc"),
                false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "seahorseKeywordIndexSreHealthContributor")
    public SreHealthContributorPort seahorseKeywordIndexSreHealthContributor(
            ObjectProvider<KeywordIndexPort> keywordIndexPort,
            Environment environment) {
        String indexType = environment.getProperty("seahorse-agent.adapters.keyword-index.type", "jdbc");
        String mode = environment.getProperty("seahorse-agent.adapters.keyword-index.mode", "sync");
        return () -> runtimeAdapterItem(
                "keyword-index",
                keywordIndexPort.getIfAvailable(),
                "KeywordIndexPort",
                "seahorse-agent.adapters.keyword-index.type",
                indexType,
                false,
                "seahorse-agent.adapters.keyword-index.mode=" + mode);
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
}
