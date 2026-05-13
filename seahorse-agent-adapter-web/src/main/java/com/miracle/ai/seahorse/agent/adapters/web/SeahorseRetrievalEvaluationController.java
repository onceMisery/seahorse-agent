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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 检索质量评测 Web adapter。
 *
 * <p>控制器只接收临时评测集并转交 kernel 计算指标，不在 Web 层执行检索策略。
 */
@RestController
@ConditionalOnBean(RetrievalEvaluationInboundPort.class)
public class SeahorseRetrievalEvaluationController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final RetrievalEvaluationInboundPort evaluationPort;

    public SeahorseRetrievalEvaluationController(RetrievalEvaluationInboundPort evaluationPort) {
        this.evaluationPort = Objects.requireNonNull(evaluationPort, "evaluationPort must not be null");
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-quality/evaluate")
    public Map<String, Object> evaluate(@PathVariable("kb-id") String kbId,
                                        @RequestBody RetrievalEvaluationRequest request) {
        RetrievalEvaluationRequest safeRequest = request == null
                ? new RetrievalEvaluationRequest("", "", 5, null, java.util.List.of())
                : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, evaluationPort.evaluate(safeRequest.toCommand(kbId)));
    }

    @PostMapping("/knowledge-base/{kb-id}/retrieval-quality/compare")
    public Map<String, Object> compare(@PathVariable("kb-id") String kbId,
                                       @RequestBody RetrievalEvaluationComparisonRequest request) {
        RetrievalEvaluationComparisonRequest safeRequest = request == null
                ? new RetrievalEvaluationComparisonRequest("", "", 5, java.util.List.of(), java.util.List.of())
                : request;
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, evaluationPort.compare(safeRequest.toCommand(kbId)));
    }
}
