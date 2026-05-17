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

package com.miracle.ai.seahorse.agent.ports.outbound.storage;

import java.io.InputStream;

/**
 * 对象存储端口。
 */
public interface ObjectStoragePort {

    /**
     * 确保存储桶或本地等价目录存在。
     *
     * <p>默认实现为 no-op，适配器可按自身存储语义覆盖。知识库创建链路通过该方法保持与旧系统
     * “创建知识库即创建存储空间”的行为兼容。
     *
     * @param bucketName 存储桶名称
     */
    default void ensureBucket(String bucketName) {
    }

    StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                        String contentType);

    /**
     * 可靠上传的默认语义与普通上传保持一致。
     *
     * <p>只有当具体适配器能够提供幂等、断点续传、内容校验或重试补偿等真实可靠性能力时，
     * 才需要覆盖该方法，避免端口层暴露“看似可靠但实际等价”的重复契约。
     */
    default StoredObject reliableUpload(String bucketName, InputStream content, long size, String originalFilename,
                                        String contentType) {
        return upload(bucketName, content, size, originalFilename, contentType);
    }

    InputStream openStream(String url);

    void deleteByUrl(String url);
}
