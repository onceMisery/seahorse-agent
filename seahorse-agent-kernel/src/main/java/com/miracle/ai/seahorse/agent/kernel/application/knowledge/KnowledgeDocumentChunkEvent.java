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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 文档分块任务事件。
 *
 * @param docId    文档 ID
 * @param kbId     知识库 ID
 * @param operator   操作人
 * @param pipelineId 入库流水线 ID
 */
public record KnowledgeDocumentChunkEvent(
        Long docId,
        Long kbId,
        String operator,
        String pipelineId
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造不可变事件。
     */
    public KnowledgeDocumentChunkEvent {
        operator = Objects.requireNonNullElse(operator, "");
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
    }
}