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

package com.miracle.ai.seahorse.agent.ports.inbound.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;

import java.util.List;

/**
 * 知识库文档入站端口。
 *
 * <p>Web、Pulsar 消费者或批处理任务只依赖该端口触发文档上传与入库，不再直接耦合旧 knowledge service。
 */
public interface KnowledgeDocumentInboundPort {

    /**
     * 上传并创建待入库文档。
     *
     * @param command 上传命令
     * @return 文档记录
     */
    KnowledgeDocumentRecord upload(UploadKnowledgeDocumentCommand command);

    /**
     * 将文档置为运行中并发送分块任务消息。
     *
     * @param docId    文档 ID
     * @param operator 操作人
     */
    void startChunk(String docId, String operator);

    /**
     * 使用指定流水线执行文档入库。
     *
     * @param docId    文档 ID
     * @param pipeline 入库流水线
     * @param operator 操作人
     */
    void executeChunk(String docId, PipelineDefinition pipeline, String operator);

    /**
     * 查询文档详情。
     *
     * @param docId 文档 ID
     * @return 文档详情
     */
    KnowledgeDocumentDetail queryById(String docId);

    /**
     * 分页查询知识库文档。
     *
     * @param kbId    知识库 ID
     * @param command 分页命令
     * @return 分页结果
     */
    KnowledgeDocumentPage page(String kbId, KnowledgeDocumentPageCommand command);

    /**
     * 搜索文档。
     *
     * @param keyword 关键字
     * @param limit   最大返回数量
     * @return 文档摘要
     */
    List<KnowledgeDocumentSummary> search(String keyword, int limit);

