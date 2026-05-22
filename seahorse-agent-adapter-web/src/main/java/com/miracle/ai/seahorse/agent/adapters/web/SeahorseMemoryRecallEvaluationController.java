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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeahorseMemoryRecallEvaluationController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";

    private final ObjectProvider<MemoryRecallEvaluationInboundPort> evaluationPortProvider;

    public SeahorseMemoryRecallEvaluationController(
            ObjectProvider<MemoryRecallEvaluationInboundPort> evaluationPortProvider) {
        this.evaluationPortProvider = evaluationPortProvider;
    }

    @PostMapping("/memories/recall-quality/evaluate")
    public Map<String, Object> evaluate(@RequestBody(required = false) MemoryRecallEvaluationCommand command) {
        MemoryRecallEvaluationInboundPort evaluationPort = evaluationPortProvider.getIfAvailable();
        if (evaluationPort == null) {
            return Map.of(KEY_CODE, ERROR_CODE, "message", "Service not available");
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, evaluationPort.evaluate(command));
    }
}
