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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.TaskDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.TaskMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.task.TaskType;
import com.miracle.ai.seahorse.agent.ports.outbound.task.TaskRepositoryPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcTaskRepository implements TaskRepositoryPort {

    private final TaskMapper mapper;

    public JdbcTaskRepository(TaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Task save(Task task) {
        TaskDO existing = mapper.selectById(task.getTaskId());
        if (existing == null) {
            mapper.insert(toDO(task));
        } else {
            mapper.updateById(toDO(task));
        }
        return task;
    }

    @Override
    public Optional<Task> findById(String taskId) {
        TaskDO po = mapper.selectById(taskId);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public List<Task> findByUserId(String userId, int limit) {
        LambdaQueryWrapper<TaskDO> wrapper = new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getUserId, userId)
                .orderByDesc(TaskDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit));
        return mapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateStatus(String taskId, TaskStatus status) {
        LambdaUpdateWrapper<TaskDO> wrapper = new LambdaUpdateWrapper<TaskDO>()
                .eq(TaskDO::getTaskId, taskId)
                .set(TaskDO::getStatus, status.name());
        if (status == TaskStatus.RUNNING) {
            wrapper.set(TaskDO::getStartedAt, LocalDateTime.now(ZoneOffset.UTC));
        }
        if (status.isTerminal()) {
            wrapper.set(TaskDO::getFinishedAt, LocalDateTime.now(ZoneOffset.UTC));
        }
        mapper.update(null, wrapper);
    }

    @Override
    public void updateRunId(String taskId, String runId) {
        LambdaUpdateWrapper<TaskDO> wrapper = new LambdaUpdateWrapper<TaskDO>()
                .eq(TaskDO::getTaskId, taskId)
                .set(TaskDO::getRunId, runId);
        mapper.update(null, wrapper);
    }

    @Override
    public void updateConversationId(String taskId, String conversationId) {
        LambdaUpdateWrapper<TaskDO> wrapper = new LambdaUpdateWrapper<TaskDO>()
                .eq(TaskDO::getTaskId, taskId)
                .set(TaskDO::getConversationId, conversationId);
        mapper.update(null, wrapper);
    }

    @Override
    public Optional<Task> findRunningByConversationId(String conversationId) {
        LambdaQueryWrapper<TaskDO> wrapper = new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getConversationId, conversationId)
                .eq(TaskDO::getStatus, TaskStatus.RUNNING.name())
                .orderByDesc(TaskDO::getCreatedAt)
                .last("LIMIT 1");
        TaskDO po = mapper.selectOne(wrapper);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    // ---- mapping ----

    private Task toDomain(TaskDO po) {
        return Task.rehydrate(
                po.getTaskId(),
                TaskType.valueOf(po.getType()),
                TaskStatus.valueOf(po.getStatus()),
                po.getUserId(),
                po.getConversationId(),
                po.getRunId(),
                po.getAgentId(),
                po.getTitle(),
                po.getQuestion(),
                toInstant(po.getCreatedAt()),
                toInstant(po.getStartedAt()),
                toInstant(po.getFinishedAt())
        );
    }

    private TaskDO toDO(Task task) {
        TaskDO po = new TaskDO();
        po.setTaskId(task.getTaskId());
        po.setType(task.getType().name());
        po.setStatus(task.getStatus().name());
        po.setUserId(task.getUserId());
        po.setConversationId(task.getConversationId());
        po.setRunId(task.getRunId());
        po.setAgentId(task.getAgentId());
        po.setTitle(task.getTitle());
        po.setQuestion(task.getQuestion());
        po.setCreatedAt(toLocalDateTime(task.getCreatedAt()));
        po.setStartedAt(toLocalDateTime(task.getStartedAt()));
        po.setFinishedAt(toLocalDateTime(task.getFinishedAt()));
        return po;
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
