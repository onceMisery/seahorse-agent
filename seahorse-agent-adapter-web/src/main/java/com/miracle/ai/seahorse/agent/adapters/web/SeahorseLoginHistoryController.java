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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.IpGeolocationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.LoginHistoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.LoginHistoryPort.LoginHistoryEntry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for querying login history of the current user.
 */
@RestController
public class SeahorseLoginHistoryController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_LIST = "list";
    private static final String SUCCESS_CODE = "0";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ObjectProvider<CurrentUserPort> currentUserPortProvider;
    private final ObjectProvider<LoginHistoryPort> loginHistoryPortProvider;
    private final ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider;

    public SeahorseLoginHistoryController(ObjectProvider<CurrentUserPort> currentUserPortProvider,
                                          ObjectProvider<LoginHistoryPort> loginHistoryPortProvider,
                                          ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider) {
        this.currentUserPortProvider = currentUserPortProvider;
        this.loginHistoryPortProvider = loginHistoryPortProvider;
        this.ipGeolocationPortProvider = ipGeolocationPortProvider;
    }

    /**
     * Get paginated login history for the current user.
     *
     * @param page the page number (0-based, default 0)
     * @param size the page size (default 20, max 100)
     * @return paginated login history with geo info
     */
    @GetMapping("/api/me/login-history")
    public Map<String, Object> getLoginHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        CurrentUserPort currentUserPort = currentUserPortProvider.getIfAvailable();
        if (currentUserPort == null) {
            return Map.of(KEY_CODE, "1", "message", "Authentication not available");
        }

        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        Long userId = currentUser.userId();
        if (userId == null) {
            return Map.of(KEY_CODE, "1", "message", "User not found");
        }

        LoginHistoryPort historyPort = loginHistoryPortProvider.getIfAvailable();
        if (historyPort == null) {
            return Map.of(KEY_CODE, "1", "message", "Login history not available");
        }

        // Validate and normalize parameters
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<LoginHistoryEntry> entries = historyPort.findByUserId(userId, safePage, safeSize);
        long total = historyPort.countByUserId(userId);

        // Optionally enrich with geo info
        IpGeolocationPort geoPort = ipGeolocationPortProvider != null
                ? ipGeolocationPortProvider.getIfAvailable() : null;
        List<Map<String, Object>> enrichedList = entries.stream()
                .map(entry -> enrichEntry(entry, geoPort))
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_LIST, enrichedList);
        data.put(KEY_TOTAL, total);
        data.put("page", safePage);
        data.put("size", safeSize);

        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data);
    }

    private Map<String, Object> enrichEntry(LoginHistoryEntry entry, IpGeolocationPort geoPort) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.id());
        map.put("userId", entry.userId());
        map.put("tenantId", entry.tenantId());
        map.put("loginType", entry.loginType());
        map.put("ipAddress", entry.ipAddress());
        map.put("userAgent", entry.userAgent());
        map.put("deviceInfo", entry.deviceInfo());
        map.put("status", entry.status());
        map.put("failureReason", entry.failureReason());
        map.put("createdAt", entry.createdAt() != null ? entry.createdAt().toString() : null);

        // Add geo location info if available
        if (geoPort != null && entry.ipAddress() != null) {
            try {
                IpGeolocationPort.GeoInfo geoInfo = geoPort.resolve(entry.ipAddress());
                if (geoInfo != null) {
                    map.put("geoLocation", geoInfo.toDisplayString());
                    map.put("geoCountry", geoInfo.country());
                    map.put("geoRegion", geoInfo.region());
                    map.put("geoCity", geoInfo.city());
                    map.put("geoIsp", geoInfo.isp());
                }
            } catch (Exception e) {
                // Graceful degradation
            }
        }

        return map;
    }
}
