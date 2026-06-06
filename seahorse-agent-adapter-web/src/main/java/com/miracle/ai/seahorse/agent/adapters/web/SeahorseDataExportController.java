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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.outbound.export.ExportTaskPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据导出 Controller。
 *
 * <p>提供异步数据导出功能：创建导出任务、查询任务进度、下载导出文件。
 */
@RestController
public class SeahorseDataExportController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";

    private final ObjectProvider<ExportTaskPort> exportTaskPortProvider;

    public SeahorseDataExportController(ObjectProvider<ExportTaskPort> exportTaskPortProvider) {
        this.exportTaskPortProvider = exportTaskPortProvider;
    }

    @PostMapping({"/export/tasks", "/api/export/tasks"})
    public Map<String, Object> createExportTask(
            @RequestBody CreateExportRequest request,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        ExportTaskPort port = exportTaskPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, ERROR_CODE, "message", "Export service not available");
        }
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        String tenantId = resolveTenantId();

        long taskId = port.createTask(new ExportTaskPort.CreateExportTaskCommand(
                tenantId,
                Long.parseLong(resolvedUserId),
                request.exportType(),
                request.fileName(),
                request.parameters()
        ));

        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, Map.of("taskId", taskId));
    }

    @GetMapping({"/export/tasks/{taskId}", "/api/export/tasks/{taskId}"})
    public Map<String, Object> getExportTask(@PathVariable long taskId) {
        ExportTaskPort port = exportTaskPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, ERROR_CODE, "message", "Export service not available");
        }
        var task = port.getTask(taskId);
        if (task.isEmpty()) {
            return Map.of(KEY_CODE, ERROR_CODE, "message", "Task not found");
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, task.get());
    }

    private String resolveTenantId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getExtra("tenantId").toString();
        } catch (Exception e) {
            return "default";
        }
    }

    record CreateExportRequest(String exportType, String fileName, String parameters) {}
}
