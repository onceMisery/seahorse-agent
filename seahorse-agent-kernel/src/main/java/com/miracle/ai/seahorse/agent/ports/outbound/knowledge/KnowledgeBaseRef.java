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
 * 可检索知识库引用。
 *
 * @param id             知识库 ID
 * @param name           知识库名称
 * @param collectionName 向量集合名称
 */
public record KnowledgeBaseRef(String id, String name, String collectionName) {

    public KnowledgeBaseRef {
        id = Objects.requireNonNullElse(id, "");
        name = Objects.requireNonNullElse(name, "");
        collectionName = Objects.requireNonNullElse(collectionName, "");
    }
}
