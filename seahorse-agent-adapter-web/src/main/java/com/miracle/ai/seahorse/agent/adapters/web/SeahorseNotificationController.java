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

import com.miracle.ai.seahorse.agent.ports.outbound.notification.NotificationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知中心 Web Controller。
 *
 * <p>提供站内通知的 CRUD 和已读/未读管理 API。
 */
@RestController
public class SeahorseNotificationController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<NotificationPort> notificationPortProvider;

    public SeahorseNotificationController(ObjectProvider<NotificationPort> notificationPortProvider) {
        this.notificationPortProvider = notificationPortProvider;
    }

    @GetMapping({"/notifications", "/api/notifications"})
    public Map<String, Object> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        NotificationPort port = notificationPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, List.of());
        }
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        String tenantId = resolveTenantId();
        var notifications = port.listNotifications(tenantId, Long.parseLong(resolvedUserId), page, size);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, notifications);
    }

    @GetMapping({"/notifications/unread-count", "/api/notifications/unread-count"})
    public Map<String, Object> unreadCount(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        NotificationPort port = notificationPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, 0L);
        }
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        String tenantId = resolveTenantId();
        long count = port.countUnread(tenantId, Long.parseLong(resolvedUserId));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, count);
    }

    @PostMapping({"/notifications/mark-read", "/api/notifications/mark-read"})
    public Map<String, Object> markAsRead(
            @RequestBody MarkReadRequest request,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        NotificationPort port = notificationPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, SUCCESS_CODE);
        }
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        String tenantId = resolveTenantId();
        port.markAsRead(tenantId, Long.parseLong(resolvedUserId), request.notificationIds());
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping({"/notifications/mark-all-read", "/api/notifications/mark-all-read"})
    public Map<String, Object> markAllAsRead(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        NotificationPort port = notificationPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, SUCCESS_CODE);
        }
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        String tenantId = resolveTenantId();
        port.markAllAsRead(tenantId, Long.parseLong(resolvedUserId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private String resolveTenantId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getExtra("tenantId").toString();
        } catch (Exception e) {
            return "default";
        }
    }

    record MarkReadRequest(List<Long> notificationIds) {}
}
