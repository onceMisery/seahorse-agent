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

import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadFileContent;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadProcessOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelKnowledgeDocumentServiceTests {

    @Test
    void shouldUploadDocumentAndPublishChunkEvent() {
        Ports ports = new Ports();
        KernelKnowledgeDocumentService service = newService(ports);

        KnowledgeDocumentRecord document = service.upload(new UploadKnowledgeDocumentCommand(
                1L,
                new UploadFileContent(new ByteArrayInputStream("content".getBytes()), 7L, "policy.pdf", "pdf"),
                "