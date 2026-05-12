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

package com.miracle.ai.seahorse.agent.kernel.application.ingestion;

import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelinePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;

import java.util.Objects;

/**
 * Seahorse 原生入库 Pipeline 管理服务。
 */
public class KernelIngestionPipelineService implements IngestionPipelineInboundPort {

    private final IngestionPipelineRepositoryPort pipelineRepositoryPort;

    public KernelIngestionPipelineService(IngestionPipelineRepositoryPort pipelineRepositoryPort) {
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
    }

    @Override
    public IngestionPipelineRecord create(IngestionPipelinePayload payload) {
        IngestionPipelinePayload safePayload = normalizePayload(payload);
        return pipelineRepositoryPort.create(safePayload);
    }

    @Override
    public IngestionPipelineRecord update(String pipelineId, IngestionPipelinePayload payload) {
        String safePipelineId = requireText(pipelineId, "pipelineId");
        IngestionPipelinePayload safePayload = normalizePayload(payload);
        if (!pipelineRepositoryPort.update(safePipelineId, safePayload)) {
            throw new IllegalArgumentException("入库 Pipeline 不存在：" + safePipelineId);
        }
        return get(safePipelineId);
    }

    @Override
    public IngestionPipelineRecord get(String pipelineId) {
        String safePipelineId = requireText(pipelineId, "pipelineId");
        return pipelineRepositoryPort.findRecordById(safePipelineId)
                .orElseThrow(() -> new IllegalArgumentException("入库 Pipeline 不存在：" + safePipelineId));
    }

    @Override
    public IngestionPipelinePage page(long current, long size, String keyword) {
        return pipelineRepositoryPort.page(current, size, keyword);
    }

    @Override
    public void delete(String pipelineId, String operator) {
        String safePipelineId = requireText(pipelineId, "pipelineId");
        if (!pipelineRepositoryPort.delete(safePipelineId, operator)) {
            throw new IllegalArgumentException("入库 Pipeline 不存在：" + safePipelineId);
        }
    }

    private IngestionPipelinePayload normalizePayload(IngestionPipelinePayload payload) {
        IngestionPipelinePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String name = requireText(safePayload.name(), "name");
        return new IngestionPipelinePayload(
                name,
                Objects.requireNonNullElse(safePayload.description(), ""),
                safePayload.nodes(),
                safePayload.operator());
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
