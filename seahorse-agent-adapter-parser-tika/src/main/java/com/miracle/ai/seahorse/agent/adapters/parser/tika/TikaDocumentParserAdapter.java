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

package com.miracle.ai.seahorse.agent.adapters.parser.tika;

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParseResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import org.apache.tika.Tika;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Apache Tika 文档解析适配器。
 *
 * <p>支持 PDF、Word、Excel、PPT、HTML、纯文本等常见格式。
 */
public class TikaDocumentParserAdapter implements DocumentParserPort {

    private static final Tika TIKA = new Tika();
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("[\\t\\x0B\\f\\r ]+");

    @Override
    public DocumentParseResult parse(byte[] content, String mimeType, String fileName, Map<String, Object> options) {
        byte[] safeContent = Objects.requireNonNullElse(content, new byte[0]);
        if (safeContent.length == 0) {
            return DocumentParseResult.ofText("");
        }
        if (isPlainText(mimeType, fileName)) {
            return DocumentParseResult.ofText(cleanup(new String(safeContent, StandardCharsets.UTF_8)));
        }
        return DocumentParseResult.ofText(parseWithTika(safeContent));
    }

    private String parseWithTika(byte[] content) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            return cleanup(TIKA.parseToString(inputStream));
        } catch (Exception ex) {
            throw new IllegalStateException("Tika document parse failed", ex);
        }
    }

    private boolean isPlainText(String mimeType, String fileName) {
        String safeMimeType = Objects.requireNonNullElse(mimeType, "").toLowerCase();
        if (safeMimeType.startsWith("text/")) {
            return true;
        }
        String safeFileName = Objects.requireNonNullElse(fileName, "").toLowerCase();
        return safeFileName.endsWith(".txt") || safeFileName.endsWith(".md") || safeFileName.endsWith(".markdown");
    }

    private String cleanup(String text) {
        String normalized = Objects.requireNonNullElse(text, "").replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (String line : lines) {
            builder.append(HORIZONTAL_SPACES.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        return EXCESSIVE_BLANK_LINES.matcher(builder.toString().strip()).replaceAll("\n\n");
    }
}
