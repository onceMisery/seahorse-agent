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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackDiffInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackRetentionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseContextPackController {

    private final ObjectProvider<ContextPackQueryInboundPort> contextPackQueryPortProvider;
    private final ObjectProvider<ContextPackRetentionInboundPort> contextPackRetentionPortProvider;
    private final ObjectProvider<ContextPackDiffInboundPort> contextPackDiffPortProvider;

    public SeahorseContextPackController(ObjectProvider<ContextPackQueryInboundPort> contextPackQueryPortProvider,
                                         ObjectProvider<ContextPackRetentionInboundPort>
                                                 contextPackRetentionPortProvider,
                                         ObjectProvider<ContextPackDiffInboundPort> contextPackDiffPortProvider) {
        this.contextPackQueryPortProvider = contextPackQueryPortProvider;
        this.contextPackRetentionPortProvider = contextPackRetentionPortProvider;
        this.contextPackDiffPortProvider = contextPackDiffPortProvider;
    }

    @GetMapping("/api/context-packs/{contextPackId}")
    public ApiResponse<Object> findById(@PathVariable String contextPackId) {
        return ApiResponses.requireService(contextPackQueryPortProvider, port -> port.findById(contextPackId)
                .orElseThrow(() -> new ResourceNotFoundException("Context pack not found")));
    }

    @GetMapping("/api/context-packs/{contextPackId}/items")
    public ApiResponse<Object> listItems(@PathVariable String contextPackId) {
        return ApiResponses.requireService(contextPackQueryPortProvider, port -> port.listItems(contextPackId));
    }

    @PostMapping("/api/context-packs/{contextPackId}/items:cleanup-expired")
    public ApiResponse<Object> cleanupExpiredItems(@PathVariable String contextPackId) {
        return ApiResponses.requireService(contextPackRetentionPortProvider,
                port -> port.cleanupExpiredItems(contextPackId));
    }

    @GetMapping("/api/context-packs/{contextPackId}/diff")
    public ApiResponse<Object> diff(@PathVariable String contextPackId,
                                    @RequestParam String rightContextPackId) {
        return ApiResponses.requireService(contextPackDiffPortProvider,
                port -> port.diff(contextPackId, rightContextPackId));
    }
}
