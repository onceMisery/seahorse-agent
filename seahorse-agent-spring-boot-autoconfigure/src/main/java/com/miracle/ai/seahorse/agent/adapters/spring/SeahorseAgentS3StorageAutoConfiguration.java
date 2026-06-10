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

import com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * S3对象存储适配器自动配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({S3Client.class, S3ObjectStorageAdapter.class})
@ConditionalOnProperty(prefix = "seahorse.agent.adapters.storage", name = "type", havingValue = "s3")
@AutoConfigureAfter(SeahorseAgentStorageAdapterAutoConfiguration.class)
@EnableConfigurationProperties(S3StorageProperties.class)
public class SeahorseAgentS3StorageAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(S3Client.class)
    @ConditionalOnProperty(name = "seahorse.agent.adapters.storage.s3.endpoint")
    public S3Client seahorseS3Client(S3StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());
        builder.endpointOverride(URI.create(properties.getEndpoint()));
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(S3Client.class)
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    public ObjectStoragePort seahorseS3ObjectStorageAdapter(S3Client s3Client) {
        return new S3ObjectStorageAdapter(s3Client);
    }
}
