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

import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生术语映射 Web adapter。
 */
@RestController
public class SeahorseQueryTermMappingController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final QueryTermMappingInboundPort mappingPort;

    public SeahorseQueryTermMappingController(ObjectProvider<QueryTermMappingInboundPort> mappingPortProvider) {
        this.mappingPort = mappingPortProvider.getIfAvailable();
    }

    @GetMapping("/mappings")
    public Map<String, Object> page(@RequestParam(required = false, defaultValue = "1") long current,
                                    @RequestParam(required = false, defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, mappingPort.page(current, size, keyword));
    }

    @GetMapping("/mappings/{id}")
    public Map<String, Object> queryById(@PathVariable String id) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, mappingPort.queryById(id));
    }

    @PostMapping("/mappings")
    public Map<String, Object> create(@RequestBody QueryTermMappingPayload request) {
        String id = mappingPort.create(Objects.requireNonNull(request, "request must not be null"));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping("/mappings/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody QueryTermMappingPayload request) {
        mappingPort.update(id, Objects.requireNonNull(request, "request must not be null"));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/mappings/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        mappingPort.delete(id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }
}
