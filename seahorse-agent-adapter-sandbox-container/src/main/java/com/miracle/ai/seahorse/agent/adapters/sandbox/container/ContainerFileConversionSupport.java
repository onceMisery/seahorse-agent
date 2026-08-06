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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件转换请求解析、校验与脚本生成的协作者（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：文件转换请求解析、校验与脚本生成的协作者。
 */
final class ContainerFileConversionSupport {

    private static final String CSV_FORMAT = "csv";
    private static final String TSV_FORMAT = "tsv";
    private static final String JSON_FORMAT = "json";
    private static final String TXT_FORMAT = "txt";
    private static final String HTML_FORMAT = "html";
    private static final String MARKDOWN_FORMAT = "markdown";
    private static final String DOCX_FORMAT = "docx";
    private static final String ODT_FORMAT = "odt";
    private static final String ODS_FORMAT = "ods";
    private static final String ODP_FORMAT = "odp";
    private static final String XLSX_FORMAT = "xlsx";
    private static final String PPTX_FORMAT = "pptx";
    private static final String PDF_FORMAT = "pdf";
    private static final String PLAIN_ENCODING = "plain";
    private static final int MAX_FILE_CONVERSION_CONTENT_CHARS = 256 * 1024;
    private static final int MAX_FILE_CONVERSION_ARCHIVE_ENTRIES = 128;
    private static final int MAX_FILE_CONVERSION_BINARY_SCAN_BYTES = 256 * 1024;

    private final ContainerSandboxAdapterProperties properties;
    private final ObjectMapper objectMapper;

