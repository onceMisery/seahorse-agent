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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 元数据抽取结果只读查询 Web adapter。
 */
@RestController
@ConditionalOnBean(MetadataExtractionResultInboundPort.class)
public class SeahorseMetadataExtractionResultController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final MetadataExtractionResultInboundPort resultPort;

    public SeahorseMetadataExtractionResultController(MetadataExtractionResultInboundPort resultPort) {
        this.resultPort = Objects.requireNonNull(resultPort, "resultPort must not be null");
    }

    @GetMapping("/metadata-extraction/results")
    public Map<String, Object> pageResults(@RequestParam String tenantId,
                                           @RequestParam(required = false, defaultValue = "") String kbId,
                                           @RequestParam(required = false, defaultValue = "") String docId,
                                           @RequestParam(required = false, defaultValue = "") String jobId,
                                           @RequestParam(required = false, defaultValue = "") String status,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                resultPort.page(tenantId, kbId, docId, jobId, status, current, size));
    }

    @GetMapping("/metadata-extraction/results/{result-id}")
    public Map<String, Object> queryById(@PathVariable("result-id") String resultId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, resultPort.queryById(resultId));
    }
}
