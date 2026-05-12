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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 文档解析出站端口。
 *
 * <p>L2 parser 节点只依赖该端口，PDF/Word/Excel 等具体解析能力由 L3 adapter 提供。
 */
public interface DocumentParserPort {

    /**
     * 解析文档字节流。
     *
     * @param content  原始文件字节
     * @param mimeType MIME 类型
     * @param fileName 文件名
     * @param options  节点规则附加选项
     * @return 解析后的文本和元数据
     */
    DocumentParseResult parse(byte[] content, String mimeType, String fileName, Map<String, Object> options);

    /**
     * 创建纯文本兜底解析器。
     *
     * @return 仅按 UTF-8 解码文本的解析器
     */
    static DocumentParserPort plainText() {
        return (content, mimeType, fileName, options) -> {
            byte[] safeContent = Objects.requireNonNullElse(content, new byte[0]);
            return DocumentParseResult.ofText(new String(safeContent, StandardCharsets.UTF_8));
        };
    }
}
