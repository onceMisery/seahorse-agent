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

package com.miracle.ai.seahorse.agent.adapters.storage.s3;

import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * S3 对象存储 adapter。
 */
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private static final String URL_PREFIX = "s3://";

    private final S3Client s3Client;

    public S3ObjectStorageAdapter(S3Client s3Client) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
    }

    @Override
    public void ensureBucket(String bucketName) {
        String bucket = requireText(bucketName, "bucketName");
        try {
            s3Client.createBucket(builder -> builder.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // 已由当前账户持有时满足幂等创建语义。
        } catch (BucketAlreadyExistsException ex) {
            throw new IllegalStateException("存储桶名称已被占用：" + bucket, ex);
        }
    }

    @Override
    public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                               String contentType) {
        return putObject(bucketName, content, size, originalFilename, contentType);
    }

    @Override
    public StoredObject reliableUpload(String bucketName, InputStream content, long size, String originalFilename,
                                       String contentType) {
        return putObject(bucketName, content, size, originalFilename, contentType);
    }

    @Override
    public InputStream openStream(String url) {
        S3Location location = parseUrl(url);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(location.bucket())
                .key(location.key())
                .build();
        return s3Client.getObject(request);
    }

    @Override
    public void deleteByUrl(String url) {
        S3Location location = parseUrl(url);
        s3Client.deleteObject(builder -> builder.bucket(location.bucket()).key(location.key()));
    }

    private StoredObject putObject(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
        String bucket = requireText(bucketName, "bucketName");
        Objects.requireNonNull(content, "content must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        String key = generateKey(originalFilename);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(resolveContentType(contentType))
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(content, size));
        return new StoredObject(URL_PREFIX + bucket + "/" + key, contentType, size, originalFilename);
    }

    private S3Location parseUrl(String url) {
        URI uri = URI.create(requireText(url, "url"));
        if (!"s3".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("unsupported s3 url: " + url);
        }
        String bucket = requireText(uri.getHost(), "bucket");
        String path = uri.getPath();
        String key = path != null && path.startsWith("/") ? path.substring(1) : path;
        return new S3Location(bucket, requireText(key, "key"));
    }

    private String generateKey(String originalFilename) {
        String suffix = suffix(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (suffix.isBlank()) {
            return uuid;
        }
        return uuid + "." + suffix;
    }

    private String suffix(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).trim();
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record S3Location(String bucket, String key) {
    }
}
