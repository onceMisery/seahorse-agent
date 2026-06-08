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
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentSreAdapterHealthAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentSreAdapterHealthAutoConfiguration.class));

    @Test
    void reportsRuntimeAdapterContributorsWhenPortsArePresent() {
        contextRunner
                .withUserConfiguration(AdapterPortConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.adapters.vector.type=pgvector",
                        "seahorse-agent.adapters.keyword-search.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-index.type=elasticsearch")
                .run(context -> {
                    Collection<SreHealthContributorPort> contributors =
                            context.getBeansOfType(SreHealthContributorPort.class).values();

                    assertThat(contributors).hasSize(5);
                    assertThat(items(contributors))
                            .anySatisfy(item -> {
                                assertThat(item.contributorName()).isEqualTo("vector-store");
                                assertThat(item.status()).isEqualTo(SreHealthStatus.GREEN);
                                assertThat(item.message()).contains("VectorSearchPort");
                                assertThat(item.evidenceRef())
                                        .contains("seahorse-agent.adapters.vector.type=pgvector");
                            })
                            .anySatisfy(item -> {
                                assertThat(item.contributorName()).isEqualTo("keyword-search");
                                assertThat(item.status()).isEqualTo(SreHealthStatus.GREEN);
                                assertThat(item.evidenceRef())
                                        .contains("seahorse-agent.adapters.keyword-search.type=elasticsearch");
                            })
                            .anySatisfy(item -> {
                                assertThat(item.contributorName()).isEqualTo("keyword-index");
                                assertThat(item.status()).isEqualTo(SreHealthStatus.GREEN);
                                assertThat(item.evidenceRef())
                                        .contains("seahorse-agent.adapters.keyword-index.type=elasticsearch");
                            })
                            .anySatisfy(item -> {
                                assertThat(item.contributorName()).isEqualTo("ai-model");
                                assertThat(item.status()).isEqualTo(SreHealthStatus.GREEN);
                                assertThat(item.message()).contains("ChatModelPort");
                            })
                            .anySatisfy(item -> {
                                assertThat(item.contributorName()).isEqualTo("object-storage");
                                assertThat(item.status()).isEqualTo(SreHealthStatus.GREEN);
                                assertThat(item.message()).contains("ObjectStoragePort");
                            });
                });
    }

    @Test
    void reportsMissingAdaptersAsWarningsInsteadOfInventingGreenHealth() {
        contextRunner.run(context -> {
            Collection<SreHealthContributorPort> contributors =
                    context.getBeansOfType(SreHealthContributorPort.class).values();

            assertThat(contributors).hasSize(5);
            assertThat(items(contributors))
                    .allSatisfy(item -> assertThat(item.status()).isEqualTo(SreHealthStatus.WARN))
                    .anySatisfy(item -> {
                        assertThat(item.contributorName()).isEqualTo("vector-store");
                        assertThat(item.message()).contains("No VectorSearchPort bean");
                    })
                    .anySatisfy(item -> {
                        assertThat(item.contributorName()).isEqualTo("keyword-search");
                        assertThat(item.message()).contains("No KeywordSearchPort bean");
                    })
                    .anySatisfy(item -> {
                        assertThat(item.contributorName()).isEqualTo("keyword-index");
                        assertThat(item.message()).contains("No KeywordIndexPort bean");
                    })
                    .anySatisfy(item -> {
                        assertThat(item.contributorName()).isEqualTo("ai-model");
                        assertThat(item.message()).contains("No ChatModelPort or StreamingChatModelPort bean");
                    })
                    .anySatisfy(item -> {
                        assertThat(item.contributorName()).isEqualTo("object-storage");
                        assertThat(item.message()).contains("No ObjectStoragePort bean");
                    });
        });
    }

    private static List<SreHealthItem> items(Collection<SreHealthContributorPort> contributors) {
        return contributors.stream()
                .map(SreHealthContributorPort::current)
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    static class AdapterPortConfiguration {

        @Bean
        VectorSearchPort vectorSearchPort() {
            return request -> List.of();
        }

        @Bean
        KeywordSearchPort keywordSearchPort() {
            return request -> List.of();
        }

        @Bean
        KeywordIndexPort keywordIndexPort() {
            return KeywordIndexPort.noop();
        }

        @Bean
        ChatModelPort chatModelPort() {
            return ChatModelPort.noop();
        }

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return StreamingChatModelPort.noop();
        }

        @Bean
        ObjectStoragePort objectStoragePort() {
            return new ObjectStoragePort() {
                @Override
                public StoredObject upload(String bucketName, InputStream content, long size,
                                           String originalFilename, String contentType) {
                    return new StoredObject("test-url", "application/octet-stream", 0L, "test.bin");
                }

                @Override
                public InputStream openStream(String url) {
                    return InputStream.nullInputStream();
                }

                @Override
                public void deleteByUrl(String url) {
                }
            };
        }
    }
}
