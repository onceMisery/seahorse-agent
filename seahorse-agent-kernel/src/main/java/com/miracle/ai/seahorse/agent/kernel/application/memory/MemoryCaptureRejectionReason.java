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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import java.util.Objects;

/**
 * Slice 7 第一刀：将 {@code MemoryCaptureCandidateExtractor} 中的硬编码拒绝原因抽离为枚举。
 *
 * <p>spec §12.2 PR 顺序约束：先落 enum + 迁移现有拒绝原因，再落 properties 以避免 properties
 * 结构被未稳定的 enum 形态反复牵动。本枚举为该顺序的第一步。
 *
 * <p>每个枚举值通过 {@link #wireValue()} 提供原始 lowercase 字符串，确保对外（trace、
 * {@link MemorySemanticClassifier#classify}、ingest 拒绝消息）的契约不变。
 */
public enum MemoryCaptureRejectionReason {

    /** 内容为空或全部为空白。 */
    BLANK("blank"),
    /** 候选字符串过短（少于最小字符数）。 */
    TOO_SHORT("too_short"),
    /** 候选字符串过长（超过最大字符数）。 */
    TOO_LONG("too_long"),
    /** 候选文本被识别为疑问句。 */
    QUESTION("question"),
    /** 文本不包含任何高价值信号（profile / preference / personal fact / explicit remember）。 */
    NO_HIGH_VALUE_SIGNAL("no_high_value_signal"),
    /** 文本包含敏感凭证关键字（密码、API key 等）。 */
    SENSITIVE_CREDENTIAL("sensitive_credential");

    private final String wireValue;

    MemoryCaptureRejectionReason(String wireValue) {
        this.wireValue = Objects.requireNonNull(wireValue, "wireValue must not be null");
    }

    /**
     * 返回与原 string-based 实现一致的小写下划线 token，供 trace / API / 测试断言使用。
     */
    public String wireValue() {
        return wireValue;
    }
}
