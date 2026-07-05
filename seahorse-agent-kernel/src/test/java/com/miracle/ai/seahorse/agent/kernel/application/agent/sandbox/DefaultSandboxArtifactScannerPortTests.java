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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSandboxArtifactScannerPortTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCM_MEDIA_TYPE = "application/vnd.ms-word.document.macroenabled.12";
    private final DefaultSandboxArtifactScannerPort scanner = new DefaultSandboxArtifactScannerPort();

    @Test
    void shouldDescribeBoundedLocalScannerPolicyWithoutRawFindingPersistence() {
        SandboxArtifactScannerPolicy policy = scanner.describePolicy();

        assertEquals("default-local-bounded", policy.scannerId());
        assertEquals("LOCAL_METADATA_AND_BOUNDED_CONTENT", policy.scannerMode());
        assertTrue(policy.failClosed());
        assertFalse(policy.rawFindingValuesPersisted());
        assertEquals(256 * 1024, policy.maxContentScanBytes());
        assertEquals(256 * 1024, policy.maxBinarySignatureScanBytes());
        assertEquals(128, policy.maxArchiveScanEntries());
        assertEquals(256 * 1024, policy.maxArchiveEntryScanBytes());
        assertEquals(32L * 1024L * 1024L, policy.maxCompressedArchiveDecompressedBytes());
        assertTrue(policy.promptSafeMediaTypes().contains("text/*"));
        assertTrue(policy.downloadOnlyMediaTypes().contains("application/gzip"));
        assertTrue(policy.downloadOnlyMediaTypes().contains("application/x-gzip"));
        assertTrue(policy.downloadOnlyMediaTypes().contains("application/zip"));
        assertTrue(policy.downloadOnlyMediaTypes().contains("application/x-tar"));
        assertTrue(policy.binarySignatureScannedMediaTypes().contains("application/pdf"));
        assertTrue(policy.binarySignatureScannedMediaTypes().contains("application/gzip"));
        assertTrue(policy.binarySignatureScannedMediaTypes().contains("application/x-gzip"));
        assertTrue(policy.binarySignatureScannedMediaTypes().contains("application/x-tar"));
        assertTrue(policy.archiveScannedMediaTypes().contains(DOCX_MEDIA_TYPE));
        assertTrue(policy.archiveScannedMediaTypes().contains("application/gzip"));
        assertTrue(policy.archiveScannedMediaTypes().contains("application/x-gzip"));
        assertTrue(policy.archiveScannedMediaTypes().contains("application/x-tar"));
        assertTrue(policy.blockedCategories().contains("OFFICE_MACRO"));
        assertTrue(policy.blockedCategories().contains("PDF_ACTIVE_CONTENT"));
        assertTrue(policy.unsupportedCapabilities().contains("external virus scanning"));
    }

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
    void shouldBlockPdfArtifactWithLaunchAction(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("launch-report.pdf");
        Files.writeString(output, "%PDF-1.7\n1 0 obj\n<< /Launch 2 0 R >>\nendobj", StandardCharsets.ISO_8859_1);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/pdf")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("Launch"));
    }

    @Test
    void shouldBlockPdfArtifactWithImportDataAction(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("import-report.pdf");
        Files.writeString(output, "%PDF-1.7\n1 0 obj\n<< /ImportData 2 0 R >>\nendobj", StandardCharsets.ISO_8859_1);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/pdf")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("ImportData"));
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
    void shouldBlockZipArchiveWithEmbeddedPdfFileAttachment(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output,
                "docs/report.pdf",
                "%PDF-1.7\n1 0 obj\n<< /EmbeddedFile 2 0 R >>\nendobj".getBytes(StandardCharsets.ISO_8859_1));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("EmbeddedFile"));
    }

    @Test
    void shouldBlockZipArchiveWithEmbeddedPdfExternalGoTo(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZip(output,
                "docs/report.pdf",
                "%PDF-1.7\n1 0 obj\n<< /GoToE 2 0 R >>\nendobj".getBytes(StandardCharsets.ISO_8859_1));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("GoToE"));
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
    void shouldBlockZipArchiveWithUnsafeDirectoryPath(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.zip");
        writeZipDirectory(output, "../outside/");

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/zip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("unsafe archive entry", result.summary());
        assertEquals("ARCHIVE_UNSAFE_ENTRY", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldPassCleanTarArchiveAsDownloadOnlyArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar");
        writeTar(output, "docs/readme.txt", "safe tar marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-tar")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldBlockTarArchiveWithExecutableEntryName(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar");
        writeTar(output, "bin/payload.exe", "not actually executed".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-tar")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive executable content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_EXECUTABLE_BINARY", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("payload.exe"));
    }

    @Test
    void shouldBlockTarArchiveWithEmbeddedPdfActiveContent(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar");
        writeTar(output,
                "docs/report.pdf",
                "%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R >>\nendobj".getBytes(StandardCharsets.ISO_8859_1));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-tar")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive pdf active content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_PDF_ACTIVE_CONTENT", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("OpenAction"));
    }

    @Test
    void shouldBlockTarArchiveWithUnsafeDirectoryPath(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar");
        writeTar(output, "../outside/", new byte[0], (byte) '5');

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-tar")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("unsafe archive entry", result.summary());
        assertEquals("ARCHIVE_UNSAFE_ENTRY", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldFailClosedWhenTarHeaderChecksumIsInvalid(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar");
        writeTar(output, "docs/readme.txt", "safe tar marker".getBytes(StandardCharsets.UTF_8));
        byte[] content = Files.readAllBytes(output);
        content[148] = (byte) '1';
        Files.write(output, content);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-tar")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("archive content scan failed", result.summary());
        assertEquals("ARCHIVE_SCAN_ERROR", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldPassCleanGzipTarArchiveAsDownloadOnlyArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar.gz");
        writeTarGzip(output, "docs/readme.txt", "safe gzip tar marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/gzip")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldPassCleanXGzipTarArchiveAsDownloadOnlyArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar.gz");
        writeTarGzip(output, "docs/readme.txt", "safe x-gzip tar marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/x-gzip")));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
        assertEquals("metadata scan passed", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("CLEAN", redactionSummary.path("decision").asText());
        assertEquals(true, redactionSummary.path("contentScanned").asBoolean());
    }

    @Test
    void shouldBlockGzipTarArchiveWithExecutableEntryName(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tgz");
        writeTarGzip(output, "bin/payload.exe", "not actually executed".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/gzip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
        assertEquals("archive executable content", result.summary());
        JsonNode redactionSummary = redactionSummary(result);
        assertEquals("ARCHIVE_EXECUTABLE_BINARY", redactionSummary.path("categories").get(0).asText());
        assertEquals(-1, result.redactionSummaryJson().indexOf("payload.exe"));
    }

    @Test
    void shouldFailClosedWhenGzipMediaDoesNotUseTarGzipFilename(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.gz");
        writeTarGzip(output, "docs/readme.txt", "safe gzip marker".getBytes(StandardCharsets.UTF_8));

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/gzip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("archive content scan failed", result.summary());
        assertEquals("ARCHIVE_SCAN_ERROR", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldFailClosedWhenGzipTarArchiveIsMalformed(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar.gz");
        Files.writeString(output, "not gzip content", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/gzip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("archive content scan failed", result.summary());
        assertEquals("ARCHIVE_SCAN_ERROR", redactionSummary(result).path("categories").get(0).asText());
    }

    @Test
    void shouldFailClosedWhenGzipTarDecompressedContentExceedsLimit(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("bundle.tar.gz");
        writeTarGzipWithZeroEntry(output, "docs/large.bin", 33L * 1024L * 1024L);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output, "application/gzip")));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
        assertEquals("archive content scan failed", result.summary());
        assertEquals("ARCHIVE_SCAN_ERROR", redactionSummary(result).path("categories").get(0).asText());
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

    private static void writeZipDirectory(Path path, String entryName) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.closeEntry();
        }
    }

    private static void writeTar(Path path, String entryName, byte[] content) throws Exception {
        Files.write(path, tarBytes(entryName, content, (byte) '0'));
    }

    private static void writeTar(Path path, String entryName, byte[] content, byte typeFlag) throws Exception {
        Files.write(path, tarBytes(entryName, content, typeFlag));
    }

    private static void writeTarGzip(Path path, String entryName, byte[] content) throws Exception {
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path))) {
            gzip.write(tarBytes(entryName, content, (byte) '0'));
        }
    }

    private static void writeTarGzipWithZeroEntry(Path path, String entryName, long size) throws Exception {
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path))) {
            byte[] header = tarHeader(entryName, size, (byte) '0');
            gzip.write(header);
            writeZeroBytes(gzip, size);
            long padding = (512 - (size % 512)) % 512;
            writeZeroBytes(gzip, padding);
            gzip.write(new byte[1024]);
        }
    }

    private static byte[] tarBytes(String entryName, byte[] content, byte typeFlag) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tarHeader(entryName, content.length, typeFlag));
        output.write(content);
        int padding = (512 - (content.length % 512)) % 512;
        output.write(new byte[padding]);
        output.write(new byte[1024]);
        return output.toByteArray();
    }

    private static byte[] tarHeader(String entryName, long size, byte typeFlag) {
        byte[] header = new byte[512];
        writeAscii(header, 0, 100, entryName);
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0);
        for (int index = 148; index < 156; index++) {
            header[index] = (byte) ' ';
        }
        header[156] = typeFlag;
        writeAscii(header, 257, 6, "ustar");
        writeAscii(header, 263, 2, "00");
        int checksum = 0;
        for (byte value : header) {
            checksum += value & 0xFF;
        }
        writeAscii(header, 148, 6, String.format("%06o", checksum));
        header[154] = 0;
        header[155] = (byte) ' ';
        return header;
    }

    private static void writeZeroBytes(OutputStream output, long count) throws Exception {
        byte[] buffer = new byte[8192];
        long remaining = count;
        while (remaining > 0) {
            int bytes = (int) Math.min(buffer.length, remaining);
            output.write(buffer, 0, bytes);
            remaining -= bytes;
        }
    }

    private static void writeAscii(byte[] target, int offset, int length, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        int bytesToCopy = Math.min(source.length, length);
        System.arraycopy(source, 0, target, offset, bytesToCopy);
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        writeAscii(target, offset, length - 1, String.format("%0" + (length - 1) + "o", value));
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
