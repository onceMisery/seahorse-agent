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

import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.ports.inbound.task.CreateTaskCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task Facade REST Controller。
 * <p>
 * 提供统一任务入口：创建任务、查询任务、取消任务。
 */
@RestController
@RequestMapping("/tasks")
public class SeahorseTaskController {

    private final ObjectProvider<TaskInboundPort> taskPortProvider;
    private final CurrentUserPort currentUserPort;

    public SeahorseTaskController(ObjectProvider<TaskInboundPort> taskPortProvider,
                                  CurrentUserPort currentUserPort) {
        this.taskPortProvider = taskPortProvider;
        this.currentUserPort = currentUserPort;
    }

    /**
     * 创建任务。
     */
    @PostMapping
    public ApiResponse<TaskResponse> createTask(@RequestBody CreateTaskRequest request) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            var currentUser = currentUserPort.requireCurrentUser();
            var command = new CreateTaskCommand(
                    request.toTaskType(),
                    String.valueOf(currentUser.userId()),
                    request.question(),
                    request.conversationId(),
                    request.agentId(),
                    request.title()
            );
            Task task = port.createTask(command);
            return TaskResponse.from(task);
        });
    }

    /**
     * 获取任务详情。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable String taskId) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            Task task = port.getTask(taskId);
            return TaskResponse.from(task);
        });
    }

    /**
     * 列出用户任务。
     */
    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            var currentUser = currentUserPort.requireCurrentUser();
            List<Task> tasks = port.listUserTasks(String.valueOf(currentUser.userId()), limit);
            return tasks.stream().map(TaskResponse::from).toList();
        });
    }

    /**
     * 取消任务。
     */
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<TaskResponse> cancelTask(@PathVariable String taskId) {
        return ApiResponses.requireService(taskPortProvider, port -> {
            Task task = port.cancelTask(taskId);
            return TaskResponse.from(task);
        });
    }
}
