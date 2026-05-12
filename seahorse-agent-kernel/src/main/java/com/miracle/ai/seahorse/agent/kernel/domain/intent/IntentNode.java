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

package com.miracle.ai.seahorse.agent.kernel.domain.intent;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * seahorse-agent 自有意图节点契约。
 */
@Data
@Builder
public class IntentNode {

    private String id;

    private String kbId;

    private String name;

    private String description;

    private String parentId;

    @Builder.Default
    private List<String> examples = new ArrayList<>();

    @Builder.Default
    private List<IntentNode> children = new ArrayList<>();

    @Builder.Default
    private String fullPath = "";

    @Builder.Default
    private IntentKind kind = IntentKind.KB;

    private String collectionName;

    private String mcpToolId;

    private Integer topK;

    private String promptSnippet;

    private String promptTemplate;

    private String paramPromptTemplate;

    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public boolean isKb() {
        return kind == null || IntentKind.KB.equals(kind);
    }

    public boolean isMcp() {
        return IntentKind.MCP.equals(kind);
    }

    public boolean isSystem() {
        return IntentKind.SYSTEM.equals(kind);
    }
}
