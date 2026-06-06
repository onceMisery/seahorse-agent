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

package com.miracle.ai.seahorse.agent.ports.outbound.export;

import java.time.Instant;
import java.util.Optional;

/**
 * 异步导出任务端口。
 *
 * <p>管理异步导出任务的生命周期：创建 → 处理中 → 完成/失败。
 */
public interface ExportTaskPort {

    /**
     * 创建导出任务。
     */
    long createTask(CreateExportTaskCommand command);

    /**
     * 更新任务进度。
     */
    void updateProgress(long taskId, int progress);

    /**
     * 标记任务完成。
     */
    void markCompleted(long taskId, String fileUrl);

    /**
     * 标记任务失败。
     */
    void markFailed(long taskId, String errorMessage);

    /**
     * 查询任务状态。
     */
    Optional<ExportTaskRecord> getTask(long taskId);

    /**
     * 创建导出任务命令。
     */
    record CreateExportTaskCommand(
            String tenantId,
            long userId,
            String exportType,
            String fileName,
            String parameters
    ) {}

    /**
     * 导出任务记录。
     */
    record ExportTaskRecord(
            long id,
            String tenantId,
            long userId,
            String exportType,
            String fileName,
            String status,
            int progress,
            String fileUrl,
            String errorMessage,
            Instant createdAt,
            Instant completedAt
    ) {}
}
