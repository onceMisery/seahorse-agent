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

import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResultInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseGateResultController {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final ObjectProvider<GateResultInboundPort> gateResultPortProvider;

    public SeahorseGateResultController(ObjectProvider<GateResultInboundPort> gateResultPortProvider) {
        this.gateResultPortProvider = gateResultPortProvider;
    }

    @GetMapping("/api/gate-results/{subjectType}/{subjectId}")
    public ApiResponse<Object> latest(@PathVariable String subjectType, @PathVariable String subjectId) {
        return ApiResponses.requireService(gateResultPortProvider, port -> port.latest(subjectType, subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Gate result not found")));
    }

    @GetMapping("/api/gate-results/{subjectType}/{subjectId}/history")
    public ApiResponse<Object> history(@PathVariable String subjectType,
                                       @PathVariable String subjectId,
                                       @RequestParam(name = "limit", required = false) Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_HISTORY_LIMIT : limit;
        return ApiResponses.requireService(gateResultPortProvider,
                port -> port.history(subjectType, subjectId, effectiveLimit));
    }
}
