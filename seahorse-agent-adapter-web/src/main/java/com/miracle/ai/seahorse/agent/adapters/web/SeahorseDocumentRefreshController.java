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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Seahorse 原生文档刷新 Web adapter。
 */
@RestController
public class SeahorseDocumentRefreshController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "system";

    private final ObjectProvider<DocumentRefreshInboundPort> refreshPortProvider;

    public SeahorseDocumentRefreshController(ObjectProvider<DocumentRefreshInboundPort> refreshPortProvider) {
        this.refreshPortProvider = refreshPortProvider;
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/refresh")
    public Map<String, Object> refresh(@PathVariable("doc-id") String docId,
                                       @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        if (refreshPortProvider.getIfAvailable() == null) {
            return Map.of(KEY_CODE, "1", "message", "Service not available");
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, refreshPortProvider.getIfAvailable().refreshDocument(docId, operator(userId)));
    }

    @PostMapping("/knowledge-base/docs/refresh-due")
    public Map<String, Object> refreshDue(@RequestParam(defaultValue = "20") int limit,
                                          @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        if (refreshPortProvider.getIfAvailable() == null) {
            return Map.of(KEY_CODE, "1", "message", "Service not available");
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                refreshPortProvider.getIfAvailable().refreshDueSchedules(Instant.now(), limit, operator(userId)));
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
