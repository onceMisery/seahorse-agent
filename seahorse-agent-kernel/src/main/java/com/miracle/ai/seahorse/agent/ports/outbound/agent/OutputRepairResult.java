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

import java.util.Objects;

/**
 * 输出自愈结果。
 *
 * @param repairedContent 修复后的内容；为空字符串表示无修复结果
 * @param notes           修复过程的说明，可选；observation 会记录长度统计而非全文
 */
public record OutputRepairResult(String repairedContent, String notes) {

    public OutputRepairResult {
        repairedContent = Objects.requireNonNullElse(repairedContent, "");
        notes = Objects.requireNonNullElse(notes, "");
    }

    public boolean hasRepairedContent() {
        return !repairedContent.isBlank();
    }

    public static OutputRepairResult empty() {
        return new OutputRepairResult("", "");
    }

    public static OutputRepairResult of(String repairedContent) {
        return new OutputRepairResult(repairedContent, "");
    }
}
