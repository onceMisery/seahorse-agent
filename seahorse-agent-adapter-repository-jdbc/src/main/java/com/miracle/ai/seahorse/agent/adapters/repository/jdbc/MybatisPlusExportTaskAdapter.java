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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.ExportTaskDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.ExportTaskMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.export.ExportTaskPort;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 导出任务 MyBatis Plus 适配器。
 */
public class MybatisPlusExportTaskAdapter implements ExportTaskPort {

    private final ExportTaskMapper exportTaskMapper;

    public MybatisPlusExportTaskAdapter(ExportTaskMapper exportTaskMapper) {
        this.exportTaskMapper = Objects.requireNonNull(exportTaskMapper);
    }

    @Override
    public long createTask(CreateExportTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        ExportTaskDO entity = new ExportTaskDO();
        entity.setTenantId(command.tenantId());
        entity.setUserId(command.userId());
        entity.setExportType(command.exportType());
        entity.setFileName(command.fileName());
        entity.setParameters(command.parameters());
        entity.setStatus("PENDING");
        entity.setProgress(0);
        entity.setCreatedAt(Timestamp.from(Instant.now()));

        exportTaskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateProgress(long taskId, int progress) {
        ExportTaskDO entity = new ExportTaskDO();
        entity.setId(taskId);
        entity.setStatus("PROCESSING");
        entity.setProgress(Math.min(progress, 100));
        exportTaskMapper.updateById(entity);
    }

    @Override
    public void markCompleted(long taskId, String fileUrl) {
        ExportTaskDO entity = new ExportTaskDO();
        entity.setId(taskId);
        entity.setStatus("COMPLETED");
        entity.setProgress(100);
        entity.setFileUrl(fileUrl);
        entity.setCompletedAt(Timestamp.from(Instant.now()));
        exportTaskMapper.updateById(entity);
    }

    @Override
    public void markFailed(long taskId, String errorMessage) {
        ExportTaskDO entity = new ExportTaskDO();
        entity.setId(taskId);
        entity.setStatus("FAILED");
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(Timestamp.from(Instant.now()));
        exportTaskMapper.updateById(entity);
    }

    @Override
    public Optional<ExportTaskRecord> getTask(long taskId) {
        ExportTaskDO entity = exportTaskMapper.selectById(taskId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toRecord(entity));
    }

    private ExportTaskRecord toRecord(ExportTaskDO entity) {
        return new ExportTaskRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getExportType(),
                entity.getFileName(),
                entity.getStatus(),
                entity.getProgress() != null ? entity.getProgress() : 0,
                entity.getFileUrl(),
                entity.getErrorMessage(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null,
                entity.getCompletedAt() != null ? entity.getCompletedAt().toInstant() : null
        );
    }
}
