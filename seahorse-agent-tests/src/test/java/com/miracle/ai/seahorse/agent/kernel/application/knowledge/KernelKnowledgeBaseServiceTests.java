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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeBaseCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeBaseValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelKnowledgeBaseServiceTests {

    @Test
    void shouldUseStorageSafeBucketNameWithoutChangingVectorCollectionName() {
        RecordingKnowledgeBaseRepository repository = new RecordingKnowledgeBaseRepository();
        RecordingVectorCollectionAdminPort vectorAdmin = new RecordingVectorCollectionAdminPort();
        RecordingObjectStoragePort storage = new RecordingObjectStoragePort();
        KernelKnowledgeBaseService service = new KernelKnowledgeBaseService(repository, vectorAdmin, storage);

        Long kbId = service.create(new CreateKnowledgeBaseCommand(
                "Codex KB", "mock", "codex_e2e_collection", "tester"));

        assertThat(kbId).isEqualTo(1L);
        assertThat(storage.buckets).containsExactly(
                KnowledgeStorageBucketNames.fromCollectionName("codex_e2e_collection"));
        assertThat(vectorAdmin.collections).containsExactly("codex_e2e_collection");
        assertThat(repository.createdValues.collectionName()).isEqualTo("codex_e2e_collection");
    }

    @Test
    void shouldRejectInvalidVectorCollectionNameBeforeCallingAdapters() {
        RecordingKnowledgeBaseRepository repository = new RecordingKnowledgeBaseRepository();
        RecordingVectorCollectionAdminPort vectorAdmin = new RecordingVectorCollectionAdminPort();
        RecordingObjectStoragePort storage = new RecordingObjectStoragePort();
        KernelKnowledgeBaseService service = new KernelKnowledgeBaseService(repository, vectorAdmin, storage);

        assertThatThrownBy(() -> service.create(new CreateKnowledgeBaseCommand(
                "Codex KB", "mock", "codex-e2e-collection", "tester")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionName can only contain letters, numbers and underscores");

        assertThat(storage.buckets).isEmpty();
        assertThat(vectorAdmin.collections).isEmpty();
        assertThat(repository.createdValues).isNull();
    }

    private static class RecordingKnowledgeBaseRepository implements KnowledgeBaseRepositoryPort {

        private CreateKnowledgeBaseValues createdValues;

        @Override
        public Long create(CreateKnowledgeBaseValues values) {
            createdValues = values;
            return 1L;
        }

        @Override
        public boolean nameExists(String normalizedName, Long excludedKbId) {
            return false;
        }

        @Override
        public Optional<KnowledgeBaseRecord> findById(Long kbId) {
            return Optional.empty();
        }

        @Override
        public KnowledgeBasePage page(long current, long size, String name) {
            return new KnowledgeBasePage(List.of(), 0, size, current, 0);
        }

        @Override
        public boolean hasDocuments(Long kbId) {
            return false;
        }

        @Override
        public boolean hasVectorizedDocuments(Long kbId) {
            return false;
        }

        @Override
        public boolean update(Long kbId, KnowledgeBaseUpdateValues values) {
            return false;
        }

        @Override
        public boolean delete(Long kbId, String operator) {
            return false;
        }
    }

    private static class RecordingVectorCollectionAdminPort implements VectorCollectionAdminPort {

        private final List<String> collections = new ArrayList<>();

        @Override
        public boolean collectionExists(String collectionName) {
            return collections.contains(collectionName);
        }

        @Override
        public void ensureCollection(String collectionName) {
            collections.add(collectionName);
        }
    }

    private static class RecordingObjectStoragePort implements ObjectStoragePort {

        private final List<String> buckets = new ArrayList<>();

        @Override
        public void ensureBucket(String bucketName) {
            buckets.add(bucketName);
        }

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
            return new StoredObject("s3://" + bucketName + "/" + originalFilename, contentType, size, originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }
}
