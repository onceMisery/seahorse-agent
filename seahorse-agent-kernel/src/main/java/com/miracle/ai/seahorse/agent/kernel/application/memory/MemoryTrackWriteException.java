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

/**
 * {@link MemoryTrackWriteService} 核心写入步骤失败时抛出的异常。
 *
 * <p>仅在第 1 步（Correction Ledger）或第 2 步（Profile upsert）失败时触发。
 * 第 3 步（markObsolete）属于"尽力而为"操作，其失败不产生此异常。
 */
public class MemoryTrackWriteException extends RuntimeException {

    private final String failedStep;

    public MemoryTrackWriteException(String failedStep, String message, Throwable cause) {
        super(message, cause);
        this.failedStep = failedStep;
    }

    /**
     * 返回失败的步骤标识（例如 "correction_upsert" 或 "profile_upsert"）。
     */
    public String failedStep() {
        return failedStep;
    }
}
