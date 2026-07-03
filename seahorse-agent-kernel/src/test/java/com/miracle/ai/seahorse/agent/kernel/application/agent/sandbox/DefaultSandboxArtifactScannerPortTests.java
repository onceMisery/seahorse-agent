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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSandboxArtifactScannerPortTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCM_MEDIA_TYPE = "application/vnd.ms-word.document.macroenabled.12";
    private final DefaultSandboxArtifactScannerPort scanner = new DefaultSandboxArtifactScannerPort();

    @Test
    void shouldPassCleanLocalTextArtifactWithStructuredSummary(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "plain artifact marker", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
        assertEquals(0, redactionSummary.path("categories").size());
    }

    @Test
    void shouldBlockLocalTextArtifactWithAssignedSecretAndStructuredSummary(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "api_key = 'sk-seahorse-secret-1234567890'", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("sensitive artifact content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("BLOCKED", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("blocked").asBoolean());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
        assertEquals("SECRET", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("sk-seahorse-secret"));
    }

    @Test
    void shouldBlockLocalTextArtifactWithPersonalDataAndStructuredSummary(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("profile.txt");
        Files.writeString(output, "owner email: user@example.com", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("personal data artifact content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("PERSONAL_DATA", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("user@example.com"));
    }

    @Test
    void shouldPassHarJsonArtifactThroughContentScanner(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("browser-network.har");
        Files.writeString(output, "{\"log\":{\"entries\":[{\"_blocked\":true}]}}", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/har+json")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldPassWebmVideoArtifactAsDownloadOnlyMetadataScan(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("browser-video.webm");
        Files.write(output, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "video/webm")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldBlockPromptSafeBinaryArtifactWithExecutableSignature(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("chart.png");
        Files.write(output, new byte[]{'M', 'Z', 0, 0, 0, 0});

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "image/png")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("executable binary artifact content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("BLOCKED", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
        assertEquals("EXECUTABLE_BINARY", redactionSummary.path("categories").get(0).asText());
    }

    @Test
    void shouldBlockPdfArtifactWithActiveContent(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("report.pdf");
        Files.writeString(output, "%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R >>\nendobj", StandardCharsets.ISO_8859_1);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/pdf")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("BLOCKED", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
        assertEquals("PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("OpenAction"));
    }

    @Test
    void shouldBlockPromptSafeBinaryArtifactWithSignatureMismatch(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("report.pdf");
        Files.write(output, new byte[]{'P', 'K', 0x03, 0x04, 0, 0});

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/pdf")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("binary signature mismatch", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("BLOCKED", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
        assertEquals("BINARY_SIGNATURE_MISMATCH", redactionSummary.path("categories").get(0).asText());
    }

    @Test
    void shouldBlockDownloadOnlyVideoArtifactWithExecutableSignature(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("browser-video.webm");
        Files.write(output, new byte[]{0x7F, 'E', 'L', 'F', 0, 0});

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "video/webm")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("EXECUTABLE_BINARY", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldPassCleanZipArchiveAsDownloadOnlyArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output, "docs/readme.txt", "safe archive marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldPassCleanOfficeOpenXmlArchiveAsDownloadOnlyArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("report.docx");
        writeZip(output, "word/document.xml", "<w:t>safe office document</w:t>".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, DOCX_MEDIA_TYPE)));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldBlockOfficeOpenXmlArchiveWithMacroProject(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("macro-report.docx");
        writeZip(output, "word/vbaProject.bin", "macro marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, DOCX_MEDIA_TYPE)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("office macro artifact content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("OFFICE_MACRO", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("vbaProject.bin"));
    }

    @Test
    void shouldBlockMacroEnabledOfficeArchiveByMediaType(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("macro-report.docm");
        writeZip(output, "word/document.xml", "<w:t>macro-enabled office document</w:t>".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, DOCM_MEDIA_TYPE)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("office macro artifact content", result.summary());
        assertEquals("OFFICE_MACRO", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldBlockZipArchiveWithExecutableEntryName(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output, "bin/payload.exe", "not actually executed".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive executable content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_EXECUTABLE_BINARY", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("payload.exe"));
    }

    @Test
    void shouldBlockZipArchiveWithEmbeddedExecutableSignature(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output, "docs/report.bin", new byte[]{'M', 'Z', 0, 0, 0});

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("ARCHIVE_EXECUTABLE_BINARY", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldBlockZipArchiveWithEmbeddedPdfActiveContent(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output,
                "docs/report.pdf",
                "%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R >>\nendobj".getBytes(StandardCharsets.ISO_8859_1));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("OpenAction"));
    }

    @Test
    void shouldBlockZipArchiveWithUnsafeEntryPath(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output, "../outside.txt", "unsafe path".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("unsafe archive entry", result.summary());
        assertEquals("ARCHIVE_UNSAFE_ENTRY", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldFailClosedWhenLocalTextArtifactCannotBeRead(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("missing.txt");

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(missing)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("artifact content unavailable", result.summary());
        assertEquals("CONTENT_UNAVAILABLE", redactionSummary(result).path("categories").get(0).asText());
    }

    private static JsonNode redactionSummary(SandboxArtifactScanResult result) throws Exception {
        return OBJECT_MAPPER.readTree(result.redactionSummaryJson());
    }

    private static void writeZip(Path path, String entryName, byte[] content) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content);
            output.closeEntry();
        }
    }

    private static SandboxArtifact fileArtifact(Path path) {
        return fileArtifact(path, "text/plain");
    }

    private static SandboxArtifact fileArtifact(Path path, String mediaType) {
        return new SandboxArtifact(
                "artifact-1",
                "session-1",
                "exec-1",
                path.toUri().toString(),
                mediaType,
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL,
                NOW);
    }
}
