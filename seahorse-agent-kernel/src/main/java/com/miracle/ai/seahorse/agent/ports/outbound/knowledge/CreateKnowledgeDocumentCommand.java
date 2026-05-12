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

package com.miracle.ai.seahorse.agent.ports.outbound.knowledge;

import java.util.Objects;

/**
 * 创建知识库文档记录命令。
 */
public record CreateKnowledgeDocumentCommand(
        String kbId,
        String docName,
        KnowledgeDocumentFileRef file,
        KnowledgeDocumentProcessRef process,
        String operator
) {

    /**
     * 构造不可变命令。
     */
    public CreateKnowledgeDocumentCommand {
        kbId = Objects.requireNonNullElse(kbId, "");
        docName = Objects.requireNonNullElse(docName, "");
        file = Objects.requireNonNullElse(file, new KnowledgeDocumentFileRef("", "", 0L));
        process = Objects.requireNonNullElse(process, new KnowledgeDocumentProcessRef("pending", "pipeline", ""));
        operator = Objects.requireNonNullElse(operator, "");
    }
}
