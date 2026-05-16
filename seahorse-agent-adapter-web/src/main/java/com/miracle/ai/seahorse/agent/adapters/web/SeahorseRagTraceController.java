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

import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTracePageCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Seahorse 原生 RAG Trace 查询 Web adapter。
 *
 */
@RestController
public class SeahorseRagTraceController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final RagTraceInboundPort traceInboundPort;

    public SeahorseRagTraceController(ObjectProvider<RagTraceInboundPort> traceInboundPortProvider) {
        this.traceInboundPort = traceInboundPortProvider.getIfAvailable();
    }

    @GetMapping("/rag/traces/runs")
    public Map<String, Object> pageRuns(@RequestParam(required = false, defaultValue = "1") long current,
                                        @RequestParam(required = false, defaultValue = "10") long size,
                                        @RequestParam(required = false) String traceId,
                                        @RequestParam(required = false) String conversationId,
                                        @RequestParam(required = false) String taskId,
                                        @RequestParam(required = false) String status) {
        RagTracePageCommand command = new RagTracePageCommand(
                current, size, traceId, conversationId, taskId, status);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, traceInboundPort.pageRuns(command));
    }

    @GetMapping("/rag/traces/runs/{traceId}")
    public Map<String, Object> detail(@PathVariable String traceId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, traceInboundPort.detail(traceId));
    }

    @GetMapping("/rag/traces/runs/{traceId}/nodes")
    public Map<String, Object> nodes(@PathVariable String traceId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, traceInboundPort.listNodes(traceId));
    }
}
