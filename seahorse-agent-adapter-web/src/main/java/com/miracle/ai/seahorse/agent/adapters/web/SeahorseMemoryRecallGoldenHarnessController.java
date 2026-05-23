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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeahorseMemoryRecallGoldenHarnessController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String KEY_MESSAGE = "message";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";
    private static final String NOT_AVAILABLE_MESSAGE = "Service not available";

    private final ObjectProvider<MemoryRecallGoldenHarnessInboundPort> harnessPortProvider;

    public SeahorseMemoryRecallGoldenHarnessController(
            ObjectProvider<MemoryRecallGoldenHarnessInboundPort> harnessPortProvider) {
        this.harnessPortProvider = harnessPortProvider;
    }

    @GetMapping("/memories/recall-quality/golden/profiles")
    public Map<String, Object> listProfiles() {
        MemoryRecallGoldenHarnessInboundPort port = harnessPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, NOT_AVAILABLE_MESSAGE);
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, port.listProfiles());
    }

    @PostMapping("/memories/recall-quality/golden/profiles/{profileName}/run")
    public Map<String, Object> runProfile(@PathVariable("profileName") String profileName) {
        MemoryRecallGoldenHarnessInboundPort port = harnessPortProvider.getIfAvailable();
        if (port == null) {
            return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, NOT_AVAILABLE_MESSAGE);
        }
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, port.runProfile(profileName));
    }
}
