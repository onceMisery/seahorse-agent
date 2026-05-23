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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeahorseContextPackController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String SERVICE_NOT_AVAILABLE = "Service not available";

    private final ObjectProvider<ContextPackQueryInboundPort> contextPackQueryPortProvider;

    public SeahorseContextPackController(ObjectProvider<ContextPackQueryInboundPort> contextPackQueryPortProvider) {
        this.contextPackQueryPortProvider = contextPackQueryPortProvider;
    }

    @GetMapping("/api/context-packs/{contextPackId}")
    public Map<String, Object> findById(@PathVariable String contextPackId) {
        ContextPackQueryInboundPort port = requirePort();
        return ok(port.findById(contextPackId)
                .orElseThrow(() -> new IllegalArgumentException("Context pack not found")));
    }

    @GetMapping("/api/context-packs/{contextPackId}/items")
    public Map<String, Object> listItems(@PathVariable String contextPackId) {
        ContextPackQueryInboundPort port = requirePort();
        return ok(port.listItems(contextPackId));
    }

    private ContextPackQueryInboundPort requirePort() {
        ContextPackQueryInboundPort port = contextPackQueryPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE);
        }
        return port;
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }
}
