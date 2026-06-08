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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 跨版本质量对比 Web adapter。
 *
 * <p>控制器只负责把管理端请求转换成统一对比命令；
 * 治理报表查询、检索评测和最终组合口径均由 kernel 端口处理。
 */
@RestController
public class SeahorseVersionQualityComparisonController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<VersionQualityComparisonInboundPort> comparisonPortProvider;

    public SeahorseVersionQualityComparisonController(ObjectProvider<VersionQualityComparisonInboundPort> comparisonPortProvider) {
        this.comparisonPortProvider = comparisonPortProvider;
    }

    @PostMapping("/knowledge-base/{kb-id}/version-quality/compare")
    public Map<String, Object> compare(@PathVariable("kb-id") String kbId,
                                       @RequestBody(required = false) VersionQualityComparisonRequest request) {
        VersionQualityComparisonRequest safeRequest = request == null
                ? new VersionQualityComparisonRequest("", 5, null, "", "", null, "", "", null)
                : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, comparisonPort().compare(safeRequest.toCommand(kbId)));
    }

    private VersionQualityComparisonInboundPort comparisonPort() {
        VersionQualityComparisonInboundPort port = comparisonPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }
}
