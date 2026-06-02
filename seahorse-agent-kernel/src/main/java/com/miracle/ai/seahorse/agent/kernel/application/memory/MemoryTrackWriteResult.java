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

import java.util.List;
import java.util.Objects;

/**
 * {@link MemoryTrackWriteService} 多步写入的精确结果报告。
 *
 * <p>每一步的成功/失败被独立记录，调用方可据此判断是否存在部分失败。
 * 第 3 步（markObsolete）为"尽力而为"操作，其失败不影响整体成功判定。
 */
public record MemoryTrackWriteResult(
        boolean correctionWritten,
        boolean profileWritten,
        boolean obsoleteMarked,
        List<String> operations
) {

    public MemoryTrackWriteResult {
        operations = List.copyOf(Objects.requireNonNullElse(operations, List.of()));
    }

    /**
     * 核心写入（correction + profile）是否全部成功。
     */
    public boolean coreSuccess() {
        return correctionWritten && profileWritten;
    }
}
