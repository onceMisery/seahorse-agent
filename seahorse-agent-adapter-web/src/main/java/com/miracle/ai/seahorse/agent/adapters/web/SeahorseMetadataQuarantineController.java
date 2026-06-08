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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineRetryCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 元数据隔离区管理 Web adapter。
 */
@RestController
public class SeahorseMetadataQuarantineController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "system";

    private final ObjectProvider<MetadataQuarantineInboundPort> quarantinePortProvider;

    public SeahorseMetadataQuarantineController(ObjectProvider<MetadataQuarantineInboundPort> quarantinePortProvider) {
        this.quarantinePortProvider = quarantinePortProvider;
    }

    @GetMapping("/metadata-quarantine/items")
    public Map<String, Object> page(@RequestParam String tenantId,
                                    @RequestParam(required = false) String kbId,
                                    @RequestParam(required = false) Boolean resolved,
                                    @RequestParam(required = false) String stage,
                                    @RequestParam(required = false) String reasonCode,
                                    @RequestParam(required = false) String documentId,
                                    @RequestParam(required = false) String jobId,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                quarantinePort().page(tenantId, kbId, resolved, stage, reasonCode, documentId, jobId, current, size));
    }

    @GetMapping("/metadata-quarantine/items/{item-id}")
    public Map<String, Object> queryById(@PathVariable("item-id") String itemId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, quarantinePort().queryById(itemId));
    }

    @PostMapping("/metadata-quarantine/items/{item-id}/resolve")
    public Map<String, Object> resolve(@PathVariable("item-id") String itemId,
                                       @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                quarantinePort().resolve(itemId, operator(userId)));
    }

    @PostMapping("/metadata-quarantine/items/{item-id}/retry")
    public Map<String, Object> retry(@PathVariable("item-id") String itemId,
                                     @RequestBody(required = false) MetadataQuarantineRetryRequest request,
                                     @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                quarantinePort().retry(itemId, new MetadataQuarantineRetryCommand(
                        operator(userId), nextRetryTime(request))));
    }

    private MetadataQuarantineInboundPort quarantinePort() {
        MetadataQuarantineInboundPort port = quarantinePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private Instant nextRetryTime(MetadataQuarantineRetryRequest request) {
        String value = request == null ? "" : request.getNextRetryTime();
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("nextRetryTime must be ISO-8601 instant");
        }
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}
