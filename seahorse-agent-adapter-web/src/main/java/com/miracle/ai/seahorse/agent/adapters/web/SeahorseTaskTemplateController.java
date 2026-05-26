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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeahorseTaskTemplateController {

    private final ObjectProvider<TaskTemplateQueryInboundPort> taskTemplateQueryPortProvider;

    public SeahorseTaskTemplateController(ObjectProvider<TaskTemplateQueryInboundPort> taskTemplateQueryPortProvider) {
        this.taskTemplateQueryPortProvider = taskTemplateQueryPortProvider;
    }

    @GetMapping("/api/task-templates")
    public ApiResponse<List<TaskTemplate>> listEnabled() {
        return ApiResponses.requireService(taskTemplateQueryPortProvider, TaskTemplateQueryInboundPort::listEnabled);
    }

    @GetMapping("/api/task-templates/{templateId}")
    public ApiResponse<TaskTemplate> getById(@PathVariable String templateId) {
        TaskTemplateId stableTemplateId = TaskTemplateId.fromValue(templateId);
        return ApiResponses.requireService(taskTemplateQueryPortProvider,
                port -> port.findById(stableTemplateId)
                        .orElseThrow(() -> new IllegalArgumentException("Task template not found")));
    }
}
