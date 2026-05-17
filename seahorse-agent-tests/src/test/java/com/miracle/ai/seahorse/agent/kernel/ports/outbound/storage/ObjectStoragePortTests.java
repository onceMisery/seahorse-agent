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

package com.miracle.ai.seahorse.agent.kernel.ports.outbound.storage;

import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class ObjectStoragePortTests {

    @Test
    void reliableUploadShouldDelegateToUploadByDefault() {
        DelegatingStoragePort storage = new DelegatingStoragePort();
        StoredObject expected = new StoredObject("local://bucket/object.txt", "text/plain", 4L, "object.txt");

        storage.nextObject = expected;
        StoredObject actual = storage.reliableUpload("bucket",
                new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)), 4, "object.txt", "text/plain");

        Assertions.assertSame(expected, actual);
        Assertions.assertEquals(1, storage.uploadCalls);
    }

    private static final class DelegatingStoragePort implements ObjectStoragePort {

        private int uploadCalls;
        private StoredObject nextObject;

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
            uploadCalls++;
            return nextObject;
        }

        @Override
        public InputStream openStream(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByUrl(String url) {
            throw new UnsupportedOperationException();
        }
    }
}
