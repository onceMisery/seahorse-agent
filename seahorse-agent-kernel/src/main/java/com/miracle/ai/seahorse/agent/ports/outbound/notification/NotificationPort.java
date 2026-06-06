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

package com.miracle.ai.seahorse.agent.ports.outbound.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 通知服务端口。
 *
 * <p>支持站内信、邮件、Webhook 等多渠道通知发送。
 */
public interface NotificationPort {

    /**
     * 发送站内通知。
     */
    void sendInApp(SendNotificationCommand command);

    /**
     * 查询用户未读通知数量。
     */
    long countUnread(String tenantId, long userId);

    /**
     * 查询用户通知列表。
     */
    List<NotificationRecord> listNotifications(String tenantId, long userId, int page, int size);

    /**
     * 标记通知为已读。
     */
    void markAsRead(String tenantId, long userId, List<Long> notificationIds);

    /**
     * 标记所有通知为已读。
     */
    void markAllAsRead(String tenantId, long userId);

    /**
     * 发送通知命令。
     */
    record SendNotificationCommand(
            String tenantId,
            long userId,
            String title,
            String content,
            String type,
            String priority,
            String link,
            Map<String, String> templateVariables
    ) {}

    /**
     * 通知记录。
     */
    record NotificationRecord(
            long id,
            String title,
            String content,
            String type,
            String priority,
            boolean isRead,
            Instant readAt,
            String link,
            Instant createdAt
    ) {}
}
