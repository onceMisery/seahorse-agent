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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;

import java.util.List;
import java.util.Objects;

/**
 * 输出自愈请求。
 *
 * @param originalRequest 触发自愈的原始治理请求
 * @param issues          上一次 validator 给出的问题列表，用于驱动模型修复
 */
public record OutputRepairRequest(OutputValidationRequest originalRequest, List<OutputValidationIssue> issues) {

    public OutputRepairRequest {
        Objects.requireNonNull(originalRequest, "originalRequest must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
