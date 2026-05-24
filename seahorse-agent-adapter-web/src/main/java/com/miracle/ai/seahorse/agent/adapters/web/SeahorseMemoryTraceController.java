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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseMemoryTraceController {

    private final ObjectProvider<MemoryTraceInboundPort> tracePortProvider;

    public SeahorseMemoryTraceController(ObjectProvider<MemoryTraceInboundPort> tracePortProvider) {
        this.tracePortProvider = tracePortProvider;
    }

    @GetMapping("/memories/traces")
    public ApiResponse<Object> traces(@RequestParam(defaultValue = "50") int limit,
                                      @RequestParam(defaultValue = "") String traceId,
                                      @RequestParam(defaultValue = "") String tenantId,
                                      @RequestParam(defaultValue = "") String userId,
                                      @RequestParam(defaultValue = "") String conversationId,
                                      @RequestParam(defaultValue = "") String sessionId,
                                      @RequestParam(defaultValue = "") String component,
                                      @RequestParam(defaultValue = "") String status) {
        return ApiResponses.requireServiceOrError(tracePortProvider,
                port -> port.listRecent(new MemoryTraceQuery(
                        limit,
                        traceId,
                        tenantId,
                        userId,
                        conversationId,
                        sessionId,
                        component,
                        status)));
    }
}
