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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;

import java.util.Objects;

/**
 * 摄取操作日志生命周期网关：将 {@link MemoryOperationLogPort} 的幂等检查 / 失败记录
 * 与 {@link MemoryOperationCompletionWriter} 的完成写入统一收口，避免在 facade 中
 * 同时持有两个生命周期细节字段。
 *
 * <p>合约保持不变：</p>
 * <ul>
 *   <li>{@link #tryStart(MemoryOperation)} 返回 {@code false} 表示同 operationId 重复，
 *       caller 应直接返回 {@code MemoryIngestionResult.ignored("duplicate_operation")}。</li>
 *   <li>{@link #markFailed(String, String)} 不吞 reason，由 caller 之后重新抛出原异常。</li>
 *   <li>{@link #markCompleted(String, MemoryIngestionResult, MemoryClassificationResult)}
 *       聚合 status + decisionMap 写入，refined-delta metadata 由内部
 *       {@link MemoryOperationCompletionWriter} 处理。</li>
 * </ul>
 */
public final class MemoryOperationGateway {

    private final MemoryOperationLogPort operationLogPort;
    private final MemoryOperationCompletionWriter completionWriter;

    public MemoryOperationGateway(MemoryOperationLogPort operationLogPort,
                                  MemoryOperationCompletionWriter completionWriter) {
        this.operationLogPort = Objects.requireNonNull(operationLogPort, "operationLogPort must not be null");
        this.completionWriter = Objects.requireNonNull(completionWriter, "completionWriter must not be null");
    }

    public boolean tryStart(MemoryOperation operation) {
        return operationLogPort.tryStart(operation);
    }

    public void markCompleted(String operationId,
                              MemoryIngestionResult result,
                              MemoryClassificationResult classification) {
        completionWriter.markCompleted(operationId, result, classification);
    }

    public void markFailed(String operationId, String reason) {
        operationLogPort.markFailed(operationId, reason);
    }
}
