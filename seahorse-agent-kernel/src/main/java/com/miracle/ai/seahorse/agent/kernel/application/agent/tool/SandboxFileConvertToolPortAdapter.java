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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SandboxFileConvertToolPortAdapter implements DescribedToolPort, ToolInvocationRequestAwarePort {

    public static final String TOOL_ID = "sandbox_file_convert";
    private static final int MAX_CONTENT_CHARS = 256 * 1024;
    private static final String SOURCE_FORMAT_ARGUMENT = "sourceFormat";
    private static final String TARGET_FORMAT_ARGUMENT = "targetFormat";
    private static final String CONTENT_ARGUMENT = "content";
    private static final String CONTENT_ENCODING_ARGUMENT = "contentEncoding";
    private static final String BASE64_ENCODING = "base64";
    private static final String PLAIN_ENCODING = "plain";
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
    private static final Set<String> DELIMITED_FORMATS = Set.of(CSV_FORMAT, TSV_FORMAT);
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Sandbox File Convert",
            "Convert bounded file content through the Seahorse sandbox runtime. Supports CSV/TSV to JSON, JSON to CSV/TSV, text to HTML, HTML to text, Markdown to HTML/text, base64 DOCX/ODT/ODP/PDF to HTML/text, base64 XLSX/ODS to CSV/HTML, and base64 PPTX to HTML/text with network disabled.",
            """
                    {"type":"object","required":["sourceFormat","targetFormat","content"],"properties":{"sourceFormat":{"type":"string","enum":["csv","tsv","json","txt","html","markdown","md","docx","odt","ods","odp","xlsx","pptx","pdf"]},"targetFormat":{"type":"string","enum":["json","csv","tsv","txt","html","pdf","png"]},"contentEncoding":{"type":"string","enum":["plain","base64"],"default":"plain","description":"Use base64 for binary DOCX/ODT/ODS/ODP/XLSX/PPTX/PDF input; plain is used for text inputs."},"content":{"type":"string","minLength":1,"maxLength":262144}}}
                    """);

    private final SandboxRuntimeInboundPort sandboxRuntime;
    private final AgentToolJsonSupport jsonSupport;

    public SandboxFileConvertToolPortAdapter(SandboxRuntimeInboundPort sandboxRuntime,
                                             AgentToolJsonSupport jsonSupport) {
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "sandboxRuntime must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        String safeCallId = hasText(toolCallId) ? toolCallId.trim() : "direct";
        return invoke(new ToolInvocationRequest(
                "sandbox-file-convert-" + safeCallId,
                safeCallId,
                safeCallId,
                null,
                null,
                null,
                null,
                null,
                null,
                TOOL_ID,
                arguments,
                Map.of(),
                null,
                List.of(TOOL_ID)));
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String sourceFormat = normalizedFormat(jsonSupport.string(safeRequest.arguments(), SOURCE_FORMAT_ARGUMENT));
        String targetFormat = normalizedFormat(jsonSupport.string(safeRequest.arguments(), TARGET_FORMAT_ARGUMENT));
        String contentEncoding = normalizedContentEncoding(jsonSupport.string(
                safeRequest.arguments(),
                CONTENT_ENCODING_ARGUMENT));
        String content = argumentStringPreservingWhitespace(safeRequest.arguments(), CONTENT_ARGUMENT);
        if (!isSupportedConversion(sourceFormat, targetFormat)) {
            return ToolInvocationResult.failed(
                    "sandbox_file_convert failed: supported conversions are csv/tsv to json, json to csv/tsv, txt to html, html to txt, markdown/md to html/txt, docx/odt/odp/pdf to html/txt, docx/pptx to pdf, pdf to png, xlsx/ods to csv/html, and pptx to html/txt");
        }
        if (isBinaryDocumentFormat(sourceFormat) && !BASE64_ENCODING.equals(contentEncoding)) {
            return ToolInvocationResult.failed("sandbox_file_convert failed: "
                    + sourceFormat + " contentEncoding must be base64");
        }
        if (!isBinaryDocumentFormat(sourceFormat) && BASE64_ENCODING.equals(contentEncoding)) {
            return ToolInvocationResult.failed(
                    "sandbox_file_convert failed: base64 contentEncoding is only supported for docx/odt/ods/odp/xlsx/pptx/pdf input");
        }
        if (content.isBlank()) {
            return ToolInvocationResult.failed("sandbox_file_convert failed: content is required");
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            return ToolInvocationResult.failed(
                    "sandbox_file_convert failed: content exceeds " + MAX_CONTENT_CHARS + " chars");
        }
        SandboxSession session = null;
        try {
            session = sandboxRuntime.createSession(new SandboxSessionCreateCommand(
                    safeRequest.tenantId(),
                    sandboxRunId(safeRequest),
                    SandboxRuntimeType.FILE_CONVERSION,
                    false,
                    List.of()));
            if (session.status().isTerminal()) {
                return failed(observation(session, null, List.of(), sourceFormat, targetFormat, contentEncoding),
                        "sandbox file conversion session did not start: " + session.reasonCode());
            }
            SandboxExecutionResult result = sandboxRuntime.execute(new SandboxExecutionCommand(
                    session.sessionId(),
                    conversionInput(sourceFormat, targetFormat, content, contentEncoding),
                    false,
                    List.of()));
            Map<String, Object> observation = observation(
                    session,
                    result.execution(),
                    result.artifacts(),
                    sourceFormat,
                    targetFormat,
                    contentEncoding);
            if (result.execution().status() == SandboxExecutionStatus.SUCCEEDED) {
                return ToolInvocationResult.ok(jsonSupport.write(observation));
            }
            return failed(observation,
                    "sandbox file conversion " + result.execution().status() + ": " + result.reasonCode());
        } catch (Exception ex) {
            return ToolInvocationResult.failed("sandbox_file_convert failed: "
                    + redactRuntimeDisplayText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
        } finally {
            closeQuietly(session);
        }
    }

    private String conversionInput(String sourceFormat, String targetFormat, String content, String contentEncoding) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceFormat", sourceFormat);
        request.put("targetFormat", targetFormat);
        request.put("contentEncoding", contentEncoding);
        request.put("content", content);
        return jsonSupport.write(request);
    }

    private ToolInvocationResult failed(Map<String, Object> observation, String summary) {
        String payload = jsonSupport.write(observation);
        return ToolInvocationResult.failed(summary + "; observation=" + payload);
    }

    private Map<String, Object> observation(SandboxSession session,
                                            SandboxExecution execution,
                                            List<SandboxArtifact> artifacts,
                                            String sourceFormat,
                                            String targetFormat,
                                            String contentEncoding) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("toolId", TOOL_ID);
        observation.put("sessionId", session == null ? null : session.sessionId());
        observation.put("runtimeType", SandboxRuntimeType.FILE_CONVERSION.name());
        observation.put("sessionStatus", session == null ? null : session.status().name());
        observation.put("sessionReasonCode", session == null ? null : session.reasonCode().name());
        observation.put("executionId", execution == null ? null : execution.executionId());
        observation.put("executionStatus", execution == null ? null : execution.status().name());
        observation.put("reasonCode", execution == null ? null : execution.reasonCode().name());
        observation.put("resultSummary", execution == null ? null : redactRuntimeDisplayText(execution.resultSummary()));
        observation.put("conversion", conversion(sourceFormat, targetFormat, contentEncoding));
        observation.put("artifacts", artifacts(artifacts));
        return observation;
    }

    private String redactRuntimeDisplayText(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private Map<String, Object> conversion(String sourceFormat, String targetFormat, String contentEncoding) {
        Map<String, Object> conversion = new LinkedHashMap<>();
        conversion.put("sourceFormat", sourceFormat);
        conversion.put("targetFormat", targetFormat);
        conversion.put("contentEncoding", contentEncoding);
        return conversion;
    }

    private List<Map<String, Object>> artifacts(List<SandboxArtifact> artifacts) {
        return Objects.requireNonNullElse(artifacts, List.<SandboxArtifact>of()).stream()
                .map(artifact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("artifactId", artifact.artifactId());
                    item.put("executionId", artifact.executionId());
                    item.put("mediaType", artifact.mediaType());
                    item.put("scanStatus", artifact.scanStatus().name());
                    item.put("sensitivity", artifact.sensitivity().name());
                    item.put("scanSummary", artifact.scanSummary());
                    item.put("redactionSummaryJson", artifact.redactionSummaryJson());
                    item.put("promptVisible", artifact.promptVisible());
                    return item;
                })
                .toList();
    }

    private void closeQuietly(SandboxSession session) {
        if (session == null || session.status().isTerminal()) {
            return;
        }
        try {
            sandboxRuntime.close(session.sessionId());
        } catch (RuntimeException ignored) {
            // Tool observations are about execution; close is best-effort cleanup here.
        }
    }

    private String sandboxRunId(ToolInvocationRequest request) {
        if (hasText(request.runId())) {
            return request.runId().trim();
        }
        return "sandbox-file-convert-" + request.toolCallId();
    }

    private String argumentStringPreservingWhitespace(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? "" : value.toString();
    }

    private String normalizedFormat(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "md".equals(normalized) ? MARKDOWN_FORMAT : normalized;
    }

    private String normalizedContentEncoding(String value) {
        if (value == null || value.isBlank()) {
            return PLAIN_ENCODING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return BASE64_ENCODING.equals(normalized) ? BASE64_ENCODING : PLAIN_ENCODING;
    }

    private boolean isSupportedConversion(String sourceFormat, String targetFormat) {
        return (DELIMITED_FORMATS.contains(sourceFormat) && JSON_FORMAT.equals(targetFormat))
                || (JSON_FORMAT.equals(sourceFormat) && DELIMITED_FORMATS.contains(targetFormat))
                || (TXT_FORMAT.equals(sourceFormat) && HTML_FORMAT.equals(targetFormat))
                || (HTML_FORMAT.equals(sourceFormat) && TXT_FORMAT.equals(targetFormat))
                || (MARKDOWN_FORMAT.equals(sourceFormat)
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat)))
                || ((DOCX_FORMAT.equals(sourceFormat)
                || ODT_FORMAT.equals(sourceFormat)
                || ODP_FORMAT.equals(sourceFormat)
                || PDF_FORMAT.equals(sourceFormat))
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat)))
                || (DOCX_FORMAT.equals(sourceFormat) && PDF_FORMAT.equals(targetFormat))
                || (PDF_FORMAT.equals(sourceFormat) && "png".equals(targetFormat))
                || (PPTX_FORMAT.equals(sourceFormat)
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat) || PDF_FORMAT.equals(targetFormat)))
                || ((XLSX_FORMAT.equals(sourceFormat) || ODS_FORMAT.equals(sourceFormat))
                && (CSV_FORMAT.equals(targetFormat) || HTML_FORMAT.equals(targetFormat)));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
