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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserAdapterTests {

    private final TikaDocumentParserAdapter adapter = new TikaDocumentParserAdapter();

    @Test
    void shouldReturnStableMetadataForPlainText() {
        DocumentParseResult result = adapter.parse(
                "hello\r\n\r\n\r\nworld".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                "demo.txt",
                Map.of());

        assertThat(result.text()).isEqualTo("hello\n\nworld");
        assertThat(result.metadata())
                .containsEntry("parser", "tika")
                .containsEntry("Content-Type", "text/plain")
                .containsEntry("contentType", "text/plain")
                .containsEntry("mimeType", "text/plain")
                .containsEntry("resourceName", "demo.txt")
                .containsEntry("fileName", "demo.txt");
    }

    @Test
    void shouldNormalizeTikaMetadataForHtml() {
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="UTF-8">
                    <title>季度复盘</title>
                    <meta name="author" content="Data Team">
                  </head>
                  <body>
                    <h1>关键指标</h1>
                    <p>收入增长稳定。</p>
                  </body>
                </html>
                """;

        DocumentParseResult result = adapter.parse(
                html.getBytes(StandardCharsets.UTF_8),
                "text/html",
                "quarterly.html",
                Map.of());

        // HTML/PDF/Office 等非纯文本格式会保留 Tika 原始 key，并补齐治理链路稳定 key。
        assertThat(result.text()).contains("关键指标", "收入增长稳定");
        assertThat(result.metadata())
                .containsEntry("parser", "tika")
                .containsEntry("title", "季度复盘")
                .containsEntry("author", "Data Team")
                .containsEntry("contentType", "text/html")
                .containsEntry("mimeType", "text/html")
                .containsEntry("resourceName", "quarterly.html")
                .containsEntry("fileName", "quarterly.html")
                .containsKeys("Content-Type", "dc:title", "dc:creator");
    }
}
