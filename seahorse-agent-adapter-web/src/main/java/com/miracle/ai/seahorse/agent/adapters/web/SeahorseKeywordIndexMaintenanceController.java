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

import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Seahorse 关键词索引运维 Web adapter。
 *
 * <p>这里只负责暴露管理触发入口，重建数据来源和后端写入仍由 kernel 入站端口统一编排。
 */
@RestController
public class SeahorseKeywordIndexMaintenanceController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<KeywordIndexMaintenanceInboundPort> maintenancePortProvider;

    public SeahorseKeywordIndexMaintenanceController(ObjectProvider<KeywordIndexMaintenanceInboundPort> maintenancePortProvider) {
        this.maintenancePortProvider = maintenancePortProvider;
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/keyword-index/rebuild")
    public Map<String, Object> rebuildDocument(@PathVariable("doc-id") String docId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, maintenancePortProvider.getIfAvailable().rebuildDocument(Long.parseLong(docId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/keyword-index/rebuild")
    public Map<String, Object> rebuildKnowledgeBase(@PathVariable("kb-id") String kbId,
                                                    @RequestParam(defaultValue = "50") int batchSize) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                maintenancePortProvider.getIfAvailable().rebuildKnowledgeBase(Long.parseLong(kbId), batchSize));
    }
}