    ContainerFileConversionSupport(ContainerSandboxAdapterProperties properties,
                                   ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    FileConversionRequest parseFileConversionRequest(String input) throws IOException {
        JsonNode root = objectMapper.readTree(ContainerSandboxTextSupport.nullToEmpty(input));
        String sourceFormat = normalizedFormat(root.path("sourceFormat").asText());
        String targetFormat = normalizedFormat(root.path("targetFormat").asText());
        String contentEncoding = normalizedContentEncoding(root.path("contentEncoding").asText(PLAIN_ENCODING));
        if (!isSupportedFileConversion(sourceFormat, targetFormat)) {
            throw new UnsupportedFileConversionException(
                    "container file conversion supports csv/tsv to json, csv to xlsx, json to csv/tsv, txt to html, html to txt/docx, markdown/md to html/txt, docx/odt/odp/pdf to html/txt, docx/odt/ods/odp/pptx/xlsx to pdf, docx/odt/ods/odp/pdf/pptx/xlsx to png, pdf to ocr_txt, xlsx/ods to csv/html, and pptx to html/txt only");
        }
        if (isBinaryDocumentFormat(sourceFormat) && !ContainerSandboxTextSupport.BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException(sourceFormat + " file conversion contentEncoding must be base64");
        }
        if (!isBinaryDocumentFormat(sourceFormat) && ContainerSandboxTextSupport.BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException("base64 contentEncoding is only supported for docx/odt/ods/odp/xlsx/pptx/pdf input");
        }
        String content = root.path("content").asText("");
        if (!ContainerSandboxTextSupport.hasText(content)) {
            throw new IllegalArgumentException("file conversion content is required");
        }
        if (content.length() > MAX_FILE_CONVERSION_CONTENT_CHARS) {
            throw new IllegalArgumentException(
                    "file conversion content exceeds " + MAX_FILE_CONVERSION_CONTENT_CHARS + " chars");
        }
        return new FileConversionRequest(sourceFormat, targetFormat, contentEncoding, content);
    }
    void validateBinaryFileConversionInput(String sourceFormat, byte[] content) {
        if (DOCX_FORMAT.equals(sourceFormat)) {
            validateDocxFileConversionInput(content);
        }
        if (ODT_FORMAT.equals(sourceFormat)) {
            validateOdtFileConversionInput(content);
        }
        if (ODS_FORMAT.equals(sourceFormat)) {
            validateOdsFileConversionInput(content);
        }
        if (ODP_FORMAT.equals(sourceFormat)) {
            validateOdpFileConversionInput(content);
        }
        if (XLSX_FORMAT.equals(sourceFormat)) {
            validateXlsxFileConversionInput(content);
        }
        if (PPTX_FORMAT.equals(sourceFormat)) {
            validatePptxFileConversionInput(content);
        }
        if (PDF_FORMAT.equals(sourceFormat)) {
            validatePdfFileConversionInput(content);
        }
    }
    void validateDocxFileConversionInput(byte[] content) {
        boolean documentXmlFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("docx archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("docx archive contains unsafe entry");
                }
                if (hasDocxActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("docx active content is not supported");
                }
                if ("word/document.xml".equals(entryName)) {
                    documentXmlFound = true;
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("docx archive could not be inspected", ex);
        }
        if (!documentXmlFound) {
            throw new IllegalArgumentException("docx word/document.xml not found");
        }
    }
    void validateOdtFileConversionInput(byte[] content) {
        boolean contentXmlFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("odt archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("odt archive contains unsafe entry");
                }
                if (hasOdtActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("odt active content is not supported");
                }
                if ("content.xml".equals(entryName)) {
                    contentXmlFound = true;
                }
                if (isOdfExternalReferenceScanEntry(entryName) && hasOdfExternalReference(archive)) {
                    throw new IllegalArgumentException("odt external reference is not supported");
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("odt archive could not be inspected", ex);
        }
        if (!contentXmlFound) {
            throw new IllegalArgumentException("odt content.xml not found");
        }
    }
    void validateOdsFileConversionInput(byte[] content) {
        boolean contentXmlFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("ods archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("ods archive contains unsafe entry");
                }
                if (hasOdfActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("ods active content is not supported");
                }
                if ("content.xml".equals(entryName)) {
                    contentXmlFound = true;
                }
                if (isOdfExternalReferenceScanEntry(entryName) && hasOdfExternalReference(archive)) {
                    throw new IllegalArgumentException("ods external reference is not supported");
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("ods archive could not be inspected", ex);
        }
        if (!contentXmlFound) {
            throw new IllegalArgumentException("ods content.xml not found");
        }
    }
    void validateOdpFileConversionInput(byte[] content) {
        boolean contentXmlFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("odp archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("odp archive contains unsafe entry");
                }
                if (hasOdfActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("odp active content is not supported");
                }
                if ("content.xml".equals(entryName)) {
                    contentXmlFound = true;
                }
                if (isOdfExternalReferenceScanEntry(entryName) && hasOdfExternalReference(archive)) {
                    throw new IllegalArgumentException("odp external reference is not supported");
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("odp archive could not be inspected", ex);
        }
        if (!contentXmlFound) {
            throw new IllegalArgumentException("odp content.xml not found");
        }
    }
    void validateXlsxFileConversionInput(byte[] content) {
        boolean worksheetFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("xlsx archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("xlsx archive contains unsafe entry");
                }
                if (hasXlsxActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("xlsx active content is not supported");
                }
                if ("xl/worksheets/sheet1.xml".equals(entryName)) {
                    worksheetFound = true;
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("xlsx archive could not be inspected", ex);
        }
        if (!worksheetFound) {
            throw new IllegalArgumentException("xlsx xl/worksheets/sheet1.xml not found");
        }
    }
    void validatePdfFileConversionInput(byte[] content) {
        byte[] prefix = java.util.Arrays.copyOf(content, Math.min(content.length, MAX_FILE_CONVERSION_BINARY_SCAN_BYTES));
        String prefixText = new String(prefix, StandardCharsets.ISO_8859_1);
        if (!prefixText.startsWith("%PDF-")) {
            throw new IllegalArgumentException("pdf header not found");
        }
        if (prefixText.contains("/Encrypt")) {
            throw new IllegalArgumentException("encrypted pdf is not supported");
        }
        if (java.util.regex.Pattern.compile(
                "/(AA|EmbeddedFile|GoToE|GoToR|ImportData|JavaScript|JS|Launch|OpenAction|Rendition|RichMedia|SubmitForm)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(prefixText).find()) {
            throw new IllegalArgumentException("pdf active content is not supported");
        }
    }
    void validatePptxFileConversionInput(byte[] content) {
        boolean slideXmlFound = false;
        int inspectedEntries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++inspectedEntries > MAX_FILE_CONVERSION_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("pptx archive exceeds entry scan budget");
                }
                String entryName = normalizedArchiveEntryName(entry.getName());
                if (hasUnsafeArchivePath(entryName)) {
                    throw new IllegalArgumentException("pptx archive contains unsafe entry");
                }
                if (hasPptxActiveContentEntry(entryName)) {
                    throw new IllegalArgumentException("pptx active content is not supported");
                }
                if (entryName.matches("ppt/slides/slide\\d+\\.xml")) {
                    slideXmlFound = true;
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("pptx archive could not be inspected", ex);
        }
        if (!slideXmlFound) {
            throw new IllegalArgumentException("pptx ppt/slides/slide*.xml not found");
        }
    }
    String normalizedArchiveEntryName(String value) {
        return ContainerSandboxTextSupport.nullToEmpty(value).replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }
    boolean hasUnsafeArchivePath(String value) {
        if (!ContainerSandboxTextSupport.hasText(value) || value.indexOf('\0') >= 0) {
            return true;
        }
        return value.startsWith("/")
                || value.matches("^[a-z]:/.*")
                || value.equals("..")
                || value.startsWith("../")
                || value.endsWith("/..")
                || value.contains("/../");
    }
    boolean hasDocxActiveContentEntry(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("word/activex/")
                || value.startsWith("word/embeddings/")
                || value.startsWith("word/externallinks/")
                || value.contains("/oleobject");
    }
    boolean hasOdtActiveContentEntry(String value) {
        return hasOdfActiveContentEntry(value);
    }
    boolean hasOdfActiveContentEntry(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return false;
        }
        return value.equals("scripts/")
                || value.startsWith("scripts/")
                || value.endsWith(".class")
                || value.endsWith(".jar")
                || value.endsWith(".js")
                || value.endsWith(".sh")
                || value.endsWith(".bat")
                || value.endsWith(".cmd")
                || value.contains("basic/")
                || value.contains("objectreplacements/");
    }
    boolean hasOdfExternalReference(ZipInputStream archive) throws IOException {
        byte[] contentXml = archive.readNBytes(MAX_FILE_CONVERSION_BINARY_SCAN_BYTES + 1);
        if (contentXml.length > MAX_FILE_CONVERSION_BINARY_SCAN_BYTES) {
            throw new IllegalArgumentException("odf content xml exceeds scan budget");
        }
        String xml = new String(contentXml, StandardCharsets.UTF_8);
        return java.util.regex.Pattern.compile(
                        "(?i)(?:xlink:)?href\\s*=\\s*['\\\"]\\s*(?:https?|ftp|file):")
                .matcher(xml)
                .find();
    }
    boolean isOdfExternalReferenceScanEntry(String entryName) {
        return "content.xml".equals(entryName)
                || "styles.xml".equals(entryName)
                || "settings.xml".equals(entryName)
                || "meta.xml".equals(entryName);
    }
    boolean hasXlsxActiveContentEntry(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("xl/activex/")
                || value.startsWith("xl/embeddings/")
                || value.startsWith("xl/externallinks/")
                || value.contains("/oleobject");
    }
    boolean hasPptxActiveContentEntry(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("ppt/activex/")
                || value.startsWith("ppt/embeddings/")
                || value.startsWith("ppt/externallinks/")
                || value.contains("/oleobject");
    }
    String fileConversionScript(FileConversionRequest request) {
        return """
                import csv
                import html
                import io
                import json
                import re
                import subprocess
                import xml.etree.ElementTree as ET
                import zipfile
                import zlib
                from html.parser import HTMLParser
                from pathlib import Path

                source_format = "%s"
                target_format = "%s"
                input_path = Path("/workspace/%s")
                output_path = Path("/workspace/%s")

                def delimiter(format_name):
                    return "\\t" if format_name == "tsv" else ","

                def normalize_cell(value):
                    if value is None:
                        return ""
                    if isinstance(value, (dict, list)):
                        return json.dumps(
                            value,
                            ensure_ascii=False,
                            sort_keys=True,
                            separators=(",", ":"),
                        )
                    return str(value)

                def read_json_rows():
                    raw = json.loads(input_path.read_text(encoding="utf-8-sig"))
                    if isinstance(raw, dict) and isinstance(raw.get("rows"), list):
                        rows = raw["rows"]
                    elif isinstance(raw, dict):
                        rows = [raw]
                    elif isinstance(raw, list):
                        rows = raw
                    else:
                        raise ValueError("json input must be an object, an array, or an object with a rows array")

                    normalized_rows = []
                    fieldnames = []
                    for index, item in enumerate(rows):
                        if not isinstance(item, dict):
                            raise ValueError(f"json row {index} must be an object")
                        normalized = {}
                        for key, value in item.items():
                            name = str(key)
                            if name not in fieldnames:
                                fieldnames.append(name)
                            normalized[name] = normalize_cell(value)
                        normalized_rows.append(normalized)
                    return normalized_rows, fieldnames

                class TextExtractor(HTMLParser):
                    def __init__(self):
                        super().__init__(convert_charrefs=True)
                        self.parts = []

                    def handle_starttag(self, tag, attrs):
                        if tag == "li":
                            self.parts.append("\\n- ")
                        elif tag in ("br", "p", "div", "section", "article", "tr", "h1", "h2", "h3", "h4", "h5", "h6"):
                            self.parts.append("\\n")

                    def handle_endtag(self, tag):
                        if tag in ("p", "div", "section", "article", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6"):
                            self.parts.append("\\n")

                    def handle_data(self, data):
                        self.parts.append(data)

                    def text(self):
                        raw = "".join(self.parts)
                        lines = []
                        for line in raw.splitlines():
                            collapsed = re.sub(r"[ \\t]+", " ", line).strip()
                            if collapsed:
                                lines.append(collapsed)
                        return "\\n".join(lines) + ("\\n" if lines else "")

                def html_to_text(value):
                    parser = TextExtractor()
                    parser.feed(value)
                    parser.close()
                    return parser.text()

                def text_to_html(value):
                    return "<!doctype html>\\n<html><body><pre>" + html.escape(value) + "</pre></body></html>\\n"

                def inline_markdown(value):
                    escaped = html.escape(value)
                    escaped = re.sub(r"`([^`]+)`", r"<code>\\1</code>", escaped)
                    escaped = re.sub(r"\\*\\*([^*]+)\\*\\*", r"<strong>\\1</strong>", escaped)
                    return escaped

                def markdown_to_html(value):
                    lines = value.replace("\\r\\n", "\\n").replace("\\r", "\\n").split("\\n")
                    output = ["<!doctype html>", "<html><body>"]
                    in_list = False
                    in_code = False
                    code_lines = []

                    def close_list():
                        nonlocal in_list
                        if in_list:
                            output.append("</ul>")
                            in_list = False

                    for line in lines:
                        stripped = line.strip()
                        if stripped.startswith("```"):
                            if in_code:
                                output.append("<pre><code>" + html.escape("\\n".join(code_lines)) + "</code></pre>")
                                code_lines = []
                                in_code = False
                            else:
                                close_list()
                                in_code = True
                            continue
                        if in_code:
                            code_lines.append(line)
                            continue
                        if not stripped:
                            close_list()
                            continue
                        heading_match = re.match(r"^(#{1,6})\\s+(.+)$", stripped)
                        if heading_match:
                            close_list()
                            level = len(heading_match.group(1))
                            output.append(f"<h{level}>" + inline_markdown(heading_match.group(2)) + f"</h{level}>")
                            continue
                        if stripped.startswith("- ") or stripped.startswith("* "):
                            if not in_list:
                                output.append("<ul>")
                                in_list = True
                            output.append("<li>" + inline_markdown(stripped[2:].strip()) + "</li>")
                            continue
                        close_list()
                        output.append("<p>" + inline_markdown(stripped) + "</p>")

                    if in_code:
                        output.append("<pre><code>" + html.escape("\\n".join(code_lines)) + "</code></pre>")
                    close_list()
                    output.extend(["</body></html>", ""])
                    return "\\n".join(output)

                def docx_to_text(path):
                    paragraphs = docx_paragraphs(path)
                    return "\\n".join(paragraphs) + ("\\n" if paragraphs else "")

                def docx_to_html(path):
                    paragraphs = docx_paragraphs(path)
                    body = "\\n".join("<p>" + html.escape(paragraph) + "</p>" for paragraph in paragraphs)
                    return "<!doctype html>\\n<html><body>\\n" + body + "\\n</body></html>\\n"

                def docx_paragraphs(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            document_info = archive.getinfo("word/document.xml")
                        except KeyError as exc:
                            raise ValueError("docx word/document.xml not found") from exc
                        if document_info.file_size > 1048576:
                            raise ValueError("docx word/document.xml exceeds extraction budget")
                        document_xml = archive.read(document_info)
                    root = ET.fromstring(document_xml)
                    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
                    paragraphs = []
                    for paragraph in root.findall(".//w:p", ns):
                        parts = []
                        for node in paragraph.findall(".//w:t", ns):
                            if node.text:
                                parts.append(node.text)
                        text = "".join(parts).strip()
                        if text:
                            paragraphs.append(text)
                    return paragraphs

                def odt_to_text(path):
                    paragraphs = odt_paragraphs(path)
                    return "\\n".join(paragraphs) + ("\\n" if paragraphs else "")

                def odt_to_html(path):
                    paragraphs = odt_paragraphs(path)
                    body = "\\n".join("<p>" + html.escape(paragraph) + "</p>" for paragraph in paragraphs)
                    return "<!doctype html>\\n<html><body>\\n" + body + "\\n</body></html>\\n"

                def odt_paragraphs(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            content_info = archive.getinfo("content.xml")
                        except KeyError as exc:
                            raise ValueError("odt content.xml not found") from exc
                        if content_info.file_size > 1048576:
                            raise ValueError("odt content.xml exceeds extraction budget")
                        content_xml = archive.read(content_info)
                    root = ET.fromstring(content_xml)
                    ns = {
                        "office": "urn:oasis:names:tc:opendocument:xmlns:office:1.0",
                        "text": "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                    }
                    body = root.find(".//office:text", ns)
                    if body is None:
                        raise ValueError("odt office text body not found")
                    paragraphs = []
                    for paragraph in body.findall(".//text:p", ns):
                        text = "".join(paragraph.itertext()).strip()
                        if text:
                            paragraphs.append(text)
                    if not paragraphs:
                        raise ValueError("odt paragraph text not found")
                    return paragraphs

                def odp_to_text(path):
                    paragraphs = odp_paragraphs(path)
                    return "\\n".join(paragraphs) + ("\\n" if paragraphs else "")

                def odp_to_html(path):
                    paragraphs = odp_paragraphs(path)
                    body = "\\n".join("<p>" + html.escape(paragraph) + "</p>" for paragraph in paragraphs)
                    return "<!doctype html>\\n<html><body>\\n" + body + "\\n</body></html>\\n"

                def odp_paragraphs(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            content_info = archive.getinfo("content.xml")
                        except KeyError as exc:
                            raise ValueError("odp content.xml not found") from exc
                        if content_info.file_size > 1048576:
                            raise ValueError("odp content.xml exceeds extraction budget")
                        content_xml = archive.read(content_info)
                    root = ET.fromstring(content_xml)
                    ns = {
                        "office": "urn:oasis:names:tc:opendocument:xmlns:office:1.0",
                        "draw": "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0",
                        "text": "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                    }
                    presentation = root.find(".//office:presentation", ns)
                    if presentation is None:
                        raise ValueError("odp presentation body not found")
                    paragraphs = []
                    for page in presentation.findall(".//draw:page", ns):
                        for paragraph in page.findall(".//text:p", ns):
                            text = "".join(paragraph.itertext()).strip()
                            if text:
                                paragraphs.append(text)
                    if not paragraphs:
                        raise ValueError("odp slide text not found")
                    return paragraphs

                def ods_rows(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            content_info = archive.getinfo("content.xml")
                        except KeyError as exc:
                            raise ValueError("ods content.xml not found") from exc
                        if content_info.file_size > 1048576:
                            raise ValueError("ods content.xml exceeds extraction budget")
                        content_xml = archive.read(content_info)
                    root = ET.fromstring(content_xml)
                    ns = {
                        "office": "urn:oasis:names:tc:opendocument:xmlns:office:1.0",
                        "table": "urn:oasis:names:tc:opendocument:xmlns:table:1.0",
                        "text": "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
                    }
                    spreadsheet = root.find(".//office:spreadsheet", ns)
                    if spreadsheet is None:
                        raise ValueError("ods spreadsheet body not found")
                    table = spreadsheet.find("table:table", ns)
                    if table is None:
                        raise ValueError("ods table not found")
                    rows = []
                    max_width = 0
                    repeat_attr = "{urn:oasis:names:tc:opendocument:xmlns:table:1.0}number-rows-repeated"
                    cell_repeat_attr = "{urn:oasis:names:tc:opendocument:xmlns:table:1.0}number-columns-repeated"
                    for row in table.findall("table:table-row", ns):
                        row_values = []
                        for cell in row.findall("table:table-cell", ns):
                            parts = []
                            for paragraph in cell.findall(".//text:p", ns):
                                text = "".join(paragraph.itertext()).strip()
                                if text:
                                    parts.append(text)
                            value = "\\n".join(parts)
                            try:
                                repeat = int(cell.get(cell_repeat_attr, "1"))
                            except ValueError:
                                repeat = 1
                            repeat = max(1, min(repeat, 64))
                            row_values.extend([value] * repeat)
                        if row_values:
                            try:
                                row_repeat = int(row.get(repeat_attr, "1"))
                            except ValueError:
                                row_repeat = 1
                            row_repeat = max(1, min(row_repeat, 64))
                            max_width = max(max_width, len(row_values))
                            for _ in range(row_repeat):
                                rows.append(list(row_values))
                        if len(rows) > 1024:
                            raise ValueError("ods worksheet rows exceed extraction budget")
                    if not rows:
                        raise ValueError("ods worksheet rows not found")
                    return [row + [""] * (max_width - len(row)) for row in rows]

                def ods_to_csv(path):
                    rows = ods_rows(path)
                    output = io.StringIO()
                    writer = csv.writer(output)
                    for row in rows:
                        writer.writerow(row)
                    return output.getvalue()

                def ods_to_html(path):
                    rows = ods_rows(path)
                    output = ["<!doctype html>", "<html><body>", "<table>"]
                    for row in rows:
                        output.append("<tr>" + "".join("<td>" + html.escape(cell) + "</td>" for cell in row) + "</tr>")
                    output.extend(["</table>", "</body></html>", ""])
                    return "\\n".join(output)

                def xlsx_shared_strings(archive):
                    try:
                        info = archive.getinfo("xl/sharedStrings.xml")
                    except KeyError:
                        return []
                    if info.file_size > 1048576:
                        raise ValueError("xlsx sharedStrings.xml exceeds extraction budget")
                    root = ET.fromstring(archive.read(info))
                    ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
                    values = []
                    for item in root.findall(".//s:si", ns):
                        parts = []
                        for node in item.findall(".//s:t", ns):
                            if node.text:
                                parts.append(node.text)
                        values.append("".join(parts))
                    return values

                def xlsx_column_index(cell_ref):
                    letters = "".join(ch for ch in cell_ref if ch.isalpha()).upper()
                    if not letters:
                        return 0
                    index = 0
                    for ch in letters:
                        index = index * 26 + (ord(ch) - ord("A") + 1)
                    return index - 1

                def xlsx_cell_text(cell, shared_strings, ns):
                    value = cell.find("s:v", ns)
                    inline = cell.find("s:is", ns)
                    if inline is not None:
                        parts = []
                        for node in inline.findall(".//s:t", ns):
                            if node.text:
                                parts.append(node.text)
                        return "".join(parts)
                    if value is None or value.text is None:
                        return ""
                    text = value.text
                    if cell.get("t") == "s":
                        try:
                            index = int(text)
                        except ValueError:
                            return ""
                        if 0 <= index < len(shared_strings):
                            return shared_strings[index]
                        return ""
                    return text

                def xlsx_rows(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            sheet_info = archive.getinfo("xl/worksheets/sheet1.xml")
                        except KeyError as exc:
                            raise ValueError("xlsx xl/worksheets/sheet1.xml not found") from exc
                        if sheet_info.file_size > 1048576:
                            raise ValueError("xlsx sheet1.xml exceeds extraction budget")
                        shared_strings = xlsx_shared_strings(archive)
                        sheet_xml = archive.read(sheet_info)
                    root = ET.fromstring(sheet_xml)
                    ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
                    rows = []
                    max_width = 0
                    for row in root.findall(".//s:row", ns):
                        cells = {}
                        for cell in row.findall("s:c", ns):
                            ref = cell.get("r", "")
                            index = xlsx_column_index(ref)
                            cells[index] = xlsx_cell_text(cell, shared_strings, ns)
                        if cells:
                            max_index = max(cells)
                            max_width = max(max_width, max_index + 1)
                            rows.append([cells.get(index, "") for index in range(max_index + 1)])
                    if not rows:
                        raise ValueError("xlsx worksheet rows not found")
                    return [row + [""] * (max_width - len(row)) for row in rows]

                def xlsx_to_csv(path):
                    rows = xlsx_rows(path)
                    output = io.StringIO()
                    writer = csv.writer(output)
                    for row in rows:
                        writer.writerow(row)
                    return output.getvalue()

                def xlsx_to_html(path):
                    rows = xlsx_rows(path)
                    output = ["<!doctype html>", "<html><body>", "<table>"]
                    for row in rows:
                        output.append("<tr>" + "".join("<td>" + html.escape(cell) + "</td>" for cell in row) + "</tr>")
                    output.extend(["</table>", "</body></html>", ""])
                    return "\\n".join(output)

                def pptx_slide_sort_key(name):
                    match = re.search(r"slide(\\d+)\\.xml$", name)
                    return int(match.group(1)) if match else 0

                def pptx_slide_texts(path):
                    with zipfile.ZipFile(path) as archive:
                        slide_names = [
                            name for name in archive.namelist()
                            if re.match(r"ppt/slides/slide\\d+\\.xml$", name)
                        ]
                        if not slide_names:
                            raise ValueError("pptx ppt/slides/slide*.xml not found")
                        slide_names.sort(key=pptx_slide_sort_key)
                        ns = {"a": "http://schemas.openxmlformats.org/drawingml/2006/main"}
                        slides = []
                        for name in slide_names:
                            info = archive.getinfo(name)
                            if info.file_size > 1048576:
                                raise ValueError("pptx slide xml exceeds extraction budget")
                            root = ET.fromstring(archive.read(info))
                            parts = []
                            for node in root.findall(".//a:t", ns):
                                if node.text:
                                    parts.append(node.text)
                            text = " ".join(part.strip() for part in parts if part.strip()).strip()
                            if text:
                                slides.append(text)
                    if not slides:
                        raise ValueError("pptx slide text not found")
                    return slides

                def pptx_to_text(path):
                    slides = pptx_slide_texts(path)
                    return "\\n".join(slides) + "\\n"

                def pptx_to_html(path):
                    slides = pptx_slide_texts(path)
                    body = "\\n".join("<p>" + html.escape(slide) + "</p>" for slide in slides)
                    return "<!doctype html>\\n<html><body>\\n" + body + "\\n</body></html>\\n"

                def pdf_unescape_literal(value):
                    output = bytearray()
                    index = 0
                    while index < len(value):
                        current = value[index]
                        if current != 92:
                            output.append(current)
                            index += 1
                            continue
                        index += 1
                        if index >= len(value):
                            break
                        escaped = value[index]
                        if escaped == 110:
                            output.append(10)
                        elif escaped == 114:
                            output.append(13)
                        elif escaped == 116:
                            output.append(9)
                        elif escaped == 98:
                            output.append(8)
                        elif escaped == 102:
                            output.append(12)
                        elif escaped in (40, 41, 92):
                            output.append(escaped)
                        elif 48 <= escaped <= 55:
                            digits = [escaped]
                            index += 1
                            while index < len(value) and len(digits) < 3 and 48 <= value[index] <= 55:
                                digits.append(value[index])
                                index += 1
                            output.append(int(bytes(digits), 8))
                            continue
                        else:
                            output.append(escaped)
                        index += 1
                    return bytes(output).decode("latin-1", errors="replace")

                def pdf_literal_strings(data):
                    values = []
                    index = 0
                    while index < len(data):
                        if data[index] != 40:
                            index += 1
                            continue
                        depth = 1
                        index += 1
                        raw = bytearray()
                        escaped = False
                        while index < len(data) and depth > 0:
                            current = data[index]
                            if escaped:
                                raw.append(92)
                                raw.append(current)
                                escaped = False
                            elif current == 92:
                                escaped = True
                            elif current == 40:
                                depth += 1
                                raw.append(current)
                            elif current == 41:
                                depth -= 1
                                if depth > 0:
                                    raw.append(current)
                            else:
                                raw.append(current)
                            index += 1
                        if raw:
                            text = pdf_unescape_literal(bytes(raw)).strip()
                            if text:
                                values.append(text)
                    return values

                def pdf_streams(content):
                    streams = []
                    for match in re.finditer(rb"<<(.*?)>>\\s*stream\\r?\\n(.*?)\\r?\\nendstream", content, re.DOTALL):
                        dictionary = match.group(1)
                        data = match.group(2)
                        if b"FlateDecode" in dictionary:
                            data = bounded_pdf_flate_decode(data)
                        streams.append(data)
                    if not streams:
                        raise ValueError("pdf text stream not found")
                    return streams

                def bounded_pdf_flate_decode(data, limit=1048576):
                    try:
                        decompressor = zlib.decompressobj()
                        output = decompressor.decompress(data, limit + 1)
                        output += decompressor.flush(limit + 1 - len(output))
                    except zlib.error as exc:
                        raise ValueError("pdf FlateDecode stream could not be decompressed") from exc
                    if len(output) > limit or decompressor.unconsumed_tail:
                        raise ValueError("pdf FlateDecode stream exceeds decompression budget")
                    return output

                def pdf_to_text(path):
                    content = path.read_bytes()
                    if not content.startswith(b"%%PDF-"):
                        raise ValueError("pdf header not found")
                    if b"/Encrypt" in content[:262144]:
                        raise ValueError("encrypted pdf is not supported")
                    texts = []
                    for stream in pdf_streams(content):
                        if b"BT" not in stream or b"ET" not in stream:
                            continue
                        texts.extend(pdf_literal_strings(stream))
                    if not texts:
                        raise ValueError("pdf text not found")
                    return "\\n".join(texts) + "\\n"

                def pdf_to_html(path):
                    lines = [line.strip() for line in pdf_to_text(path).splitlines() if line.strip()]
                    body = "\\n".join("<p>" + html.escape(line) + "</p>" for line in lines)
                    return "<!doctype html>\\n<html><body>\\n" + body + "\\n</body></html>\\n"

                def office_to_pdf(path):
                    result = subprocess.run(
                        ["soffice", "--headless", "--nologo", "--nodefault", "--nolockcheck", "--norestore",
                         "--convert-to", "pdf", "--outdir", str(output_path.parent), str(path)],
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        timeout=25,
                    )
                    rendered = path.with_suffix(".pdf")
                    if result.returncode != 0 or not rendered.is_file() or rendered.stat().st_size == 0:
                        raise ValueError("office pdf rendering failed")
                    if rendered != output_path:
                        rendered.replace(output_path)

                def html_to_docx(path):
                    result = subprocess.run(
                        ["soffice", "--headless", "--nologo", "--nodefault", "--nolockcheck", "--norestore",
                         "--convert-to", "docx:Office Open XML Text", "--outdir", str(output_path.parent), str(path)],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                    rendered = path.with_suffix(".docx")
                    if result.returncode != 0 or not rendered.is_file() or rendered.stat().st_size == 0:
                        raise ValueError("html docx conversion failed")
                    if rendered != output_path:
                        rendered.replace(output_path)

                def csv_to_xlsx(path):
                    with path.open("r", encoding="utf-8-sig", newline="") as source:
                        for row in csv.reader(source):
                            if any(
                                (candidate := cell.lstrip()).startswith(("=", "+", "@"))
                                or (candidate.startswith("-")
                                    and re.fullmatch(r"-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?", candidate) is None)
                                for cell in row
                            ):
                                raise ValueError("csv formula content is not supported for xlsx conversion")
                    result = subprocess.run(
                        ["soffice", "--headless", "--nologo", "--nodefault", "--nolockcheck", "--norestore",
                         "--convert-to", "xlsx:Calc MS Excel 2007 XML", "--outdir", str(output_path.parent), str(path)],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                    rendered = path.with_suffix(".xlsx")
                    if result.returncode != 0 or not rendered.is_file() or rendered.stat().st_size == 0:
                        raise ValueError("csv xlsx conversion failed")
                    if rendered != output_path:
                        rendered.replace(output_path)

                def pdf_to_png(path):
                    output_base = output_path.with_suffix("")
                    result = subprocess.run(
                        ["pdftoppm", "-f", "1", "-l", "1", "-scale-to", "2048", "-png", "-singlefile",
                         str(path), str(output_base)],
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        timeout=25,
                    )
                    if result.returncode != 0 or not output_path.is_file() or output_path.stat().st_size == 0:
                        raise ValueError("pdf png rendering failed")

                def office_to_png(path):
                    rendered_pdf = output_path.with_suffix(".source.pdf")
                    result = subprocess.run(
                        ["soffice", "--headless", "--nologo", "--nodefault", "--nolockcheck", "--norestore",
                         "--convert-to", "pdf", "--outdir", str(output_path.parent), str(path)],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                    source_pdf = path.with_suffix(".pdf")
                    if result.returncode != 0 or not source_pdf.is_file() or source_pdf.stat().st_size == 0:
                        raise ValueError("office png rendering failed")
                    source_pdf.replace(rendered_pdf)
                    try:
                        output_base = output_path.with_suffix("")
                        rendered = subprocess.run(
                            ["pdftoppm", "-f", "1", "-l", "1", "-scale-to", "2048", "-png", "-singlefile",
                             str(rendered_pdf), str(output_base)],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                        if rendered.returncode != 0 or not output_path.is_file() or output_path.stat().st_size == 0:
                            raise ValueError("office png rendering failed")
                    finally:
                        rendered_pdf.unlink(missing_ok=True)

                def pdf_to_ocr_text(path):
                    image_path = output_path.with_suffix(".png")
                    render = subprocess.run(
                        ["pdftoppm", "-f", "1", "-l", "1", "-scale-to", "2048", "-png", "-singlefile",
                         str(path), str(image_path.with_suffix(""))],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                    if render.returncode != 0 or not image_path.is_file() or image_path.stat().st_size == 0:
                        raise ValueError("pdf ocr rendering failed")
                    recognized = subprocess.run(
                        ["tesseract", str(image_path), "stdout", "-l", "eng"],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=25)
                    image_path.unlink(missing_ok=True)
                    if recognized.returncode != 0:
                        raise ValueError("pdf ocr failed")
                    output_path.write_text(recognized.stdout, encoding="utf-8")

                if target_format == "json" and source_format in ("csv", "tsv"):
                    with input_path.open("r", encoding="utf-8-sig", newline="") as source:
                        reader = csv.DictReader(source, delimiter=delimiter(source_format))
                        rows = [dict(row) for row in reader]

                    output_path.write_text(
                        json.dumps(rows, ensure_ascii=False, indent=2),
                        encoding="utf-8",
                    )
                    print(f"converted {len(rows)} rows from {source_format} to json")
                elif source_format == "csv" and target_format == "xlsx":
                    csv_to_xlsx(input_path)
                    print(f"converted csv worksheet to xlsx")
                elif source_format == "json" and target_format in ("csv", "tsv"):
                    rows, fieldnames = read_json_rows()
                    with output_path.open("w", encoding="utf-8", newline="") as target:
                        writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter=delimiter(target_format))
                        writer.writeheader()
                        writer.writerows(rows)
                    print(f"converted {len(rows)} rows from json to {target_format}")
                elif source_format == "txt" and target_format == "html":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(text_to_html(raw), encoding="utf-8")
                    print(f"converted text document to html")
                elif source_format == "html" and target_format == "txt":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(html_to_text(raw), encoding="utf-8")
                    print(f"converted html document to text")
                elif source_format == "html" and target_format == "docx":
                    html_to_docx(input_path)
                    print(f"converted html document to docx")
                elif source_format == "markdown" and target_format == "html":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(markdown_to_html(raw), encoding="utf-8")
                    print(f"converted markdown document to html")
                elif source_format == "markdown" and target_format == "txt":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(html_to_text(markdown_to_html(raw)), encoding="utf-8")
                    print(f"converted markdown document to text")
                elif source_format == "docx" and target_format == "txt":
                    output_path.write_text(docx_to_text(input_path), encoding="utf-8")
                    print(f"converted docx document to text")
                elif source_format == "docx" and target_format == "html":
                    output_path.write_text(docx_to_html(input_path), encoding="utf-8")
                    print(f"converted docx document to html")
                elif source_format == "docx" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered docx document to pdf")
                elif source_format == "docx" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered docx first page to png")
                elif source_format == "odt" and target_format == "txt":
                    output_path.write_text(odt_to_text(input_path), encoding="utf-8")
                    print(f"converted odt document to text")
                elif source_format == "odt" and target_format == "html":
                    output_path.write_text(odt_to_html(input_path), encoding="utf-8")
                    print(f"converted odt document to html")
                elif source_format == "odt" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered odt document to pdf")
                elif source_format == "odt" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered odt first page to png")
                elif source_format == "odp" and target_format == "txt":
                    output_path.write_text(odp_to_text(input_path), encoding="utf-8")
                    print(f"converted odp presentation to text")
                elif source_format == "odp" and target_format == "html":
                    output_path.write_text(odp_to_html(input_path), encoding="utf-8")
                    print(f"converted odp presentation to html")
                elif source_format == "odp" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered odp presentation to pdf")
                elif source_format == "odp" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered odp first slide to png")
                elif source_format == "ods" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered ods spreadsheet to pdf")
                elif source_format == "ods" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered ods first sheet to png")
                elif source_format == "ods" and target_format == "csv":
                    output_path.write_text(ods_to_csv(input_path), encoding="utf-8")
                    print(f"converted ods spreadsheet to csv")
                elif source_format == "ods" and target_format == "html":
                    output_path.write_text(ods_to_html(input_path), encoding="utf-8")
                    print(f"converted ods spreadsheet to html")
                elif source_format == "xlsx" and target_format == "csv":
                    output_path.write_text(xlsx_to_csv(input_path), encoding="utf-8")
                    print(f"converted xlsx worksheet to csv")
                elif source_format == "xlsx" and target_format == "html":
                    output_path.write_text(xlsx_to_html(input_path), encoding="utf-8")
                    print(f"converted xlsx worksheet to html")
                elif source_format == "xlsx" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered xlsx worksheet to pdf")
                elif source_format == "xlsx" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered xlsx first sheet to png")
                elif source_format == "pptx" and target_format == "txt":
                    output_path.write_text(pptx_to_text(input_path), encoding="utf-8")
                    print(f"converted pptx presentation to text")
                elif source_format == "pptx" and target_format == "html":
                    output_path.write_text(pptx_to_html(input_path), encoding="utf-8")
                    print(f"converted pptx presentation to html")
                elif source_format == "pptx" and target_format == "pdf":
                    office_to_pdf(input_path)
                    print(f"rendered pptx presentation to pdf")
                elif source_format == "pptx" and target_format == "png":
                    office_to_png(input_path)
                    print(f"rendered pptx first slide to png")
                elif source_format == "pdf" and target_format == "txt":
                    output_path.write_text(pdf_to_text(input_path), encoding="utf-8")
                    print(f"converted pdf document to text")
                elif source_format == "pdf" and target_format == "html":
                    output_path.write_text(pdf_to_html(input_path), encoding="utf-8")
                    print(f"converted pdf document to html")
                elif source_format == "pdf" and target_format == "png":
                    pdf_to_png(input_path)
                    print(f"rendered pdf first page to png")
                elif source_format == "pdf" and target_format == "ocr_txt":
                    pdf_to_ocr_text(input_path)
                    print(f"ocr rendered pdf first page")
                else:
                    raise ValueError(f"unsupported conversion: {source_format} to {target_format}")
                """.formatted(
                request.sourceFormat(),
                request.targetFormat(),
                fileConversionInputName(request.sourceFormat()),
                fileConversionOutputName(request.targetFormat()));
    }
    boolean isSupportedFileConversion(String sourceFormat, String targetFormat) {
        return (isDelimitedFileFormat(sourceFormat) && JSON_FORMAT.equals(targetFormat))
                || (CSV_FORMAT.equals(sourceFormat) && XLSX_FORMAT.equals(targetFormat))
                || (JSON_FORMAT.equals(sourceFormat) && isDelimitedFileFormat(targetFormat))
                || (TXT_FORMAT.equals(sourceFormat) && HTML_FORMAT.equals(targetFormat))
                || (HTML_FORMAT.equals(sourceFormat) && TXT_FORMAT.equals(targetFormat))
                || (HTML_FORMAT.equals(sourceFormat) && DOCX_FORMAT.equals(targetFormat))
                || (MARKDOWN_FORMAT.equals(sourceFormat)
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat)))
                || ((DOCX_FORMAT.equals(sourceFormat)
                || ODT_FORMAT.equals(sourceFormat)
                || ODP_FORMAT.equals(sourceFormat)
                || PDF_FORMAT.equals(sourceFormat))
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat)))
                || (DOCX_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || (ODT_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || (ODS_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || (ODP_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || (XLSX_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || ((DOCX_FORMAT.equals(sourceFormat) || ODT_FORMAT.equals(sourceFormat) || ODS_FORMAT.equals(sourceFormat) || ODP_FORMAT.equals(sourceFormat) || XLSX_FORMAT.equals(sourceFormat))
                && "png".equals(targetFormat))
                || (PDF_FORMAT.equals(sourceFormat) && "png".equals(targetFormat))
                || (PPTX_FORMAT.equals(sourceFormat) && "png".equals(targetFormat))
                || (PDF_FORMAT.equals(sourceFormat) && "ocr_txt".equals(targetFormat))
                || (PPTX_FORMAT.equals(sourceFormat)
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat) || PDF_FORMAT.equals(targetFormat)))
                || ((XLSX_FORMAT.equals(sourceFormat) || ODS_FORMAT.equals(sourceFormat))
                && (CSV_FORMAT.equals(targetFormat) || HTML_FORMAT.equals(targetFormat)));
    }
    boolean isDelimitedFileFormat(String format) {
        return CSV_FORMAT.equals(format) || TSV_FORMAT.equals(format);
    }
    boolean isBinaryDocumentFormat(String sourceFormat) {
        return DOCX_FORMAT.equals(sourceFormat)
                || ODT_FORMAT.equals(sourceFormat)
                || ODS_FORMAT.equals(sourceFormat)
                || ODP_FORMAT.equals(sourceFormat)
                || XLSX_FORMAT.equals(sourceFormat)
                || PPTX_FORMAT.equals(sourceFormat)
                || PDF_FORMAT.equals(sourceFormat);
    }
    String fileConversionInputName(String sourceFormat) {
        String extension = switch (sourceFormat) {
            case MARKDOWN_FORMAT -> "md";
            case TXT_FORMAT -> "txt";
            case HTML_FORMAT -> "html";
            case DOCX_FORMAT -> "docx";
            case ODT_FORMAT -> "odt";
            case ODS_FORMAT -> "ods";
            case ODP_FORMAT -> "odp";
            case XLSX_FORMAT -> "xlsx";
            case PPTX_FORMAT -> "pptx";
            case PDF_FORMAT -> "pdf";
            default -> sourceFormat;
        };
        return "input." + extension;
    }
    String fileConversionOutputName(String targetFormat) {
        if ("ocr_txt".equals(targetFormat)) {
            return "converted.txt";
        }
        return "converted." + targetFormat;
    }
    String imageForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserImage();
        }
        return properties.getPythonImage();
    }
    String imageForExecution(SandboxRuntimeType runtimeType, String input) throws IOException {
        if (runtimeType != SandboxRuntimeType.FILE_CONVERSION) {
            return imageForRuntime(runtimeType);
        }
        FileConversionRequest request = parseFileConversionRequest(input);
        return requiresOfficeRenderer(request) ? properties.getOfficeConversionImage() : imageForRuntime(runtimeType);
    }
    static boolean requiresOfficeRenderer(FileConversionRequest request) {
        return (PDF_FORMAT.equals(request.targetFormat())
                && (DOCX_FORMAT.equals(request.sourceFormat())
                || ODT_FORMAT.equals(request.sourceFormat())
                || ODS_FORMAT.equals(request.sourceFormat())
                || ODP_FORMAT.equals(request.sourceFormat())
                || PPTX_FORMAT.equals(request.sourceFormat())
                || XLSX_FORMAT.equals(request.sourceFormat())))
                || (PDF_FORMAT.equals(request.sourceFormat())
                && ("png".equals(request.targetFormat()) || "ocr_txt".equals(request.targetFormat())))
                || ((DOCX_FORMAT.equals(request.sourceFormat())
                || ODT_FORMAT.equals(request.sourceFormat())
                || ODS_FORMAT.equals(request.sourceFormat())
                || ODP_FORMAT.equals(request.sourceFormat())
                || PPTX_FORMAT.equals(request.sourceFormat())
                || XLSX_FORMAT.equals(request.sourceFormat())) && "png".equals(request.targetFormat()))
                || (HTML_FORMAT.equals(request.sourceFormat()) && DOCX_FORMAT.equals(request.targetFormat()))
                || (CSV_FORMAT.equals(request.sourceFormat()) && XLSX_FORMAT.equals(request.targetFormat()));
    }
    String memoryForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserMemory();
        }
        return properties.getMemory();
    }
    long maxSessionFileLimit() {
        return Math.max(1L, properties.getMaxSessionFileBytes());
    }
    static String normalizedFormat(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "md".equals(normalized) ? MARKDOWN_FORMAT : normalized;
    }
    static String normalizedContentEncoding(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PLAIN_ENCODING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return ContainerSandboxTextSupport.BASE64_ENCODING.equals(normalized) ? ContainerSandboxTextSupport.BASE64_ENCODING : PLAIN_ENCODING;
    }
}
