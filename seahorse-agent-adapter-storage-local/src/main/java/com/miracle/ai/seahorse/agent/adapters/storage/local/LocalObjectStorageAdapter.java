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

package com.miracle.ai.seahorse.agent.adapters.storage.local;

import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * 本地文件系统对象存储 adapter。
 */
public class LocalObjectStorageAdapter implements ObjectStoragePort {

    private static final String URL_PREFIX = "local://";
    private static final String DEFAULT_BUCKET = "default";

    private final Path rootDirectory;

    public LocalObjectStorageAdapter() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "seahorse-agent-storage"));
    }

    public LocalObjectStorageAdapter(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
    }

    @Override
    public void ensureBucket(String bucketName) {
        try {
            Files.createDirectories(rootDirectory.resolve(safePathPart(bucketName, DEFAULT_BUCKET)));
        } catch (IOException ex) {
            throw new IllegalStateException("create local bucket failed: " + bucketName, ex);
        }
    }

    @Override
    public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                               String contentType) {
        return writeObject(bucketName, content, size, originalFilename, contentType);
    }

    @Override
    public StoredObject reliableUpload(String bucketName, InputStream content, long size, String originalFilename,
                                       String contentType) {
        return writeObject(bucketName, content, size, originalFilename, contentType);
    }

    @Override
    public InputStream openStream(String url) {
        try {
            return Files.newInputStream(resolveUrl(url));
        } catch (IOException ex) {
            throw new IllegalStateException("open local object failed: " + url, ex);
        }
    }

    @Override
    public void deleteByUrl(String url) {
        try {
            Files.deleteIfExists(resolveUrl(url));
        } catch (IOException ex) {
            throw new IllegalStateException("delete local object failed: " + url, ex);
        }
    }

    private StoredObject writeObject(String bucketName, InputStream content, long size, String originalFilename,
                                     String contentType) {
        Objects.requireNonNull(content, "content must not be null");
        String bucket = safePathPart(bucketName, DEFAULT_BUCKET);
        String filename = UUID.randomUUID() + "-" + safePathPart(originalFilename, "object.bin");
        Path target = rootDirectory.resolve(bucket).resolve(filename);
        copy(content, target);
        String url = URL_PREFIX + bucket + "/" + filename;
        return new StoredObject(url, Objects.requireNonNullElse(contentType, ""), size, originalFilename);
    }

    private void copy(InputStream content, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("write local object failed: " + target, ex);
        }
    }

    private Path resolveUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new IllegalArgumentException("unsupported local object url: " + url);
        }
        String relativePath = url.substring(URL_PREFIX.length());
        Path resolved = rootDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDirectory.normalize())) {
            throw new IllegalArgumentException("local object url escapes root directory: " + url);
        }
        return resolved;
    }

    private String safePathPart(String value, String defaultValue) {
        String safeValue = Objects.requireNonNullElse(value, defaultValue);
        String normalized = safeValue.replace('\\', '_').replace('/', '_').trim();
        if (normalized.isBlank()) {
            return defaultValue;
        }
        return normalized;
    }
}
