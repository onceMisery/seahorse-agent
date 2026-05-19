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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplatePayload;
import org.springframework.beans.factory.ObjectProvider;
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
 * 知识库检索策略模板 Web adapter。
 */
@RestController
public class SeahorseRetrievalStrategyTemplateController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<RetrievalStrategyTemplateInboundPort> templatePortProvider;

    public SeahorseRetrievalStrategyTemplateController(ObjectProvider<RetrievalStrategyTemplateInboundPort> templatePortProvider) {
        this.templatePortProvider = templatePortProvider;
    }

    @GetMapping("/knowledge-base/{kb-id}/retrieval-strategy-templates")
    public Map<String, Object> listTemplates(@PathVariable("kb-id") String kbId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, templatePortProvider.getIfAvailable().listTemplates(kbId));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-strategy-templates")
    public Map<String, Object> createTemplate(@PathVariable("kb-id") String kbId,
                                              @RequestBody RetrievalStrategyTemplatePayload request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, templatePortProvider.getIfAvailable().upsertTemplate(kbId, request));
    }

    @PutMapping("/knowledge-base/{kb-id}/retrieval-strategy-templates/{template-key}")
    public Map<String, Object> updateTemplate(@PathVariable("kb-id") String kbId,
                                              @PathVariable("template-key") String templateKey,
                                              @RequestBody RetrievalStrategyTemplatePayload request) {
        RetrievalStrategyTemplatePayload safeRequest = Objects.requireNonNull(request,
                "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                templatePortProvider.getIfAvailable().upsertTemplate(kbId, safeRequest.withTemplateKey(templateKey)));
    }

    @DeleteMapping("/knowledge-base/{kb-id}/retrieval-strategy-templates/{template-key}")
    public Map<String, Object> deleteTemplate(@PathVariable("kb-id") String kbId,
                                              @PathVariable("template-key") String templateKey) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                Map.of("deleted", templatePortProvider.getIfAvailable().deleteTemplate(kbId, templateKey)));
    }
}
