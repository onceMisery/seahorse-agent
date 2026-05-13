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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 知识库检索策略模板 Web adapter。
 */
@RestController
@ConditionalOnBean(RetrievalStrategyTemplateInboundPort.class)
public class SeahorseRetrievalStrategyTemplateController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final RetrievalStrategyTemplateInboundPort templatePort;

    public SeahorseRetrievalStrategyTemplateController(RetrievalStrategyTemplateInboundPort templatePort) {
        this.templatePort = Objects.requireNonNull(templatePort, "templatePort must not be null");
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-strategy-templates")
    public Map<String, Object> listTemplates(@PathVariable("kb-id") String kbId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, templatePort.listTemplates(kbId));
    }
}
