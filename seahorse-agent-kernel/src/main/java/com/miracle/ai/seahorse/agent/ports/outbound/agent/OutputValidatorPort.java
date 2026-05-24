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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;

/**
 * 输出 validator 出站端口。
 *
 * <p>每个具体实现负责一种 artifact 类型（JSON、DDL、Markdown、Mermaid 等）。Slice 1a
 * 只接入 JSON validator；后续切片再扩展。
 */
public interface OutputValidatorPort {

    /**
     * 当前 validator 的标识，用于观测、记录和分派。
     */
    String name();

    /**
     * 判断 validator 是否支持本次请求所声明的 artifact 类型。
     */
    boolean supports(OutputValidationRequest request);

    /**
     * 执行校验，返回结构化结果。实现不应抛出异常表达校验失败；任何运行时异常都视为
     * validator 自身故障，由上层治理服务统一处理。
     */
    OutputValidationResult validate(OutputValidationRequest request);
}
