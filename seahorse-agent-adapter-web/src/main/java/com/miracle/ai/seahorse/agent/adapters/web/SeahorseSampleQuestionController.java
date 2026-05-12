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

import com.miracle.ai.seahorse.agent.ports.inbound.sample.CreateSampleQuestionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.UpdateSampleQuestionCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * Seahorse 原生示例问题 Web adapter。
 *
 * <p>支撑欢迎页随机问题和管理端分页维护。
 */
@RestController
@ConditionalOnBean(SampleQuestionInboundPort.class)
public class SeahorseSampleQuestionController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final SampleQuestionInboundPort sampleQuestionPort;

    public SeahorseSampleQuestionController(SampleQuestionInboundPort sampleQuestionPort) {
        this.sampleQuestionPort = Objects.requireNonNull(sampleQuestionPort, "sampleQuestionPort must not be null");
    }

    @GetMapping("/rag/sample-questions")
    public Map<String, Object> listRandomQuestions() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, sampleQuestionPort.listRandomQuestions());
    }

    @GetMapping("/sample-questions")
    public Map<String, Object> page(@RequestParam(required = false, defaultValue = "1") long current,
                                    @RequestParam(required = false, defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                sampleQuestionPort.page(new SampleQuestionPageCommand(current, size, keyword)));
    }

    @GetMapping("/sample-questions/{id}")
    public Map<String, Object> queryById(@PathVariable String id) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, sampleQuestionPort.queryById(id));
    }

    @PostMapping("/sample-questions")
    public Map<String, Object> create(@RequestBody SampleQuestionCreateRequest request) {
        SampleQuestionCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String id = sampleQuestionPort.create(new CreateSampleQuestionCommand(
                safeRequest.title(), safeRequest.description(), safeRequest.question()));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping("/sample-questions/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody SampleQuestionUpdateRequest request) {
        SampleQuestionUpdateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        sampleQuestionPort.update(id, new UpdateSampleQuestionCommand(
                safeRequest.title(),
                safeRequest.description(),
                safeRequest.question(),
                safeRequest.titlePresent(),
                safeRequest.descriptionPresent(),
                safeRequest.questionPresent()));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/sample-questions/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        sampleQuestionPort.delete(id);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }
}
