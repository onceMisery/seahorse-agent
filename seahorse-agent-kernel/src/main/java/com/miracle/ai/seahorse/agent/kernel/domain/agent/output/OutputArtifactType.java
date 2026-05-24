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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

/**
 * Agent 最终输出 artifact 的类型。
 *
 * <p>Phase D Slice 1a 仅治理 {@link #JSON} 与 {@link #PLAIN_TEXT}，后续切片再扩展
 * Markdown、Mermaid、DDL 等结构化产物。
 */
public enum OutputArtifactType {

    /**
     * 纯文本回答；仅做长度等弱校验，不做结构校验。
     */
    PLAIN_TEXT,

    /**
     * JSON 结构化回答，参与 schema 校验。
     */
    JSON,

    /**
     * Markdown 结构化回答；Slice 1d 起参与必备章节校验。
     */
    MARKDOWN,

    /**
     * Mermaid 图形回答；Slice 1d 起参与基础语法校验。
     */
    MERMAID,

    /**
     * DDL 语句回答；Slice 1b 起参与危险语句黑名单校验。
     */
    DDL
}
