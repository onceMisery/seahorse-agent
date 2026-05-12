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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;

import java.util.Objects;

/**
 * 文档刷新服务端口聚合。
 */
public record DocumentRefreshServicePorts(
        DocumentRefreshSchedulePort schedulePort,
        DocumentRefreshStateRepositoryPort stateRepositoryPort,
        KnowledgeDocumentRepositoryPort documentRepositoryPort,
        DocumentFetcherPort documentFetcherPort,
        ObjectStoragePort objectStoragePort,
        KnowledgeDocumentInboundPort documentInboundPort,
        PipelineDefinitionRepositoryPort pipelineRepositoryPort,
        SchedulerPort schedulerPort,
        DistributedLockPort lockPort
) {

    public DocumentRefreshServicePorts {
        Objects.requireNonNull(schedulePort, "schedulePort must not be null");
        Objects.requireNonNull(stateRepositoryPort, "stateRepositoryPort must not be null");
        Objects.requireNonNull(documentRepositoryPort, "documentRepositoryPort must not be null");
        Objects.requireNonNull(documentFetcherPort, "documentFetcherPort must not be null");
        Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        Objects.requireNonNull(documentInboundPort, "documentInboundPort must not be null");
        Objects.requireNonNull(pipelineRepositoryPort, "pipelineRepositoryPort must not be null");
        Objects.requireNonNull(schedulerPort, "schedulerPort must not be null");
        lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
    }

    public DocumentRefreshServicePorts(DocumentRefreshSchedulePort schedulePort,
                                       DocumentRefreshStateRepositoryPort stateRepositoryPort,
                                       KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                       DocumentFetcherPort documentFetcherPort,
                                       ObjectStoragePort objectStoragePort,
                                       KnowledgeDocumentInboundPort documentInboundPort,
                                       PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                       SchedulerPort schedulerPort) {
        this(schedulePort, stateRepositoryPort, documentRepositoryPort, documentFetcherPort, objectStoragePort,
                documentInboundPort, pipelineRepositoryPort, schedulerPort, DistributedLockPort.noop());
    }
}
