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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import java.util.Map;
import java.util.Objects;

/**
 * 文档解析结果。
 *
 * @param text     解析得到的纯文本
 * @param metadata 解析器返回的元数据
 */
public record DocumentParseResult(String text, Map<String, Object> metadata) {

    public DocumentParseResult {
        text = Objects.requireNonNullElse(text, "");
        metadata = Objects.requireNonNullElse(metadata, Map.of());
    }

    public static DocumentParseResult ofText(String text) {
        return new DocumentParseResult(text, Map.of());
    }
}
