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

import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生意图树 Web adapter。
 */
@RestController
@ConditionalOnBean(IntentTreeInboundPort.class)
public class SeahorseIntentTreeController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final IntentTreeInboundPort intentTreePort;

    public SeahorseIntentTreeController(IntentTreeInboundPort intentTreePort) {
        this.intentTreePort = Objects.requireNonNull(intentTreePort, "intentTreePort must not be null");
    }

    @GetMapping("/intent-tree/trees")
    public Map<String, Object> tree() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, intentTreePort.tree());
    }

    @PostMapping("/intent-tree")
    public Map<String, Object> createNode(@RequestBody IntentNodePayload request) {
        String id = intentTreePort.create(Objects.requireNonNull(request, "request must not be null"));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping("/intent-tree/{id}")
    public Map<String, Object> updateNode(@PathVariable String id, @RequestBody IntentNodePayload request) {
        intentTreePort.update(id, Objects.requireNonNull(request, "request must not be null"));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/intent-tree/{id}")
    public Map<String, Object> deleteNode(@PathVariable String id) {
        intentTreePort.delete(id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping("/intent-tree/batch/enable")
    public Map<String, Object> batchEnable(@RequestBody IntentNodeBatchRequest request) {
        intentTreePort.batchEnable(Objects.requireNonNull(request, "request must not be null").ids());
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping("/intent-tree/batch/disable")
    public Map<String, Object> batchDisable(@RequestBody IntentNodeBatchRequest request) {
        intentTreePort.batchDisable(Objects.requireNonNull(request, "request must not be null").ids());
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PostMapping("/intent-tree/batch/delete")
    public Map<String, Object> batchDelete(@RequestBody IntentNodeBatchRequest request) {
        intentTreePort.batchDelete(Objects.requireNonNull(request, "request must not be null").ids());
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }
}
