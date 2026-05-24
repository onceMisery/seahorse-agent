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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

/**
 * 输出治理决策。
 *
 * <p>Slice 1a 仅使用 {@link #PASS}、{@link #WARN}、{@link #BLOCK}；自愈相关的
 * {@code HEALED}、{@code FAILED_AFTER_HEAL} 在 1c 切片再引入。
 */
public enum OutputValidationDecision {

    /**
     * 校验通过；调用方可直接消费内容。
     */
    PASS,

    /**
     * 校验存在告警；调用方仍可消费原始内容，但应记录告警。
     */
    WARN,

    /**
     * 校验阻断；调用方必须使用 fallback 内容或拒绝输出。
     */
    BLOCK
}
