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
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;

import java.time.Instant;

/**
 * 任务响应 DTO。
 */
public record TaskResponse(
        String taskId,
        String type,
        String status,
        String conversationId,
        String runId,
        String agentId,
        String title,
        String question,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getTaskId(),
                task.getType().name().toLowerCase(),
                task.getStatus().name().toLowerCase(),
                task.getConversationId(),
                task.getRunId(),
                task.getAgentId(),
                task.getTitle(),
                task.getQuestion(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }
}
