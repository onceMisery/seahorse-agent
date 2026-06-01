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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;

import java.util.List;

/**
 * 知识库 Chunk 管理入站端口。
 */
public interface KnowledgeChunkInboundPort {

    KnowledgeChunkPage page(Long docId, KnowledgeChunkPageCommand command);

    KnowledgeChunkRecord create(Long docId, CreateKnowledgeChunkCommand command);

    void update(Long docId, Long chunkId, UpdateKnowledgeChunkCommand command);

    void delete(Long docId, Long chunkId);

    void enable(Long docId, Long chunkId, boolean enabled, String operator);

    void batchEnable(Long docId, List<Long> chunkIds, boolean enabled, String operator);
}