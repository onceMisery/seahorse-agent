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

import com.miracle.ai.seahorse.agent.adapters.storage.local.LocalObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

/**
 * 对象存储适配器自动配置。
 *
 * <p>存储适配器独立成组，后续新增 OSS、MinIO 等实现时不再扩张原生适配器总配置。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentStorageAdapterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.storage", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    public LocalObjectStorageAdapter seahorseLocalObjectStorageAdapter(
            @Value("${seahorse-agent.adapters.storage.local.root:${java.io.tmpdir}/seahorse-agent-storage}")
            Path rootDirectory) {
        return new LocalObjectStorageAdapter(rootDirectory);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "software.amazon.awssdk.services.s3.S3Client",
            "com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter"
    })
    static class S3StorageAutoConfiguration {

        @Bean
        @ConditionalOnBean(S3Client.class)
        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.storage", name = "type",
                havingValue = "s3", matchIfMissing = true)
        @ConditionalOnMissingBean(ObjectStoragePort.class)
        public S3ObjectStorageAdapter seahorseS3ObjectStorageAdapter(S3Client s3Client) {
            return new S3ObjectStorageAdapter(s3Client);
        }
    }
}
