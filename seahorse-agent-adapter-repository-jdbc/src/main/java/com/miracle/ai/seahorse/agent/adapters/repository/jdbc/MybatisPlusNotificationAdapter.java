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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.NotificationDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.NotificationMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.notification.NotificationPort;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 通知中心 MyBatis Plus 适配器。
 */
public class MybatisPlusNotificationAdapter implements NotificationPort {

    private final NotificationMapper notificationMapper;

    public MybatisPlusNotificationAdapter(NotificationMapper notificationMapper) {
        this.notificationMapper = Objects.requireNonNull(notificationMapper);
    }

    @Override
    public void sendInApp(SendNotificationCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        NotificationDO entity = new NotificationDO();
        entity.setTenantId(command.tenantId());
        entity.setUserId(command.userId());
        entity.setTitle(command.title());
        entity.setContent(command.content());
        entity.setType(command.type() != null ? command.type() : "SYSTEM");
        entity.setPriority(command.priority() != null ? command.priority() : "NORMAL");
        entity.setLink(command.link());
        entity.setIsRead(false);
        entity.setCreatedAt(Timestamp.from(Instant.now()));

        notificationMapper.insert(entity);
    }

    @Override
    public long countUnread(String tenantId, long userId) {
        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getIsRead, false);

        return notificationMapper.selectCount(wrapper);
    }

    @Override
    public List<NotificationRecord> listNotifications(String tenantId, long userId, int page, int size) {
        Page<NotificationDO> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getUserId, userId)
                .orderByDesc(NotificationDO::getCreatedAt);

        Page<NotificationDO> result = notificationMapper.selectPage(pageParam, wrapper);

        return result.getRecords().stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public void markAsRead(String tenantId, long userId, List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());

        LambdaUpdateWrapper<NotificationDO> wrapper = new LambdaUpdateWrapper<NotificationDO>()
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getUserId, userId)
                .in(NotificationDO::getId, notificationIds)
                .eq(NotificationDO::getIsRead, false)
                .set(NotificationDO::getIsRead, true)
                .set(NotificationDO::getReadAt, now);

        notificationMapper.update(null, wrapper);
    }

    @Override
    public void markAllAsRead(String tenantId, long userId) {
        Timestamp now = Timestamp.from(Instant.now());

        LambdaUpdateWrapper<NotificationDO> wrapper = new LambdaUpdateWrapper<NotificationDO>()
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getIsRead, false)
                .set(NotificationDO::getIsRead, true)
                .set(NotificationDO::getReadAt, now);

        notificationMapper.update(null, wrapper);
    }

    private NotificationRecord toRecord(NotificationDO entity) {
        return new NotificationRecord(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getType(),
                entity.getPriority(),
                Boolean.TRUE.equals(entity.getIsRead()),
                entity.getReadAt() != null ? entity.getReadAt().toInstant() : null,
                entity.getLink(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null
        );
    }
}
