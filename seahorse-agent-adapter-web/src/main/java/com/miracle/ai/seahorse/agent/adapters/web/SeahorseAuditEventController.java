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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AuditQueryInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SeahorseAuditEventController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<AuditQueryInboundPort> auditQueryPortProvider;

    public SeahorseAuditEventController(ObjectProvider<AuditQueryInboundPort> auditQueryPortProvider) {
        this.auditQueryPortProvider = auditQueryPortProvider;
    }

    @GetMapping("/api/audit-events")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) String runId,
                                    @RequestParam(required = false) String agentId,
                                    @RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String resourceId,
                                    @RequestParam(required = false) AuditEventType eventType,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        return ApiResponses.requireService(auditQueryPortProvider,
                port -> port.page(
                        tenantId,
                        runId,
                        agentId,
                        resourceType,
                        resourceId,
                        eventType,
                        occurredFrom,
                        occurredTo,
                        current,
                        size));
    }

    @GetMapping("/api/audit-events/{auditId}")
    public ApiResponse<Object> findById(@PathVariable String auditId) {
        return ApiResponses.requireService(auditQueryPortProvider, port -> port.findById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event not found")));
    }
}
