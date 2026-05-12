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

package com.miracle.ai.seahorse.agent.kernel.domain.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG Prompt 组装上下文。
 */
@Data
@Builder
public class PromptContext {

    private String question;

    private String mcpContext;

    private String kbContext;

    private List<IntentScore> mcpIntents;

    private List<IntentScore> kbIntents;

    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean hasMcp() {
        return mcpContext != null && !mcpContext.isBlank();
    }

    public boolean hasKb() {
        return kbContext != null && !kbContext.isBlank();
    }
}
