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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Slice 3 第一刀：将记忆派生索引（向量 / 关键字 / 图谱）的同步写入与 outbox 兜底逻辑从
 * {@code DefaultMemoryEnginePort} 中剥离，便于独立演进与单测覆盖。
 *
 * <p>职责范围：
 * <ul>
 *     <li>向量索引同步 upsert / delete；失败时落入 {@link MemoryOutboxPort}。</li>
 *     <li>按配置可选触发关键字 / 图谱索引的 outbox 推送（异步处理）。</li>
 * </ul>
 *
 * <p>操作字符串（{@link #OPERATION_VECTOR_UPSERT} 等）由 {@code DefaultMemoryEnginePort}
 * 现存测试覆盖、并写入 ingestion 结果，外部不应改名。
 */
public final class MemoryDerivedIndexDispatchService {

    public static final String OPERATION_VECTOR_UPSERT = "VECTOR_UPSERT";
    public static final String OPERATION_VECTOR_OUTBOX_ENQUEUE = "VECTOR_OUTBOX_ENQUEUE";
    public static final String OPERATION_KEYWORD_OUTBOX_ENQUEUE = "KEYWORD_OUTBOX_ENQUEUE";
    public static final String OPERATION_GRAPH_OUTBOX_ENQUEUE = "GRAPH_OUTBOX_ENQUEUE";
    public static final String OPERATION_VECTOR_DELETE = "VECTOR_DELETE";
    public static final String OPERATION_VECTOR_DELETE_OUTBOX_ENQUEUE = "VECTOR_DELETE_OUTBOX_ENQUEUE";
    public static final String OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE = "KEYWORD_DELETE_OUTBOX_ENQUEUE";
    public static final String OPERATION_GRAPH_DELETE_OUTBOX_ENQUEUE = "GRAPH_DELETE_OUTBOX_ENQUEUE";

    private static final Logger LOG = LoggerFactory.getLogger(MemoryDerivedIndexDispatchService.class);

    private final MemoryVectorPort memoryVectorPort;
    private final MemoryOutboxPort memoryOutboxPort;
    private final boolean keywordIndexOutboxEnabled;
    private final boolean graphIndexOutboxEnabled;
    private final String embeddingModel;

    public MemoryDerivedIndexDispatchService(MemoryVectorPort memoryVectorPort,
                                             MemoryOutboxPort memoryOutboxPort,
                                             boolean keywordIndexOutboxEnabled,
                                             boolean graphIndexOutboxEnabled,
                                             String embeddingModel) {
        this.memoryVectorPort = Objects.requireNonNull(memoryVectorPort, "memoryVectorPort must not be null");
        this.memoryOutboxPort = Objects.requireNonNull(memoryOutboxPort, "memoryOutboxPort must not be null");
        this.keywordIndexOutboxEnabled = keywordIndexOutboxEnabled;
        this.graphIndexOutboxEnabled = graphIndexOutboxEnabled;
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
    }

    /**
     * 同步写入向量索引；失败转 outbox。按配置追加关键字 / 图谱索引 outbox 任务。
     *
     * @return 已执行的操作标识列表（与历史 ingestion 结果保持完全兼容）
     */
    public List<String> dispatchUpsert(MemoryRecord record, String userId, String tenantId) {
        Objects.requireNonNull(record, "record must not be null");
        List<String> operations = new ArrayList<>();
        try {
            memoryVectorPort.upsert(record.id(), userId, record.content(), embeddingModel);
            operations.add(OPERATION_VECTOR_UPSERT);
        } catch (RuntimeException ex) {
            LOG.warn("记忆向量索引失败，已转入outbox: memoryId={}, userId={}, error={}",
                    record.id(), userId, Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorUpsert(
                    record,
                    userId,
                    tenantId,
                    embeddingModel,
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
            operations.add(OPERATION_VECTOR_OUTBOX_ENQUEUE);
        }
        if (keywordIndexOutboxEnabled) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordUpsert(record, userId, tenantId));
            operations.add(OPERATION_KEYWORD_OUTBOX_ENQUEUE);
        }
        if (graphIndexOutboxEnabled) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.graphUpsert(record, userId, tenantId));
            operations.add(OPERATION_GRAPH_OUTBOX_ENQUEUE);
        }
        return operations;
    }

    /**
     * 同步删除向量索引；失败转 outbox。按配置追加关键字 / 图谱索引删除 outbox 任务。
     */
    public List<String> dispatchDelete(String memoryId, String userId, String tenantId) {
        List<String> operations = new ArrayList<>();
        try {
            memoryVectorPort.delete(memoryId, userId, tenantId);
            operations.add(OPERATION_VECTOR_DELETE);
        } catch (RuntimeException ex) {
            LOG.warn("记忆向量删除失败，已转入outbox: memoryId={}, userId={}, error={}",
                    memoryId, userId, Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_VECTOR_DELETE_OUTBOX_ENQUEUE);
        }
        if (keywordIndexOutboxEnabled) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE);
        }
        if (graphIndexOutboxEnabled) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.graphDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_GRAPH_DELETE_OUTBOX_ENQUEUE);
        }
        return operations;
    }
}
