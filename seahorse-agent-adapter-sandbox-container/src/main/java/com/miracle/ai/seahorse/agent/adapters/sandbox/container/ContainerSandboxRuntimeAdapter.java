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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContainerSandboxRuntimeAdapter implements SandboxRuntimePort {

    private static final String COORDINATOR_SESSION_ID_PREFIX = "sandbox_";
    private static final String LEGACY_SESSION_ID_PREFIX = "sandbox_container_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_container_";
    private static final String ARTIFACT_ID_PREFIX = "sandbox_artifact_container_";
    private static final String SCRIPT_NAME = "main.py";
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
    private static final String BASE64_ENCODING = "base64";
    private static final String PLAIN_ENCODING = "plain";
    private static final String BROWSER_ACTION_SNAPSHOT = "snapshot";
    private static final String BROWSER_ACTION_EXTRACT_TEXT = "extract_text";
    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String CONTAINER_NAME_PREFIX = "seahorse-sandbox-";
    private static final int MAX_FILE_CONVERSION_CONTENT_CHARS = 256 * 1024;
    private static final int MAX_FILE_CONVERSION_ARCHIVE_ENTRIES = 128;
    private static final int MAX_FILE_CONVERSION_BINARY_SCAN_BYTES = 256 * 1024;
    private static final int MAX_SESSION_WORKSPACE_FILES = 256;
    private static final int MAX_BROWSER_HTML_CHARS = 256 * 1024;
    private static final int MAX_BROWSER_URL_CHARS = 2048;
    private static final int MAX_BROWSER_URL_QUERY_CHARS = 512;
    private static final int MAX_BROWSER_ALLOWED_HOSTS = 16;
    private static final int MAX_BROWSER_COOKIES = 16;
    private static final int MAX_BROWSER_COOKIE_NAME_CHARS = 128;
    private static final int MAX_BROWSER_COOKIE_VALUE_CHARS = 4096;
    private static final int MAX_BROWSER_PROXY_SERVERS = 8;
    private static final int MAX_BROWSER_SESSION_STATE_CHARS = 128 * 1024;
    private static final int MAX_BROWSER_SESSION_STATE_COOKIES = 32;
    private static final int MAX_BROWSER_SESSION_STATE_ORIGINS = 16;
    private static final int MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS = 128;
    private static final int MAX_BROWSER_SESSION_STATE_NAME_CHARS = 256;
    private static final int MAX_BROWSER_SESSION_STATE_VALUE_CHARS = 8192;
    private static final Set<String> BROWSER_SESSION_STATE_KEYS = Set.of("cookies", "origins");
    private static final Set<String> BROWSER_SESSION_STATE_COOKIE_KEYS = Set.of(
            "name", "value", "domain", "path", "expires", "httpOnly", "secure", "sameSite");
    private static final Set<String> BROWSER_SESSION_STATE_ORIGIN_KEYS = Set.of("origin", "localStorage");
    private static final Set<String> BROWSER_SESSION_STATE_LOCAL_STORAGE_KEYS = Set.of("name", "value");
    private static final int DEFAULT_BROWSER_VIEWPORT_WIDTH = 1280;
    private static final int DEFAULT_BROWSER_VIEWPORT_HEIGHT = 720;
    private static final int MIN_BROWSER_VIEWPORT_SIZE = 320;
    private static final int MAX_BROWSER_VIEWPORT_SIZE = 2400;
    private static final Set<String> SENSITIVE_BROWSER_QUERY_PARAMETER_NAMES = Set.of(
            "accesstoken",
            "apikey",
            "authorization",
            "authtoken",
            "bearer",
            "bearertoken",
            "clientsecret",
            "credential",
            "credentials",
            "idtoken",
            "oauthtoken",
            "password",
            "refreshtoken",
            "secret",
            "session",
            "sessionid",
            "sessiontoken",
            "token");

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerCommandRunner commandRunner;
    private final Clock clock;
    private final Path workspaceRoot;
    private final String workspaceMountSourceRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger browserProxyCursor = new AtomicInteger();

    public ContainerSandboxRuntimeAdapter(ContainerSandboxAdapterProperties properties,
                                          ContainerCommandRunner commandRunner,
                                          Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.workspaceRoot = resolveWorkspaceRoot(properties.getWorkspaceRoot());
        this.workspaceMountSourceRoot = trimToNull(properties.getWorkspaceMountSourceRoot());
    }

    @Override
    public SandboxSession createSession(SandboxSessionRequest request) {
        SandboxSessionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        String sessionId = safeRequest.sessionId() == null
                ? LEGACY_SESSION_ID_PREFIX + SnowflakeIds.nextIdString()
                : safeRequest.sessionId();
        try {
            Files.createDirectories(workspaceForSession(sessionId));
            return SandboxSession.created(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
                    now);
        } catch (IOException ex) {
            return SandboxSession.failed(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
                    now);
        }
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        SandboxExecutionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        SandboxSession session = safeRequest.session();
        Instant startedAt = clock.instant();
        String executionId = EXECUTION_ID_PREFIX + SnowflakeIds.nextIdString();
        if (session.runtimeType() != SandboxRuntimeType.CODE_INTERPRETER
                && session.runtimeType() != SandboxRuntimeType.FILE_CONVERSION
                && session.runtimeType() != SandboxRuntimeType.BROWSER_AUTOMATION) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    "container sandbox supports CODE_INTERPRETER, FILE_CONVERSION, and BROWSER_AUTOMATION only");
        }
        try {
            Path workspace = workspaceForSession(session.sessionId());
            Files.createDirectories(workspace);
            prepareWorkspacePermissions(workspace);
            String executionImage = imageForExecution(session.runtimeType(), safeRequest.input());
            validateContainerNetworkBoundary(session.runtimeType(), safeRequest.networkRequested());
            Set<Path> excludedArtifacts = prepareWorkspace(
                    session.runtimeType(),
                    safeRequest.input(),
                    workspace,
                    safeRequest.networkRequested(),
                    safeRequest.requestedHosts(),
                    safeRequest.browserPrivateNetworkAllowedHosts());
            ContainerCommandResult commandResult = commandRunner.run(
                    containerCommand(session, workspace, safeRequest.networkRequested(), safeRequest.requestedHosts(), executionImage));
            Instant finishedAt = clock.instant();
            if (commandResult.timedOut()) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.TIMED_OUT,
                        summary("timed out", commandResult),
                        SandboxPolicyReasonCode.RUNTIME_TIMED_OUT,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.failed(execution, SandboxPolicyReasonCode.RUNTIME_TIMED_OUT);
            }
            if (commandResult.exitCode() == 0) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.SUCCEEDED,
                        summary("exitCode=0", commandResult),
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.succeeded(
                        execution,
                        collectArtifacts(session, execution.executionId(), workspace, finishedAt, excludedArtifacts));
            }
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    summary("exitCode=" + commandResult.exitCode(), commandResult));
        } catch (IOException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime io failure: " + nullToEmpty(ex.getMessage()));
        } catch (UnsupportedFileConversionException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (UnsupportedBrowserAutomationException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime invalid request: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime interrupted");
        } catch (RuntimeException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    private Set<Path> prepareWorkspace(SandboxRuntimeType runtimeType,
                                       String input,
                                       Path workspace,
                                       boolean networkRequested,
                                       List<String> requestedHosts,
                                       List<String> browserPrivateNetworkAllowedHosts) throws IOException {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (runtimeType == SandboxRuntimeType.CODE_INTERPRETER) {
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), input, StandardCharsets.UTF_8);
            return Set.of(safeWorkspace.resolve(SCRIPT_NAME));
        }
        if (runtimeType == SandboxRuntimeType.FILE_CONVERSION) {
            FileConversionRequest request = parseFileConversionRequest(input);
            byte[] binaryContent = null;
            if (BASE64_ENCODING.equals(request.contentEncoding())) {
                binaryContent = decodeBase64Content(request.content());
                validateBinaryFileConversionInput(request.sourceFormat(), binaryContent);
            }
            Path inputPath = safeWorkspace.resolve(fileConversionInputName(request.sourceFormat()));
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), fileConversionScript(request), StandardCharsets.UTF_8);
            if (binaryContent != null) {
                Files.write(inputPath, binaryContent);
            } else {
                Files.writeString(
                        inputPath,
                        request.content(),
                        StandardCharsets.UTF_8);
            }
            return Set.of(
                    safeWorkspace.resolve(SCRIPT_NAME),
                    inputPath);
        }
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            BrowserAutomationRequest request = parseBrowserAutomationRequest(
                    input,
                    browserPrivateNetworkAllowedHosts);
            validateBrowserNetworkBoundary(request, networkRequested, requestedHosts);
            Path inputPath = safeWorkspace.resolve(browserInputName());
            Path cookiesPath = safeWorkspace.resolve(browserCookiesName());
            Path sessionStateInputPath = safeWorkspace.resolve(browserSessionStateInputName());
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), browserAutomationScript(request), StandardCharsets.UTF_8);
            LinkedHashSet<Path> excluded = new LinkedHashSet<>();
            excluded.add(safeWorkspace.resolve(SCRIPT_NAME));
            if (!request.cookies().isEmpty()) {
                Files.writeString(cookiesPath, jsonForScript(request.cookies()), StandardCharsets.UTF_8);
                excluded.add(cookiesPath);
            }
            if (hasText(request.sessionStateJson())) {
                Files.writeString(sessionStateInputPath, request.sessionStateJson(), StandardCharsets.UTF_8);
                excluded.add(sessionStateInputPath);
            }
            if (!hasText(request.url())) {
                Files.writeString(
                        inputPath,
                        request.html(),
                        StandardCharsets.UTF_8);
                excluded.add(inputPath);
                return Set.copyOf(excluded);
            }
            return Set.copyOf(excluded);
        }
        throw new IllegalArgumentException("unsupported sandbox runtime type: " + runtimeType);
    }

    private static void prepareWorkspacePermissions(Path workspace) throws IOException {
        try {
            Files.setPosixFilePermissions(workspace, EnumSet.allOf(PosixFilePermission.class));
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows filesystems do not expose POSIX permissions through NIO.
        }
        java.io.File directory = workspace.toFile();
        boolean readable = directory.setReadable(true, false) || directory.canRead();
        boolean writable = directory.setWritable(true, false) || directory.canWrite();
        boolean executable = directory.setExecutable(true, false) || directory.canExecute();
        if (!readable || !writable || !executable) {
            throw new IOException("sandbox workspace permissions could not be prepared");
        }
    }

    private void validateContainerNetworkBoundary(SandboxRuntimeType runtimeType, boolean networkRequested) {
        if (!networkRequested || runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return;
        }
        throw new IllegalArgumentException(
                "container runtime network egress is only supported for browser automation");
    }

    private FileConversionRequest parseFileConversionRequest(String input) throws IOException {
        JsonNode root = objectMapper.readTree(nullToEmpty(input));
        String sourceFormat = normalizedFormat(root.path("sourceFormat").asText());
        String targetFormat = normalizedFormat(root.path("targetFormat").asText());
        String contentEncoding = normalizedContentEncoding(root.path("contentEncoding").asText(PLAIN_ENCODING));
        if (!isSupportedFileConversion(sourceFormat, targetFormat)) {
            throw new UnsupportedFileConversionException(
                    "container file conversion supports csv/tsv to json, csv to xlsx, json to csv/tsv, txt to html, html to txt/docx, markdown/md to html/txt, docx/odt/odp/pdf to html/txt, docx/odt/ods/odp/pptx/xlsx to pdf, docx/odt/ods/odp/pdf/pptx/xlsx to png, pdf to ocr_txt, xlsx/ods to csv/html, and pptx to html/txt only");
        }
        if (isBinaryDocumentFormat(sourceFormat) && !BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException(sourceFormat + " file conversion contentEncoding must be base64");
        }
        if (!isBinaryDocumentFormat(sourceFormat) && BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException("base64 contentEncoding is only supported for docx/odt/ods/odp/xlsx/pptx/pdf input");
        }
        String content = root.path("content").asText("");
        if (!hasText(content)) {
            throw new IllegalArgumentException("file conversion content is required");
        }
        if (content.length() > MAX_FILE_CONVERSION_CONTENT_CHARS) {
            throw new IllegalArgumentException(
                    "file conversion content exceeds " + MAX_FILE_CONVERSION_CONTENT_CHARS + " chars");
        }
        return new FileConversionRequest(sourceFormat, targetFormat, contentEncoding, content);
    }

    private void validateBinaryFileConversionInput(String sourceFormat, byte[] content) {
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

    private void validateDocxFileConversionInput(byte[] content) {
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

    private void validateOdtFileConversionInput(byte[] content) {
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

    private void validateOdsFileConversionInput(byte[] content) {
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

    private void validateOdpFileConversionInput(byte[] content) {
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

    private void validateXlsxFileConversionInput(byte[] content) {
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

    private void validatePdfFileConversionInput(byte[] content) {
        byte[] prefix = java.util.Arrays.copyOf(content, Math.min(content.length, MAX_FILE_CONVERSION_BINARY_SCAN_BYTES));
        String prefixText = new String(prefix, StandardCharsets.ISO_8859_1);
        if (!prefixText.startsWith("%PDF-")) {
            throw new IllegalArgumentException("pdf header not found");
        }
        if (prefixText.contains("/Encrypt")) {
            throw new IllegalArgumentException("encrypted pdf is not supported");
        }
        if (java.util.regex.Pattern.compile(
                "/(AA|EmbeddedFile|GoToE|GoToR|ImportData|JavaScript|JS|Launch|Rendition|RichMedia|SubmitForm)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(prefixText).find()) {
            throw new IllegalArgumentException("pdf active content is not supported");
        }
    }

    private void validatePptxFileConversionInput(byte[] content) {
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

    private String normalizedArchiveEntryName(String value) {
        return nullToEmpty(value).replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasUnsafeArchivePath(String value) {
        if (!hasText(value) || value.indexOf('\0') >= 0) {
            return true;
        }
        return value.startsWith("/")
                || value.matches("^[a-z]:/.*")
                || value.equals("..")
                || value.startsWith("../")
                || value.endsWith("/..")
                || value.contains("/../");
    }

    private boolean hasDocxActiveContentEntry(String value) {
        if (!hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("word/activex/")
                || value.startsWith("word/embeddings/")
                || value.startsWith("word/externallinks/")
                || value.contains("/oleobject");
    }

    private boolean hasOdtActiveContentEntry(String value) {
        return hasOdfActiveContentEntry(value);
    }

    private boolean hasOdfActiveContentEntry(String value) {
        if (!hasText(value)) {
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

    private boolean hasOdfExternalReference(ZipInputStream archive) throws IOException {
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

    private boolean isOdfExternalReferenceScanEntry(String entryName) {
        return "content.xml".equals(entryName)
                || "styles.xml".equals(entryName)
                || "settings.xml".equals(entryName)
                || "meta.xml".equals(entryName);
    }

    private boolean hasXlsxActiveContentEntry(String value) {
        if (!hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("xl/activex/")
                || value.startsWith("xl/embeddings/")
                || value.startsWith("xl/externallinks/")
                || value.contains("/oleobject");
    }

    private boolean hasPptxActiveContentEntry(String value) {
        if (!hasText(value)) {
            return false;
        }
        return value.equals("vbaproject.bin")
                || value.endsWith("/vbaproject.bin")
                || value.startsWith("ppt/activex/")
                || value.startsWith("ppt/embeddings/")
                || value.startsWith("ppt/externallinks/")
                || value.contains("/oleobject");
    }

    private BrowserAutomationRequest parseBrowserAutomationRequest(String input,
                                                                   List<String> browserPrivateNetworkAllowedHosts)
            throws IOException {
        JsonNode root = objectMapper.readTree(nullToEmpty(input));
        String action = normalizedBrowserAction(root.path("action").asText(BROWSER_ACTION_SNAPSHOT));
        if (!isSupportedBrowserAction(action)) {
            throw new UnsupportedBrowserAutomationException(
                    "container browser automation supports snapshot and extract_text actions only");
        }
        String html = root.path("html").asText("");
        String url = normalizedBrowserUrl(root.path("url").asText(""));
        String urlHost = hasText(url) ? browserUrlHost(url) : "";
        String urlOrigin = hasText(url) ? browserUrlOrigin(url, "url") : "";
        List<String> allowedHosts = normalizedBrowserAllowedHosts(root.get("allowedHosts"));
        List<BrowserCookie> cookies = normalizedBrowserCookies(
                root.get("cookies"),
                allowedHosts,
                urlHost);
        if (!hasText(url) && !hasText(html)) {
            throw new IllegalArgumentException("browser automation html or url is required");
        }
        if (hasText(url) && allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("browser automation allowedHosts is required for url mode");
        }
        if (hasText(url) && !allowedHosts.contains(urlHost)) {
            throw new IllegalArgumentException("browser automation url host must be included in allowedHosts");
        }
        if (html.length() > MAX_BROWSER_HTML_CHARS) {
            throw new IllegalArgumentException(
                    "browser automation html exceeds " + MAX_BROWSER_HTML_CHARS + " chars");
        }
        int viewportWidth = boundedInt(root,
                "viewportWidth",
                DEFAULT_BROWSER_VIEWPORT_WIDTH,
                MIN_BROWSER_VIEWPORT_SIZE,
                MAX_BROWSER_VIEWPORT_SIZE);
        int viewportHeight = boundedInt(root,
                "viewportHeight",
                DEFAULT_BROWSER_VIEWPORT_HEIGHT,
                MIN_BROWSER_VIEWPORT_SIZE,
                MAX_BROWSER_VIEWPORT_SIZE);
        boolean screenshot = root.path("screenshot").isMissingNode()
                ? BROWSER_ACTION_SNAPSHOT.equals(action)
                : root.path("screenshot").asBoolean(BROWSER_ACTION_SNAPSHOT.equals(action));
        boolean har = root.path("har").asBoolean(false);
        boolean video = root.path("video").asBoolean(false);
        boolean captureSessionState = root.path("captureSessionState").asBoolean(false);
        String sessionStateJson = normalizedBrowserSessionState(root.get("sessionState"), allowedHosts, urlHost, urlOrigin, hasText(url));
        if (captureSessionState && !hasText(url)) {
            throw new IllegalArgumentException("browser automation session state capture is only supported for url mode");
        }
        return new BrowserAutomationRequest(action,
                html,
                url,
                allowedHosts,
                cookies,
                viewportWidth,
                viewportHeight,
                screenshot,
                har,
                video,
                captureSessionState,
                sessionStateJson,
                effectiveBrowserPrivateNetworkAllowedHosts(browserPrivateNetworkAllowedHosts));
    }

    private void validateBrowserNetworkBoundary(BrowserAutomationRequest request,
                                                boolean networkRequested,
                                                List<String> requestedHosts) {
        if (request.allowedHosts().isEmpty()) {
            return;
        }
        if (!networkRequested) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts requires networkRequested=true");
        }
        Set<String> authorizedHosts = normalizedBrowserRequestedHosts(requestedHosts);
        if (!authorizedHosts.containsAll(request.allowedHosts())) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts must be included in requestedHosts");
        }
    }

    private Set<String> normalizedBrowserRequestedHosts(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        values.forEach(value -> addNormalizedBrowserHost(hosts, value));
        return Set.copyOf(hosts);
    }

    private String browserAutomationScript(BrowserAutomationRequest request) {
        List<String> browserProxyServers = normalizedBrowserProxyServers();
        String browserProxyServer = selectBrowserProxyServer(browserProxyServers);
        BrowserProxyCredentials proxyCredentials = browserProxyCredentials(browserProxyServers);
        return """
                import json
                import ipaddress
                import socket
                from datetime import datetime, timezone
                from pathlib import Path
                from urllib.parse import unquote_plus, urlparse
                from playwright.sync_api import sync_playwright

                action = "%s"
                target_url = %s
                browser_proxy_server = %s
                browser_proxy_username = %s
                browser_proxy_password = %s
                browser_proxy_pool_size = %d
                allowed_hosts = set(%s)
                private_network_allowed_hosts = set(%s)
                viewport_width = %d
                viewport_height = %d
                screenshot_enabled = %s
                har_enabled = %s
                video_enabled = %s
                capture_session_state = %s
                input_path = Path("/workspace/%s")
                cookies_path = Path("/workspace/%s")
                session_state_input_path = Path("/workspace/%s")
                result_path = Path("/workspace/%s")
                screenshot_path = Path("/workspace/%s")
                har_path = Path("/workspace/%s")
                video_dir = Path("/workspace/browser-video-recordings")
                video_path = Path("/workspace/%s")
                session_state_path = Path("/workspace/%s")
                session_summary_path = Path("/workspace/%s")
                max_session_state_bytes = %d

                def compact_text(value, limit=12000):
                    normalized = "\\n".join(line.strip() for line in value.replace("\\r", "\\n").split("\\n") if line.strip())
                    return normalized[:limit]

                def utc_now():
                    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

                sensitive_query_parameter_names = {
                    "accesstoken",
                    "apikey",
                    "authorization",
                    "authtoken",
                    "bearer",
                    "bearertoken",
                    "clientsecret",
                    "credential",
                    "credentials",
                    "idtoken",
                    "oauthtoken",
                    "password",
                    "refreshtoken",
                    "secret",
                    "session",
                    "sessionid",
                    "sessiontoken",
                    "token",
                }

                def normalized_query_parameter_name(value):
                    name = unquote_plus(value).lower()
                    bracket_index = name.find("[")
                    if bracket_index > 0:
                        name = name[:bracket_index]
                    return "".join(ch for ch in name if ch.isalnum())

                def has_credential_url_parts(url):
                    parsed = urlparse(url)
                    if parsed.username or parsed.password or parsed.fragment:
                        return True
                    for parameter in parsed.query.replace(";", "&").split("&"):
                        if not parameter:
                            continue
                        name = parameter.split("=", 1)[0]
                        if normalized_query_parameter_name(name) in sensitive_query_parameter_names:
                            return True
                    return False

                host_resolution_cache = {}

                def is_private_network_address(value):
                    try:
                        return not ipaddress.ip_address(value).is_global
                    except ValueError:
                        return True

                def resolved_host_decision(host):
                    cached = host_resolution_cache.get(host)
                    if cached is not None:
                        return cached
                    private_network_allowed = host in private_network_allowed_hosts
                    try:
                        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
                    except OSError:
                        decision = (False, "dns_resolution_failed")
                        host_resolution_cache[host] = decision
                        return decision
                    addresses = sorted({item[4][0] for item in infos if len(item) >= 5 and item[4]})
                    if not addresses:
                        decision = (False, "dns_resolution_failed")
                    elif any(is_private_network_address(address) for address in addresses):
                        decision = (
                            True,
                            "private_network_host_allowlisted",
                        ) if private_network_allowed else (
                            False,
                            "resolved_private_ip",
                        )
                    else:
                        decision = (True, "allowlisted_host")
                    host_resolution_cache[host] = decision
                    return decision

                def redacted_har_url(url):
                    if url.startswith("data:"):
                        return "data:<redacted>"
                    if url.startswith("blob:"):
                        return "blob:<redacted>"
                    if not has_credential_url_parts(url):
                        return url
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme not in ("http", "https") or not host:
                        return "<redacted-url>"
                    try:
                        port = parsed.port
                    except ValueError:
                        port = None
                    authority = host if port is None else f"{host}:{port}"
                    redacted = f"{scheme}://{authority}{parsed.path or ''}"
                    if parsed.username or parsed.password:
                        redacted += "?<redacted-userinfo>"
                    if parsed.query:
                        redacted += ("&" if "?" in redacted else "?") + "<redacted-query>"
                    if parsed.fragment:
                        redacted += "#<redacted-fragment>"
                    return redacted

                def egress_decision(url):
                    if url.startswith(("about:", "blob:", "data:")):
                        return True, "internal_scheme"
                    if has_credential_url_parts(url):
                        return False, "credential_url"
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme in ("http", "https") and host:
                        if host in allowed_hosts:
                            return resolved_host_decision(host)
                        return False, "host_not_allowlisted"
                    return False, "unsupported_url"

                def allowed_url(url):
                    allowed, _ = egress_decision(url)
                    return allowed

                def request_host(url):
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme in ("http", "https") and host:
                        return host
                    return None

                def increment(counter, key):
                    safe_key = key or "unknown"
                    counter[safe_key] = counter.get(safe_key, 0) + 1

                def blocked_navigation_reason(events):
                    for event in events:
                        if event.get("blocked"):
                            return event.get("blockedReason") or "blocked"
                    return None

                def empty_har_request(method, url):
                    return {
                        "method": method,
                        "url": url,
                        "httpVersion": "HTTP/1.1",
                        "cookies": [],
                        "headers": [],
                        "queryString": [],
                        "headersSize": -1,
                        "bodySize": 0,
                    }

                def empty_har_response(status, status_text):
                    return {
                        "status": status,
                        "statusText": status_text,
                        "httpVersion": "HTTP/1.1",
                        "cookies": [],
                        "headers": [],
                        "content": {"size": 0, "mimeType": ""},
                        "redirectURL": "",
                        "headersSize": -1,
                        "bodySize": 0,
                    }

                def build_har(events):
                    entries = []
                    for event in events:
                        status = event.get("status") or 0
                        status_text = event.get("statusText") or event.get("failure") or ("blocked" if event.get("blocked") else "")
                        entries.append({
                            "startedDateTime": event["startedDateTime"],
                            "time": 0,
                            "request": empty_har_request(event["method"], event["url"]),
                            "response": empty_har_response(status, status_text),
                            "cache": {},
                            "timings": {"send": 0, "wait": 0, "receive": 0},
                            "_resourceType": event.get("resourceType"),
                            "_blocked": bool(event.get("blocked")),
                            "_blockedReason": event.get("blockedReason"),
                            "_failure": event.get("failure"),
                        })
                    return {
                        "log": {
                            "version": "1.2",
                            "creator": {"name": "seahorse-sandbox-browser", "version": "1"},
                            "pages": [{
                                "startedDateTime": utc_now(),
                                "id": "sandbox-browser-page",
                                "title": "sandbox browser",
                                "pageTimings": {},
                            }],
                            "entries": entries,
                        }
                    }

                def build_egress_summary(events):
                    allowed_count = 0
                    blocked_count = 0
                    allowed_host_counts = {}
                    resource_type_counts = {}
                    blocked_resource_type_counts = {}
                    blocked_reason_counts = {}
                    for event in events:
                        resource_type = event.get("resourceType") or "unknown"
                        increment(resource_type_counts, resource_type)
                        if event.get("blocked"):
                            blocked_count += 1
                            increment(blocked_resource_type_counts, resource_type)
                            increment(blocked_reason_counts, event.get("blockedReason"))
                        else:
                            allowed_count += 1
                            host = event.get("host")
                            if host in allowed_hosts:
                                increment(allowed_host_counts, host)
                    return {
                        "mode": "url" if target_url else "html",
                        "networkRequested": bool(target_url),
                        "policy": "ALLOWLISTED" if target_url else "DENY_ALL",
                        "allowedHostCount": len(allowed_hosts),
                        "requestCount": len(events),
                        "continuedRequestCount": allowed_count,
                        "blockedRequestCount": blocked_count,
                        "blockedReasonCounts": blocked_reason_counts,
                        "resourceTypeCounts": resource_type_counts,
                        "blockedResourceTypeCounts": blocked_resource_type_counts,
                        "allowedHostRequestCounts": [
                            {"host": host, "requestCount": count}
                            for host, count in sorted(allowed_host_counts.items())
                        ],
                        "proxy": {
                            "enabled": bool(target_url and browser_proxy_server),
                            "authenticated": bool(target_url and browser_proxy_server and browser_proxy_username and browser_proxy_password),
                            "poolSize": browser_proxy_pool_size,
                            "rotationEnabled": bool(browser_proxy_pool_size > 1),
                        },
                    }

                def build_session_summary(state, current_url):
                    cookies = state.get("cookies") or []
                    origins = state.get("origins") or []
                    return {
                        "source": "url" if target_url else "html",
                        "targetUrl": target_url or None,
                        "url": redacted_har_url(current_url),
                        "cookies": {
                            "count": len(cookies),
                            "domains": sorted({cookie.get("domain") for cookie in cookies if cookie.get("domain")}),
                        },
                        "origins": [
                            {
                                "origin": origin.get("origin"),
                                "localStorageCount": len(origin.get("localStorage") or []),
                            }
                            for origin in origins
                            if origin.get("origin")
                        ],
                    }

                html = "" if target_url else input_path.read_text(encoding="utf-8-sig")
                browser_cookies = json.loads(cookies_path.read_text(encoding="utf-8")) if cookies_path.exists() else []
                browser_session_state = json.loads(session_state_input_path.read_text(encoding="utf-8")) if session_state_input_path.exists() else None
                network_events = []
                network_event_index = {}
                video_file = None

                with sync_playwright() as playwright:
                    browser = playwright.chromium.launch(
                        headless=True,
                        args=["--no-sandbox", "--disable-dev-shm-usage"],
                    )
                    context = None
                    try:
                        context_options = {
                            "viewport": {"width": viewport_width, "height": viewport_height},
                        }
                        if browser_session_state is not None:
                            context_options["storage_state"] = str(session_state_input_path)
                        if target_url and browser_proxy_server:
                            proxy_options = {"server": browser_proxy_server}
                            if browser_proxy_username and browser_proxy_password:
                                proxy_options["username"] = browser_proxy_username
                                proxy_options["password"] = browser_proxy_password
                            context_options["proxy"] = proxy_options
                        if video_enabled:
                            video_dir.mkdir(parents=True, exist_ok=True)
                            context_options["record_video_dir"] = str(video_dir)
                            context_options["record_video_size"] = {"width": viewport_width, "height": viewport_height}
                        context = browser.new_context(**context_options)
                        if browser_cookies:
                            context.add_cookies(browser_cookies)
                        page = context.new_page()

                        def on_request(request):
                            allowed, decision_reason = egress_decision(request.url)
                            blocked = not allowed
                            event = {
                                "startedDateTime": utc_now(),
                                "method": request.method,
                                "url": redacted_har_url(request.url),
                                "host": request_host(request.url) if allowed else None,
                                "resourceType": request.resource_type,
                                "status": 0,
                                "statusText": "",
                                "failure": None,
                                "blocked": blocked,
                                "blockedReason": None if allowed else decision_reason,
                            }
                            network_event_index[id(request)] = event
                            network_events.append(event)

                        def on_response(response):
                            event = network_event_index.get(id(response.request))
                            if event is not None:
                                event["status"] = response.status
                                event["statusText"] = response.status_text

                        def on_request_failed(request):
                            event = network_event_index.get(id(request))
                            if event is not None:
                                failure = request.failure or "request failed"
                                event["failure"] = failure
                                event["statusText"] = failure

                        def block_external(route):
                            url = route.request.url
                            if allowed_url(url):
                                route.continue_()
                            else:
                                route.abort()

                        page.on("request", on_request)
                        page.on("response", on_response)
                        page.on("requestfailed", on_request_failed)
                        page.route("**/*", block_external)
                        if target_url:
                            try:
                                page.goto(target_url, wait_until="load", timeout=10000)
                            except Exception:
                                blocked_reason = blocked_navigation_reason(network_events)
                                if blocked_reason:
                                    blocked_count = sum(1 for event in network_events if event.get("blocked"))
                                    raise RuntimeError(
                                        f"browser navigation blocked by egress policy; blockedReason={blocked_reason}; egressRequests={len(network_events)}; egressBlocked={blocked_count}"
                                    ) from None
                                raise RuntimeError("browser navigation failed") from None
                        else:
                            page.set_content(html, wait_until="load", timeout=10000)
                        if har_enabled:
                            page.wait_for_timeout(250)
                        title = page.title()
                        try:
                            body_text = page.locator("body").inner_text(timeout=3000)
                        except Exception:
                            body_text = page.content()
                        screenshot_file = None
                        if screenshot_enabled:
                            page.screenshot(path=str(screenshot_path), full_page=True)
                            screenshot_file = screenshot_path.name
                        session_summary_file = None
                        session_state_file = None
                        session_replay_summary = build_session_summary(browser_session_state, target_url) if browser_session_state is not None else None
                        if capture_session_state:
                            state = context.storage_state(path=str(session_state_path))
                            if session_state_path.stat().st_size > max_session_state_bytes:
                                session_state_path.unlink(missing_ok=True)
                                session_summary_path.unlink(missing_ok=True)
                                raise RuntimeError("browser session state capture exceeds storage budget")
                            session_summary_path.write_text(
                                json.dumps(build_session_summary(state, page.url), ensure_ascii=False, indent=2),
                                encoding="utf-8",
                            )
                            session_summary_file = session_summary_path.name
                            session_state_file = session_state_path.name
                        egress_summary = build_egress_summary(network_events)
                        result = {
                            "action": action,
                            "source": "url" if target_url else "html",
                            "title": title,
                            "url": redacted_har_url(page.url),
                            "targetUrl": target_url or None,
                            "allowedHosts": sorted(allowed_hosts),
                            "proxy": {
                                "enabled": bool(target_url and browser_proxy_server),
                                "authenticated": bool(target_url and browser_proxy_server and browser_proxy_username and browser_proxy_password),
                                "poolSize": browser_proxy_pool_size,
                                "rotationEnabled": bool(browser_proxy_pool_size > 1),
                            },
                            "egress": egress_summary,
                            "cookies": {
                                "count": len(browser_cookies),
                                "domains": sorted({cookie.get("domain") for cookie in browser_cookies if cookie.get("domain")}),
                            },
                            "text": compact_text(body_text),
                            "textLength": len(body_text),
                            "viewport": {
                                "width": viewport_width,
                                "height": viewport_height,
                            },
                            "screenshot": screenshot_file,
                            "har": har_path.name if har_enabled else None,
                            "video": video_path.name if video_enabled else None,
                            "sessionState": {
                                "replayed": bool(browser_session_state is not None),
                                "replay": session_replay_summary,
                                "captured": bool(capture_session_state),
                                "summary": session_summary_file,
                                "state": session_state_file,
                            },
                        }
                        result_path.write_text(
                            json.dumps(result, ensure_ascii=False, indent=2),
                            encoding="utf-8",
                        )
                        if har_enabled:
                            har_path.write_text(
                                json.dumps(build_har(network_events), ensure_ascii=False, indent=2),
                                encoding="utf-8",
                            )
                        if video_enabled:
                            context.close()
                            context = None
                            videos = sorted(
                                video_dir.glob("*.webm"),
                                key=lambda item: item.stat().st_mtime_ns,
                                reverse=True,
                            )
                            if not videos:
                                raise RuntimeError("browser video recording was not created")
                            videos[0].replace(video_path)
                    finally:
                        if context is not None:
                            context.close()
                        browser.close()

                print(f"browser {action} completed; textLength={len(body_text)}; screenshot={screenshot_enabled}; har={har_enabled}; video={video_enabled}; cookies={len(browser_cookies)}; sessionStateReplay={browser_session_state is not None}; sessionStateCapture={capture_session_state}; egressRequests={egress_summary['requestCount']}; egressContinued={egress_summary['continuedRequestCount']}; egressBlocked={egress_summary['blockedRequestCount']}; proxyEnabled={egress_summary['proxy']['enabled']}; proxyAuthenticated={egress_summary['proxy']['authenticated']}; proxyPoolSize={egress_summary['proxy']['poolSize']}; proxyRotation={egress_summary['proxy']['rotationEnabled']}")
                """.formatted(
                request.action(),
                jsonForScript(request.url()),
                jsonForScript(browserProxyServer),
                jsonForScript(proxyCredentials.username()),
                jsonForScript(proxyCredentials.password()),
                browserProxyServers.size(),
                jsonForScript(request.allowedHosts()),
                jsonForScript(request.browserPrivateNetworkAllowedHosts()),
                request.viewportWidth(),
                request.viewportHeight(),
                request.screenshot() ? "True" : "False",
                request.har() ? "True" : "False",
                request.video() ? "True" : "False",
                request.captureSessionState() ? "True" : "False",
                browserInputName(),
                browserCookiesName(),
                browserSessionStateInputName(),
                browserResultName(),
                browserScreenshotName(),
                browserHarName(),
                browserVideoName(),
                browserSessionStateName(),
                browserSessionSummaryName(),
                MAX_BROWSER_SESSION_STATE_CHARS);
    }

    private String fileConversionScript(FileConversionRequest request) {
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

    private boolean isSupportedFileConversion(String sourceFormat, String targetFormat) {
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

    private boolean isDelimitedFileFormat(String format) {
        return CSV_FORMAT.equals(format) || TSV_FORMAT.equals(format);
    }

    private boolean isBinaryDocumentFormat(String sourceFormat) {
        return DOCX_FORMAT.equals(sourceFormat)
                || ODT_FORMAT.equals(sourceFormat)
                || ODS_FORMAT.equals(sourceFormat)
                || ODP_FORMAT.equals(sourceFormat)
                || XLSX_FORMAT.equals(sourceFormat)
                || PPTX_FORMAT.equals(sourceFormat)
                || PDF_FORMAT.equals(sourceFormat);
    }

    private String fileConversionInputName(String sourceFormat) {
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

    private String fileConversionOutputName(String targetFormat) {
        if ("ocr_txt".equals(targetFormat)) {
            return "converted.txt";
        }
        return "converted." + targetFormat;
    }

    private boolean isSupportedBrowserAction(String action) {
        return BROWSER_ACTION_SNAPSHOT.equals(action) || BROWSER_ACTION_EXTRACT_TEXT.equals(action);
    }

    private String browserInputName() {
        return "browser-input.html";
    }

    private String browserCookiesName() {
        return "browser-cookies.json";
    }

    private String browserSessionStateInputName() {
        return "browser-session-state-input.json";
    }

    private String browserResultName() {
        return "browser-result.json";
    }

    private String browserScreenshotName() {
        return "screenshot.png";
    }

    private String browserHarName() {
        return "browser-network.har";
    }

    private String browserVideoName() {
        return "browser-video.webm";
    }

    private String browserSessionStateName() {
        return "browser-session-state.json";
    }

    private String browserSessionSummaryName() {
        return "browser-session-summary.json";
    }

    private int boundedInt(JsonNode root, String name, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        JsonNode value = root.path(name);
        if (value.isNumber()) {
            parsed = value.asInt(defaultValue);
        } else if (value.isTextual() && hasText(value.asText())) {
            try {
                parsed = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private String normalizedBrowserUrl(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_BROWSER_URL_CHARS) {
            throw new IllegalArgumentException("browser automation url exceeds " + MAX_BROWSER_URL_CHARS + " chars");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation url must be an HTTP/HTTPS URL with a host");
            }
            if (hasText(uri.getUserInfo())) {
                throw new IllegalArgumentException("browser automation url must not include userinfo credentials");
            }
            if (hasText(uri.getRawFragment())) {
                throw new IllegalArgumentException("browser automation url must not include fragment identifiers");
            }
            validateBrowserUrlQuery(uri);
            return uri.normalize().toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }

    private void validateBrowserUrlQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (!hasText(rawQuery)) {
            return;
        }
        if (rawQuery.length() > MAX_BROWSER_URL_QUERY_CHARS) {
            throw new IllegalArgumentException(
                    "browser automation url query exceeds " + MAX_BROWSER_URL_QUERY_CHARS + " chars");
        }
        for (String parameter : rawQuery.split("[&;]")) {
            if (!hasText(parameter)) {
                continue;
            }
            String rawName = parameter.split("=", 2)[0];
            String normalizedName = normalizedBrowserQueryParameterName(rawName);
            if (SENSITIVE_BROWSER_QUERY_PARAMETER_NAMES.contains(normalizedName)) {
                throw new IllegalArgumentException(
                        "browser automation url query must not include credential parameters");
            }
        }
    }

    private String normalizedBrowserQueryParameterName(String value) {
        String decodedName = decodedBrowserQueryParameterName(value).toLowerCase(Locale.ROOT);
        int bracketIndex = decodedName.indexOf('[');
        if (bracketIndex > 0) {
            decodedName = decodedName.substring(0, bracketIndex);
        }
        return decodedName.replaceAll("[^a-z0-9]", "");
    }

    private String decodedBrowserQueryParameterName(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String browserUrlHost(String url) {
        try {
            String host = new URI(url).getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "url host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }

    private String browserUrlOrigin(String url, String label) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(host)) {
                throw new IllegalArgumentException("browser automation " + label + " must be HTTP/HTTPS");
            }
            if (label.startsWith("sessionState origin")
                    && (hasText(uri.getUserInfo())
                    || hasText(uri.getRawPath())
                    || hasText(uri.getRawQuery())
                    || hasText(uri.getRawFragment()))) {
                throw new IllegalArgumentException("browser automation " + label + " must be an origin only");
            }
            validatePublicBrowserHost(host, label + " host");
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + host + ":" + port;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation " + label + " is not valid", ex);
        }
    }

    private List<String> normalizedBrowserAllowedHosts(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (value.isArray()) {
            value.forEach(item -> addNormalizedBrowserHost(hosts, item.asText("")));
        } else if (value.isTextual()) {
            for (String item : value.asText("").split(",")) {
                addNormalizedBrowserHost(hosts, item);
            }
        } else {
            addNormalizedBrowserHost(hosts, value.asText(""));
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts exceeds " + MAX_BROWSER_ALLOWED_HOSTS + " hosts");
        }
        return new ArrayList<>(hosts);
    }

    private void addNormalizedBrowserHost(Set<String> hosts, String value) {
        addNormalizedBrowserHost(hosts, value, "allowedHosts");
    }

    private void addNormalizedBrowserHost(Set<String> hosts, String value, String label) {
        if (!hasText(value)) {
            return;
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.contains("/") || host.contains(":") || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation " + label + " must contain host names only");
        }
        validatePublicBrowserHost(host, label);
        hosts.add(host);
    }

    private List<String> normalizedBrowserPrivateNetworkAllowedHosts() {
        String value = trimToNull(properties.getBrowserPrivateNetworkAllowedHosts());
        if (value == null) {
            return List.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            addNormalizedBrowserHost(hosts, item, "browserPrivateNetworkAllowedHosts");
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browserPrivateNetworkAllowedHosts must contain at most "
                            + MAX_BROWSER_ALLOWED_HOSTS
                            + " hosts");
        }
        return new ArrayList<>(hosts);
    }

    private List<String> effectiveBrowserPrivateNetworkAllowedHosts(List<String> runtimeRequestHosts) {
        if (runtimeRequestHosts == null) {
            return normalizedBrowserPrivateNetworkAllowedHosts();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String item : runtimeRequestHosts) {
            addNormalizedBrowserHost(hosts, item, "browserPrivateNetworkAllowedHosts");
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browserPrivateNetworkAllowedHosts must contain at most "
                            + MAX_BROWSER_ALLOWED_HOSTS
                            + " hosts");
        }
        return new ArrayList<>(hosts);
    }

    private String normalizedBrowserSessionState(JsonNode value,
                                                 List<String> allowedHosts,
                                                 String urlHost,
                                                 String urlOrigin,
                                                 boolean urlMode) throws IOException {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (!urlMode) {
            throw new IllegalArgumentException("browser automation session state replay is only supported for url mode");
        }
        JsonNode state = value.isTextual()
                ? objectMapper.readTree(value.asText(""))
                : value;
        if (state == null || !state.isObject()) {
            throw new IllegalArgumentException("browser automation sessionState must be an object");
        }
        validateKnownBrowserSessionStateKeys(state, BROWSER_SESSION_STATE_KEYS, "sessionState");
        validateBrowserSessionStateCookies(state.get("cookies"), allowedHosts, urlHost);
        validateBrowserSessionStateOrigins(state.get("origins"), allowedHosts, urlHost, urlOrigin);
        String serialized = objectMapper.writeValueAsString(state);
        if (serialized.length() > MAX_BROWSER_SESSION_STATE_CHARS) {
            throw new IllegalArgumentException("browser automation sessionState exceeds "
                    + MAX_BROWSER_SESSION_STATE_CHARS + " chars");
        }
        return serialized;
    }

    private void validateBrowserSessionStateCookies(JsonNode value, List<String> allowedHosts, String urlHost) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState cookies must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_COOKIES) {
            throw new IllegalArgumentException("browser automation sessionState cookies exceeds "
                    + MAX_BROWSER_SESSION_STATE_COOKIES + " items");
        }
        for (JsonNode cookieNode : value) {
            if (!cookieNode.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState cookie must be an object");
            }
            validateKnownBrowserSessionStateKeys(cookieNode, BROWSER_SESSION_STATE_COOKIE_KEYS, "sessionState cookie");
            normalizedBrowserCookieName(cookieNode.path("name").asText(""));
            normalizedBrowserCookieValue(cookieNode.path("value").asText(""));
            String domain = normalizedBrowserCookieDomain(cookieNode.path("domain").asText(""));
            String domainHost = browserCookieDomainHost(domain);
            if (!allowedHosts.contains(domainHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState cookie domain must be included in allowedHosts");
            }
            if (!domainHost.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState cookie domain must match the target URL host");
            }
            normalizedBrowserCookiePath(cookieNode.path("path").asText("/"));
        }
    }

    private void validateBrowserSessionStateOrigins(JsonNode value,
                                                    List<String> allowedHosts,
                                                    String urlHost,
                                                    String urlOrigin) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState origins must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_ORIGINS) {
            throw new IllegalArgumentException("browser automation sessionState origins exceeds "
                    + MAX_BROWSER_SESSION_STATE_ORIGINS + " items");
        }
        for (JsonNode originNode : value) {
            if (!originNode.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState origin must be an object");
            }
            validateKnownBrowserSessionStateKeys(originNode, BROWSER_SESSION_STATE_ORIGIN_KEYS, "sessionState origin");
            String originValue = originNode.path("origin").asText("");
            String host = browserSessionStateOriginHost(originValue);
            if (!allowedHosts.contains(host)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin host must be included in allowedHosts");
            }
            if (!host.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin host must match the target URL host");
            }
            if (!browserUrlOrigin(originValue, "sessionState origin").equals(urlOrigin)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin must match the target URL origin");
            }
            validateBrowserSessionStateLocalStorage(originNode.get("localStorage"));
        }
    }

    private void validateBrowserSessionStateLocalStorage(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState localStorage must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS) {
            throw new IllegalArgumentException("browser automation sessionState localStorage exceeds "
                    + MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS + " items");
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState localStorage item must be an object");
            }
            validateKnownBrowserSessionStateKeys(
                    item,
                    BROWSER_SESSION_STATE_LOCAL_STORAGE_KEYS,
                    "sessionState localStorage item");
            boundedBrowserSessionStateText(item.path("name").asText(""),
                    "sessionState localStorage name",
                    MAX_BROWSER_SESSION_STATE_NAME_CHARS,
                    true);
            boundedBrowserSessionStateText(item.path("value").asText(""),
                    "sessionState localStorage value",
                    MAX_BROWSER_SESSION_STATE_VALUE_CHARS,
                    false);
        }
    }

    private String boundedBrowserSessionStateText(String value, String label, int maxChars, boolean required) {
        String text = value == null ? "" : value;
        if ((required && !hasText(text)) || text.length() > maxChars || containsControlCharacter(text)) {
            throw new IllegalArgumentException("browser automation " + label + " is invalid");
        }
        return text;
    }

    private void validateKnownBrowserSessionStateKeys(JsonNode value, Set<String> allowedKeys, String label) {
        value.fieldNames().forEachRemaining(key -> {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("browser automation " + label + " contains unsupported fields");
            }
        });
    }

    private String browserCookieDomainHost(String domain) {
        if (!hasText(domain) || domain.startsWith(".") || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation sessionState cookie domain is invalid");
        }
        validatePublicBrowserHost(domain, "sessionState cookie domain");
        return domain;
    }

    private String browserSessionStateOriginHost(String origin) {
        if (!hasText(origin)) {
            throw new IllegalArgumentException("browser automation sessionState origin is required");
        }
        try {
            URI uri = new URI(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation sessionState origin must be HTTP/HTTPS");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "sessionState origin host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation sessionState origin is not valid", ex);
        }
    }

    private List<BrowserCookie> normalizedBrowserCookies(JsonNode value,
                                                         List<String> allowedHosts,
                                                         String urlHost) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation cookies must be an array");
        }
        if (value.size() > MAX_BROWSER_COOKIES) {
            throw new IllegalArgumentException(
                    "browser automation cookies exceeds " + MAX_BROWSER_COOKIES + " items");
        }
        if (value.isEmpty()) {
            return List.of();
        }
        if (!hasText(urlHost)) {
            throw new IllegalArgumentException("browser automation cookies are only supported for url mode");
        }
        List<BrowserCookie> cookies = new ArrayList<>();
        for (JsonNode cookieNode : value) {
            if (!cookieNode.isObject()) {
                throw new IllegalArgumentException("browser automation cookie must be an object");
            }
            String name = normalizedBrowserCookieName(cookieNode.path("name").asText(""));
            String cookieValue = normalizedBrowserCookieValue(cookieNode.path("value").asText(""));
            String domain = normalizedBrowserCookieDomain(cookieNode.path("domain").asText(urlHost));
            if (!allowedHosts.contains(domain)) {
                throw new IllegalArgumentException("browser automation cookie domain must be included in allowedHosts");
            }
            if (!domain.equals(urlHost)) {
                throw new IllegalArgumentException("browser automation cookie domain must match the target URL host");
            }
            cookies.add(new BrowserCookie(
                    name,
                    cookieValue,
                    domain,
                    normalizedBrowserCookiePath(cookieNode.path("path").asText("/")),
                    cookieNode.path("httpOnly").asBoolean(true),
                    cookieNode.path("secure").asBoolean(false),
                    normalizedBrowserCookieSameSite(cookieNode.path("sameSite").asText("Lax"))));
        }
        return cookies;
    }

    private String normalizedBrowserCookieName(String value) {
        String name = value == null ? "" : value.trim();
        if (!hasText(name)) {
            throw new IllegalArgumentException("browser automation cookie name is required");
        }
        if (name.length() > MAX_BROWSER_COOKIE_NAME_CHARS
                || name.matches(".*[\\s;,=].*")
                || containsControlCharacter(name)) {
            throw new IllegalArgumentException("browser automation cookie name is invalid");
        }
        return name;
    }

    private String normalizedBrowserCookieValue(String value) {
        String cookieValue = value == null ? "" : value;
        if (cookieValue.length() > MAX_BROWSER_COOKIE_VALUE_CHARS || containsControlCharacter(cookieValue)) {
            throw new IllegalArgumentException("browser automation cookie value is invalid");
        }
        return cookieValue;
    }

    private String normalizedBrowserCookieDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!hasText(domain)
                || domain.startsWith(".")
                || domain.contains("/")
                || domain.contains(":")
                || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation cookie domain must be a host name only");
        }
        validatePublicBrowserHost(domain, "cookie domain");
        return domain;
    }

    private void validatePublicBrowserHost(String host, String label) {
        if (!hasText(host)
                || "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.contains(":")
                || !host.contains(".")
                || !hasValidDnsLabels(host)
                || isIpv4Literal(host)
                || host.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("browser automation " + label
                    + " must be a valid dotted DNS host, not localhost or an IP literal");
        }
    }

    private boolean hasValidDnsLabels(String host) {
        String[] labels = host.split("\\.", -1);
        for (String dnsLabel : labels) {
            if (dnsLabel.isEmpty()
                    || dnsLabel.length() > 63
                    || dnsLabel.startsWith("-")
                    || dnsLabel.endsWith("-")) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv4Literal(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = host.split("\\.");
        for (String part : parts) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private String normalizedBrowserCookiePath(String value) {
        String path = hasText(value) ? value.trim() : "/";
        if (!path.startsWith("/") || containsControlCharacter(path)) {
            throw new IllegalArgumentException("browser automation cookie path must start with /");
        }
        return path;
    }

    private String normalizedBrowserCookieSameSite(String value) {
        if (!hasText(value)) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f);
    }

    private String jsonForScript(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("browser automation script input could not be serialized", ex);
        }
    }

    @Override
    public SandboxSession closeSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        deleteWorkspace(safeSession.sessionId());
        return safeSession.closed(clock.instant());
    }

    @Override
    public SandboxRuntimeSessionOwnership inspectSessionOwnership(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        return Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                ? SandboxRuntimeSessionOwnership.OWNED
                : SandboxRuntimeSessionOwnership.ABSENT;
    }

    @Override
    public SandboxRuntimeCleanupResult sweepOrphanedResources(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        Instant sweptAt = clock.instant();
        Instant cutoff = sweptAt.minus(properties.getOrphanWorkspaceMinAge());
        int inspected = 0;
        int skippedActive = 0;
        int skippedRecent = 0;
        int removed = 0;
        int failed = 0;
        List<String> removedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        try (var paths = Files.list(workspaceRoot)) {
            List<Path> candidates = paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isManagedWorkspaceDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path candidate : candidates) {
                inspected++;
                String workspaceName = candidate.getFileName().toString();
                if (safeActiveSessionIds.contains(workspaceName)) {
                    skippedActive++;
                    continue;
                }
                if (isRecentWorkspace(candidate, cutoff)) {
                    skippedRecent++;
                    continue;
                }
                if (deleteWorkspacePath(candidate)) {
                    removed++;
                    removedNames.add(workspaceName);
                } else {
                    failed++;
                    failedNames.add(workspaceName);
                }
            }
        } catch (IOException ex) {
            ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
            return new SandboxRuntimeCleanupResult(
                    sweptAt,
                    safeActiveSessionIds.size(),
                    inspected,
                    skippedActive,
                    skippedRecent,
                    removed,
                    failed + 1,
                    removedNames,
                    failedNames,
                    containerSummary.inspectedCount(),
                    containerSummary.activeCount(),
                    containerSummary.orphanCount(),
                    containerSummary.failedInspectionCount(),
                    containerSummary.activeNames(),
                    containerSummary.orphanNames(),
                    containerSummary.failureMessages());
        }
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        return new SandboxRuntimeCleanupResult(
                sweptAt,
                safeActiveSessionIds.size(),
                inspected,
                skippedActive,
                skippedRecent,
                removed,
                failed,
                removedNames,
                failedNames,
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                containerSummary.failureMessages());
    }

    @Override
    public SandboxRuntimeHealth inspectHealth(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        OciRuntimeAvailability ociRuntimeAvailability = inspectConfiguredOciRuntime();
        boolean workspaceAvailable = Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                && Files.isWritable(workspaceRoot);
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        failureMessages.addAll(ociRuntimeAvailability.failureMessages());
        if (!workspaceAvailable) {
            failureMessages.add("sandbox workspace root is not available");
        }
        WorkspaceDiskSummary diskSummary = workspaceDiskSummary(workspaceAvailable);
        failureMessages.addAll(diskSummary.failureMessages());
        boolean engineAvailable = containerSummary.failedInspectionCount() == 0;
        int ownedActiveSessionCount = ownedActiveSessionCount(safeActiveSessionIds);
        CapacitySummary capacitySummary = capacitySummary(ownedActiveSessionCount);
        return new SandboxRuntimeHealth(
                clock.instant(),
                "container",
                properties.getEngine(),
                properties.getNodeId(),
                properties.isAdmissionEnabled(),
                healthStatus(
                        engineAvailable,
                        ociRuntimeAvailability.available(),
                        workspaceAvailable,
                        diskSummary.available(),
                        capacitySummary.available(),
                        containerSummary.orphanCount(),
                        failureMessages),
                engineAvailable,
                workspaceAvailable,
                diskSummary.freeBytes(),
                diskSummary.minFreeBytes(),
                diskSummary.available(),
                diskSummary.status(),
                ownedActiveSessionCount,
                capacitySummary.limit(),
                capacitySummary.remaining(),
                capacitySummary.available(),
                capacitySummary.status(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                normalizedBrowserPrivateNetworkAllowedHosts(),
                properties.isDropAllCapabilities(),
                properties.isNoNewPrivileges(),
                properties.isReadOnlyRootFilesystem(),
                properties.getMaxSessionFileBytes(),
                MAX_SESSION_WORKSPACE_FILES,
                failureMessages,
                properties.getOciRuntime(),
                ociRuntimeAvailability.available());
    }

    private int ownedActiveSessionCount(Set<String> activeSessionIds) {
        return (int) activeSessionIds.stream()
                .filter(sessionId -> Files.isDirectory(workspaceForSession(sessionId), LinkOption.NOFOLLOW_LINKS))
                .count();
    }

    @Override
    public SandboxRuntimeContainerReapResult reapOrphanedContainers(Set<String> activeSessionIds, boolean dryRun) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        Instant reapedAt = clock.instant();
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        List<String> reapedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        if (containerSummary.failedInspectionCount() == 0 && !dryRun) {
            for (String containerName : containerSummary.orphanNames()) {
                if (removeManagedContainer(containerName)) {
                    reapedNames.add(containerName);
                } else {
                    failedNames.add(containerName);
                    failureMessages.add("failed to remove sandbox container " + containerName);
                }
            }
        }
        return new SandboxRuntimeContainerReapResult(
                reapedAt,
                dryRun,
                safeActiveSessionIds.size(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                reapedNames.size(),
                failedNames.size(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                reapedNames,
                failedNames,
                failureMessages);
    }

    private ContainerCommand containerCommand(SandboxSession session,
                                              Path workspace,
                                              boolean networkRequested,
                                              List<String> requestedHosts,
                                              String executionImage) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("run");
        if (!properties.getOciRuntime().isBlank()) {
            commandLine.add("--runtime");
            commandLine.add(properties.getOciRuntime());
        }
        commandLine.add("--rm");
        commandLine.add("--name");
        commandLine.add(containerName(session.sessionId()));
        if (networkRequested) {
            for (String host : dockerInternalHosts(requestedHosts)) {
                commandLine.add("--add-host");
                commandLine.add(host + ":host-gateway");
            }
        } else {
            commandLine.add("--network");
            commandLine.add("none");
        }
        commandLine.add("--memory");
        commandLine.add(memoryForRuntime(session.runtimeType()));
        commandLine.add("--cpus");
        commandLine.add(properties.getCpus());
        commandLine.add("--pids-limit");
        commandLine.add(Long.toString(properties.getPidsLimit()));
        if (properties.isDropAllCapabilities()) {
            commandLine.add("--cap-drop");
            commandLine.add("ALL");
        }
        if (properties.isNoNewPrivileges()) {
            commandLine.add("--security-opt");
            commandLine.add("no-new-privileges:true");
        }
        if (properties.isReadOnlyRootFilesystem()) {
            commandLine.add("--read-only");
            commandLine.add("--tmpfs");
            commandLine.add("/tmp:rw,noexec,nosuid,size=64m");
        }
        commandLine.add("--ulimit");
        commandLine.add("fsize=" + maxSessionFileLimit() + ":" + maxSessionFileLimit());
        commandLine.add("--user");
        commandLine.add(properties.getRunAsUser());
        commandLine.add("-e");
        commandLine.add("HOME=/tmp");
        commandLine.add("-e");
        commandLine.add("XDG_CACHE_HOME=/tmp/.cache");
        commandLine.add("-v");
        commandLine.add(mountSourceForSession(session.sessionId(), workspace) + ":" + CONTAINER_WORKSPACE + ":rw");
        commandLine.add("-w");
        commandLine.add(CONTAINER_WORKSPACE);
        commandLine.add(executionImage);
        commandLine.add("python");
        commandLine.add(CONTAINER_WORKSPACE + "/" + SCRIPT_NAME);
        return new ContainerCommand(
                commandLine,
                workspace,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private List<String> dockerInternalHosts(List<String> requestedHosts) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add("host.docker.internal");
        for (String browserProxyHost : browserProxyHosts()) {
            if (browserProxyHost.endsWith(".docker.internal")) {
                hosts.add(browserProxyHost);
            }
        }
        if (requestedHosts != null) {
            for (String requestedHost : requestedHosts) {
                String host = nullToEmpty(requestedHost).trim().toLowerCase(Locale.ROOT);
                if (host.endsWith(".docker.internal")) {
                    hosts.add(host);
                }
            }
        }
        return List.copyOf(hosts);
    }

    private List<String> browserProxyHosts() {
        List<String> proxyServers = normalizedBrowserProxyServers();
        if (proxyServers.isEmpty()) {
            return List.of();
        }
        return proxyServers.stream()
                .map(proxyServer -> URI.create(proxyServer).getHost().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String selectBrowserProxyServer(List<String> proxyServers) {
        if (proxyServers.isEmpty()) {
            return "";
        }
        if (proxyServers.size() == 1) {
            return proxyServers.get(0);
        }
        int index = Math.floorMod(browserProxyCursor.getAndIncrement(), proxyServers.size());
        return proxyServers.get(index);
    }

    private List<String> normalizedBrowserProxyServers() {
        String singleProxyServer = trimToNull(properties.getBrowserProxyServer());
        String proxyServerList = trimToNull(properties.getBrowserProxyServers());
        if (singleProxyServer != null && proxyServerList != null) {
            throw new IllegalArgumentException(
                    "browserProxyServer and browserProxyServers must not be configured together");
        }
        if (proxyServerList == null) {
            String normalized = normalizeBrowserProxyServer(singleProxyServer);
            return hasText(normalized) ? List.of(normalized) : List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String candidate : proxyServerList.split(",")) {
            String proxyServer = normalizeBrowserProxyServer(candidate);
            if (hasText(proxyServer)) {
                normalized.add(proxyServer);
            }
            if (normalized.size() > MAX_BROWSER_PROXY_SERVERS) {
                throw new IllegalArgumentException(
                        "browserProxyServers must contain at most " + MAX_BROWSER_PROXY_SERVERS + " entries");
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeBrowserProxyServer(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = nullToEmpty(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!Set.of("http", "https").contains(scheme)
                    || !hasText(host)
                    || hasText(uri.getUserInfo())
                    || hasText(uri.getQuery())
                    || hasText(uri.getFragment())
                    || (hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("browserProxyServer must be an HTTP/HTTPS origin without credentials");
            }
            return new URI(
                    scheme,
                    null,
                    host.toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browserProxyServer must be a valid HTTP/HTTPS origin", ex);
        }
    }

    private BrowserProxyCredentials browserProxyCredentials(List<String> proxyServers) {
        String username = trimToNull(properties.getBrowserProxyUsername());
        String password = hasText(properties.getBrowserProxyPassword())
                ? properties.getBrowserProxyPassword()
                : null;
        if (username == null && password == null) {
            return BrowserProxyCredentials.none();
        }
        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "browserProxyUsername and browserProxyPassword must be configured together");
        }
        if (proxyServers.isEmpty()) {
            throw new IllegalArgumentException(
                    "browserProxyUsername/browserProxyPassword require browserProxyServer or browserProxyServers");
        }
        return new BrowserProxyCredentials(username, password);
    }

    private record BrowserProxyCredentials(String username, String password) {

        private static BrowserProxyCredentials none() {
            return new BrowserProxyCredentials("", "");
        }
    }

    private String imageForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserImage();
        }
        return properties.getPythonImage();
    }

    private String imageForExecution(SandboxRuntimeType runtimeType, String input) throws IOException {
        if (runtimeType != SandboxRuntimeType.FILE_CONVERSION) {
            return imageForRuntime(runtimeType);
        }
        FileConversionRequest request = parseFileConversionRequest(input);
        return requiresOfficeRenderer(request) ? properties.getOfficeConversionImage() : imageForRuntime(runtimeType);
    }

    private static boolean requiresOfficeRenderer(FileConversionRequest request) {
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

    private String memoryForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserMemory();
        }
        return properties.getMemory();
    }

    private long maxSessionFileLimit() {
        return Math.max(1L, properties.getMaxSessionFileBytes());
    }

    private ContainerCommand containerInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("ps");
        commandLine.add("-a");
        commandLine.add("--filter");
        commandLine.add("name=" + CONTAINER_NAME_PREFIX);
        commandLine.add("--format");
        commandLine.add("{{.Names}}\t{{.Status}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerCommand ociRuntimeInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("info");
        commandLine.add("--format");
        commandLine.add("docker".equalsIgnoreCase(properties.getEngine())
                ? "{{range $name, $_ := .Runtimes}}{{println $name}}{{end}}"
                : "{{.Host.OCIRuntime.Name}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerCommand containerRemoveCommand(String containerName) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("rm");
        commandLine.add("-f");
        commandLine.add(containerName);
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerInspectionSummary inspectManagedContainers(Set<String> activeContainerNames) {
        Set<String> safeActiveContainerNames = activeContainerNames == null
                ? Set.of()
                : Set.copyOf(activeContainerNames);
        try {
            ContainerCommandResult result = commandRunner.run(containerInspectionCommand());
            if (result.timedOut()) {
                return ContainerInspectionSummary.failed("container inspection timed out");
            }
            if (result.exitCode() != 0) {
                return ContainerInspectionSummary.failed(
                        "container inspection exitCode=" + result.exitCode() + "; stderr="
                                + oneLinePreview(result.stderr()));
            }
            List<String> activeNames = new ArrayList<>();
            List<String> orphanNames = new ArrayList<>();
            int inspectedCount = 0;
            for (String line : result.stdout().lines().toList()) {
                String name = containerNameFromPsLine(line);
                if (!hasText(name) || !isManagedContainerName(name)) {
                    continue;
                }
                inspectedCount++;
                if (safeActiveContainerNames.contains(name)) {
                    activeNames.add(name);
                } else {
                    orphanNames.add(name);
                }
            }
            activeNames.sort(String::compareTo);
            orphanNames.sort(String::compareTo);
            return new ContainerInspectionSummary(
                    inspectedCount,
                    activeNames.size(),
                    orphanNames.size(),
                    0,
                    activeNames,
                    orphanNames,
                    List.of());
        } catch (IOException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection io failure: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ContainerInspectionSummary.failed("container inspection interrupted");
        } catch (RuntimeException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    private OciRuntimeAvailability inspectConfiguredOciRuntime() {
        String configuredRuntime = properties.getOciRuntime();
        if (!hasText(configuredRuntime)) {
            return OciRuntimeAvailability.confirmed();
        }
        try {
            ContainerCommandResult result = commandRunner.run(ociRuntimeInspectionCommand());
            if (result.timedOut() || result.exitCode() != 0) {
                return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
            }
            boolean available = result.stdout().lines()
                    .map(String::trim)
                    .anyMatch(configuredRuntime::equals);
            return available
                    ? OciRuntimeAvailability.confirmed()
                    : OciRuntimeAvailability.unavailable("configured OCI runtime is not available");
        } catch (IOException ex) {
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection interrupted");
        } catch (RuntimeException ex) {
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
        }
    }

    private boolean removeManagedContainer(String containerName) {
        if (!isManagedContainerName(containerName)) {
            return false;
        }
        try {
            ContainerCommandResult result = commandRunner.run(containerRemoveCommand(containerName));
            return !result.timedOut() && result.exitCode() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CapacitySummary capacitySummary(int activeSessionCount) {
        int limit = Math.max(properties.getMaxActiveSessions(), 0);
        if (limit == 0) {
            return new CapacitySummary(0, 0, true, SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        }
        int remaining = Math.max(limit - Math.max(activeSessionCount, 0), 0);
        if (remaining == 0) {
            return new CapacitySummary(limit, 0, false, SandboxRuntimeHealth.CAPACITY_SATURATED);
        }
        return new CapacitySummary(limit, remaining, true, SandboxRuntimeHealth.CAPACITY_AVAILABLE);
    }

    private WorkspaceDiskSummary workspaceDiskSummary(boolean workspaceAvailable) {
        long minFreeBytes = Math.max(properties.getMinWorkspaceFreeBytes(), 0L);
        if (!workspaceAvailable) {
            return new WorkspaceDiskSummary(
                    -1L,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_UNKNOWN,
                    List.of());
        }
        try {
            FileStore store = Files.getFileStore(workspaceRoot);
            long freeBytes = Math.max(store.getUsableSpace(), 0L);
            if (minFreeBytes == 0L) {
                return new WorkspaceDiskSummary(
                        freeBytes,
                        0L,
                        true,
                        SandboxRuntimeHealth.DISK_UNBOUNDED,
                        List.of());
            }
            if (freeBytes >= minFreeBytes) {
                return new WorkspaceDiskSummary(
                        freeBytes,
                        minFreeBytes,
                        true,
                        SandboxRuntimeHealth.DISK_AVAILABLE,
                        List.of());
            }
            return new WorkspaceDiskSummary(
                    freeBytes,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_LOW,
                    List.of());
        } catch (IOException | RuntimeException ex) {
            if (minFreeBytes == 0L) {
                return new WorkspaceDiskSummary(
                        -1L,
                        0L,
                        true,
                        SandboxRuntimeHealth.DISK_UNBOUNDED,
                        List.of());
            }
            return new WorkspaceDiskSummary(
                    -1L,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_UNKNOWN,
                    List.of("workspace disk inspection failed"));
        }
    }

    private String healthStatus(boolean engineAvailable,
                                boolean ociRuntimeAvailable,
                                boolean workspaceAvailable,
                                boolean workspaceDiskAvailable,
                                boolean capacityAvailable,
                                int orphanContainerCount,
                                List<String> failureMessages) {
        if (!engineAvailable || !ociRuntimeAvailable || !workspaceAvailable) {
            return SandboxRuntimeHealth.STATUS_UNAVAILABLE;
        }
        if (!workspaceDiskAvailable || !capacityAvailable || orphanContainerCount > 0 || !failureMessages.isEmpty()) {
            return SandboxRuntimeHealth.STATUS_DEGRADED;
        }
        return SandboxRuntimeHealth.STATUS_HEALTHY;
    }

    private List<SandboxArtifact> collectArtifacts(SandboxSession session,
                                                   String executionId,
                                                   Path workspace,
                                                   Instant createdAt,
                                                   Set<Path> excludedArtifacts) throws IOException {
        if (!Files.exists(workspace)) {
            return List.of();
        }
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        Set<Path> safeExcludedArtifacts = excludedArtifacts == null
                ? Set.of()
                : excludedArtifacts.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (var paths = Files.walk(safeWorkspace)) {
            List<Path> files = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(safeWorkspace))
                    .limit(MAX_SESSION_WORKSPACE_FILES + 1L)
                    .toList();
            if (files.size() > MAX_SESSION_WORKSPACE_FILES) {
                throw new IOException("sandbox workspace exceeds session file count limit");
            }
            long workspaceBytes = 0L;
            for (Path path : files) {
                workspaceBytes = Math.addExact(workspaceBytes, Files.size(path));
                if (workspaceBytes > maxSessionFileLimit()) {
                    throw new IOException("sandbox workspace exceeds session file limit");
                }
            }
            return files.stream()
                    .filter(path -> !safeExcludedArtifacts.contains(path))
                    .sorted(Comparator.comparing(path -> safeWorkspace.relativize(path).toString()))
                    .map(path -> artifact(session, executionId, path, createdAt))
                    .toList();
        }
    }

    private SandboxArtifact artifact(SandboxSession session, String executionId, Path path, Instant createdAt) {
        return new SandboxArtifact(
                ARTIFACT_ID_PREFIX + SnowflakeIds.nextIdString(),
                session.sessionId(),
                executionId,
                path.toUri().toString(),
                mediaType(path),
                SandboxArtifactScanStatus.PENDING,
                artifactSensitivity(path),
                createdAt);
    }

    private ContextSensitivity artifactSensitivity(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString();
        if (browserSessionStateName().equals(name)) {
            return ContextSensitivity.SECRET;
        }
        return ContextSensitivity.INTERNAL;
    }

    private String mediaType(Path path) {
        String known = knownMediaType(path);
        if (known != null) {
            return known;
        }
        try {
            String probed = Files.probeContentType(path);
            if (hasText(probed)) {
                return probed.trim();
            }
        } catch (IOException ignored) {
            // Fall through to a conservative binary type.
        }
        return "application/octet-stream";
    }

    private String knownMediaType(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return "application/gzip";
        }
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (extension) {
            case "txt", "log" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "csv" -> "text/csv";
            case "tsv" -> "text/tab-separated-values";
            case "html", "htm" -> "text/html";
            case "py" -> "text/x-python";
            case "yaml", "yml" -> "text/yaml";
            case "json" -> "application/json";
            case "har" -> "application/har+json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "tar" -> "application/x-tar";
            case "zip" -> "application/zip";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "docm" -> "application/vnd.ms-word.document.macroenabled.12";
            case "xlsm" -> "application/vnd.ms-excel.sheet.macroenabled.12";
            case "pptm" -> "application/vnd.ms-powerpoint.presentation.macroenabled.12";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "webm" -> "video/webm";
            default -> null;
        };
    }

    private SandboxExecutionResult failedResult(String executionId,
                                                SandboxSession session,
                                                Instant startedAt,
                                                SandboxPolicyReasonCode reasonCode,
                                                String summary) {
        Instant finishedAt = clock.instant();
        SandboxExecution execution = new SandboxExecution(
                executionId,
                session.sessionId(),
                session.runtimeType(),
                SandboxExecutionStatus.FAILED,
                summary,
                reasonCode,
                startedAt,
                finishedAt);
        return SandboxExecutionResult.failed(execution, reasonCode);
    }

    private String summary(String prefix, ContainerCommandResult result) {
        return "%s; durationMs=%d; stdout=%s; stderr=%s".formatted(
                prefix,
                Math.max(0L, result.duration().toMillis()),
                oneLinePreview(result.stdout()),
                oneLinePreview(result.stderr()));
    }

    private String oneLinePreview(String value) {
        String preview = nullToEmpty(value)
                .replace('\r', '\n')
                .lines()
                .limit(8)
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("");
        if (preview.length() <= 512) {
            return preview;
        }
        return preview.substring(0, 512);
    }

    private Path workspaceForSession(String sessionId) {
        String safeName = safeFilesystemName(sessionId);
        Path workspace = workspaceRoot.resolve(safeName).toAbsolutePath().normalize();
        if (!workspace.startsWith(workspaceRoot) || workspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("invalid sandbox session workspace");
        }
        return workspace;
    }

    private String mountSourceForSession(String sessionId, Path workspace) {
        if (workspaceMountSourceRoot == null) {
            return workspace.toAbsolutePath().normalize().toString();
        }
        String root = stripTrailingSeparators(workspaceMountSourceRoot);
        if (root.length() >= 3 && Character.isLetter(root.charAt(0)) && root.charAt(1) == ':'
                && (root.charAt(2) == '/' || root.charAt(2) == '\\')) {
            root = "/run/desktop/mnt/host/" + Character.toLowerCase(root.charAt(0)) + root.substring(2);
        }
        return root + "/" + safeFilesystemName(sessionId);
    }

    private void deleteWorkspace(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        if (!Files.exists(workspace)) {
            return;
        }
        if (!deleteWorkspacePath(workspace)) {
            throw new IllegalStateException("sandbox workspace could not be deleted");
        }
    }

    private boolean deleteWorkspacePath(Path workspace) {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (!safeWorkspace.startsWith(workspaceRoot) || safeWorkspace.equals(workspaceRoot)) {
            return false;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                Path safePath = path.toAbsolutePath().normalize();
                if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The final existence check keeps close fail-closed when any deletion fails.
                }
            });
            return !Files.exists(safeWorkspace);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isManagedWorkspaceDirectory(Path path) {
        Path safePath = path.toAbsolutePath().normalize();
        if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
            return false;
        }
        Path filename = safePath.getFileName();
        return filename != null && filename.toString().startsWith(COORDINATOR_SESSION_ID_PREFIX);
    }

    private boolean isManagedContainerName(String value) {
        return hasText(value) && value.startsWith(CONTAINER_NAME_PREFIX);
    }

    private String containerNameFromPsLine(String line) {
        if (!hasText(line)) {
            return "";
        }
        String[] parts = line.split("\\t", 2);
        return parts[0].trim();
    }

    private boolean isRecentWorkspace(Path workspace, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(workspace, LinkOption.NOFOLLOW_LINKS);
            return modifiedTime.toInstant().isAfter(cutoff);
        } catch (IOException ex) {
            return true;
        }
    }

    private Set<String> normalizeActiveSessionIds(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(safeFilesystemName(sessionId.trim()));
            }
        }
        return Set.copyOf(safeNames);
    }

    private Set<String> normalizeActiveContainerNames(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(containerName(sessionId));
            }
        }
        return Set.copyOf(safeNames);
    }

    private String containerName(String sessionId) {
        String base = CONTAINER_NAME_PREFIX + safeFilesystemName(sessionId).toLowerCase(Locale.ROOT);
        if (base.length() <= 96) {
            return base;
        }
        return base.substring(0, 96);
    }

    private String safeFilesystemName(String value) {
        String safe = nullToEmpty(value).replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("sandbox session id must not be blank");
        }
        return safe;
    }

    private Path resolveWorkspaceRoot(String configuredRoot) {
        try {
            Path root = hasText(configuredRoot)
                    ? Path.of(configuredRoot)
                    : Path.of(System.getProperty("java.io.tmpdir"), "seahorse-sandbox-container");
            Path normalized = root.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create sandbox workspace root", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizedFormat(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "md".equals(normalized) ? MARKDOWN_FORMAT : normalized;
    }

    private static String normalizedContentEncoding(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PLAIN_ENCODING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return BASE64_ENCODING.equals(normalized) ? BASE64_ENCODING : PLAIN_ENCODING;
    }

    private static String normalizedBrowserAction(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static byte[] decodeBase64Content(String value) {
        try {
            return Base64.getDecoder().decode(nullToEmpty(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("file conversion content is not valid base64", ex);
        }
    }

    private static String stripTrailingSeparators(String value) {
        String result = Objects.requireNonNull(value, "value must not be null").trim();
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("workspaceMountSourceRoot must not be empty");
        }
        return result;
    }

    private record FileConversionRequest(String sourceFormat,
                                         String targetFormat,
                                         String contentEncoding,
                                         String content) {}

    private record BrowserAutomationRequest(String action,
                                            String html,
                                            String url,
                                            List<String> allowedHosts,
                                            List<BrowserCookie> cookies,
                                            int viewportWidth,
                                            int viewportHeight,
                                            boolean screenshot,
                                            boolean har,
                                            boolean video,
                                            boolean captureSessionState,
                                            String sessionStateJson,
                                            List<String> browserPrivateNetworkAllowedHosts) {

        private BrowserAutomationRequest {
            browserPrivateNetworkAllowedHosts = browserPrivateNetworkAllowedHosts == null
                    ? List.of()
                    : List.copyOf(browserPrivateNetworkAllowedHosts);
        }
    }

    private record BrowserCookie(String name,
                                 String value,
                                 String domain,
                                 String path,
                                 boolean httpOnly,
                                 boolean secure,
                                 String sameSite) {}

    private static final class UnsupportedFileConversionException extends RuntimeException {

        private UnsupportedFileConversionException(String message) {
            super(message);
        }
    }

    private static final class UnsupportedBrowserAutomationException extends RuntimeException {

        private UnsupportedBrowserAutomationException(String message) {
            super(message);
        }
    }

    private record ContainerInspectionSummary(int inspectedCount,
                                              int activeCount,
                                              int orphanCount,
                                              int failedInspectionCount,
                                              List<String> activeNames,
                                              List<String> orphanNames,
                                              List<String> failureMessages) {

        private ContainerInspectionSummary {
            activeNames = activeNames == null ? List.of() : List.copyOf(activeNames);
            orphanNames = orphanNames == null ? List.of() : List.copyOf(orphanNames);
            failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        }

        private static ContainerInspectionSummary failed(String message) {
            return new ContainerInspectionSummary(
                    0,
                    0,
                    0,
                    1,
                    List.of(),
                    List.of(),
                    List.of(nullToEmpty(message)));
        }
    }

    private record OciRuntimeAvailability(boolean available,
                                          List<String> failureMessages) {

        private OciRuntimeAvailability {
            failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        }

        private static OciRuntimeAvailability confirmed() {
            return new OciRuntimeAvailability(true, List.of());
        }

        private static OciRuntimeAvailability unavailable(String message) {
            return new OciRuntimeAvailability(false, List.of(nullToEmpty(message)));
        }
    }

    private record CapacitySummary(int limit,
                                   int remaining,
                                   boolean available,
                                   String status) {}

    private record WorkspaceDiskSummary(long freeBytes,
                                        long minFreeBytes,
                                        boolean available,
                                        String status,
                                        List<String> failureMessages) {

        private WorkspaceDiskSummary {
            failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        }
    }
}
