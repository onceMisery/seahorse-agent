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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Schema 使用情况报表 Web 适配器。
 */
@RestController
public class SeahorseMetadataSchemaUsageController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<MetadataSchemaUsageInboundPort> schemaUsagePortProvider;

    public SeahorseMetadataSchemaUsageController(ObjectProvider<MetadataSchemaUsageInboundPort> schemaUsagePortProvider) {
        this.schemaUsagePortProvider = schemaUsagePortProvider;
    }

    @GetMapping("/knowledge-base/{kb-id}/metadata-schema/usage-report")
    public Map<String, Object> report(@PathVariable("kb-id") String kbId,
                                      @RequestParam String tenantId,
                                      @RequestParam(value = "schemaVersion", required = false) Integer schemaVersion) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                schemaUsagePort().report(tenantId, kbId, schemaVersion));
    }

    private MetadataSchemaUsageInboundPort schemaUsagePort() {
        MetadataSchemaUsageInboundPort port = schemaUsagePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }
}
