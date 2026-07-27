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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentS3StorageAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentS3StorageAutoConfiguration.class));

    @Test
    void shouldBindCanonicalS3PropertiesWhenCanonicalTypeActivatesAdapter() {
        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.storage.type=s3",
                        "seahorse-agent.adapters.storage.s3.endpoint=http://localhost:9000",
                        "seahorse-agent.adapters.storage.s3.region=cn-north-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    S3StorageProperties properties = context.getBean(S3StorageProperties.class);
                    assertThat(properties.getEndpoint()).isEqualTo("http://localhost:9000");
                    assertThat(properties.getRegion()).isEqualTo("cn-north-1");
                });
    }

    @Test
    void shouldPreferCanonicalS3PropertiesOverLegacyProperties() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.storage.type=s3",
                        "seahorse-agent.adapters.storage.type=s3",
                        "seahorse.agent.adapters.storage.s3.endpoint=http://legacy:9000",
                        "seahorse-agent.adapters.storage.s3.endpoint=http://canonical:9000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(S3StorageProperties.class).getEndpoint())
                            .isEqualTo("http://canonical:9000");
                });
    }
}
