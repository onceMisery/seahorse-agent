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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserAdapterTests {

    private final TikaDocumentParserAdapter adapter = new TikaDocumentParserAdapter();

    @Test
    void shouldReturnStableMetadataForPlainText() {
        byte[] content = "hello\r\n\r\n\r\nworld".getBytes(StandardCharsets.UTF_8);
        DocumentParseResult result = adapter.parse(
                content,
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
                .containsEntry("fileName", "demo.txt")
                .containsEntry("inputSizeBytes", content.length)
                .containsEntry("textCharCount", result.text().length())
                .containsEntry("warnings", List.of());
        assertThat((Long) result.metadata().get("parseDurationMs")).isGreaterThanOrEqualTo(0L);
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

        byte[] content = html.getBytes(StandardCharsets.UTF_8);
        DocumentParseResult result = adapter.parse(
                content,
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
                .containsEntry("inputSizeBytes", content.length)
                .containsEntry("textCharCount", result.text().length())
                .containsEntry("warnings", List.of())
                .containsKeys("Content-Type", "dc:title", "dc:creator");
        assertThat((Long) result.metadata().get("parseDurationMs")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldParsePdfDocxAndXlsxWithTika() throws Exception {
        assertParsedText(pdf("pdf invoice export policy"), "application/pdf", "policy.pdf",
                "pdf invoice export policy");
        assertParsedText(docx("docx approval matrix"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "approval.docx", "docx approval matrix");
        assertParsedText(xlsx("xlsx revenue forecast"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "forecast.xlsx", "xlsx revenue forecast");
    }

    private void assertParsedText(byte[] content, String mimeType, String fileName, String expectedText) {
        DocumentParseResult result = adapter.parse(content, mimeType, fileName, Map.of());

        assertThat(result.text()).contains(expectedText);
        assertThat(result.metadata())
                .containsEntry("parser", "tika")
                .containsEntry("mimeType", mimeType)
                .containsEntry("fileName", fileName);
    }

    private byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsx(String text) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = workbook.createSheet("Sheet1").createRow(0);
            row.createCell(0).setCellValue(text);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
