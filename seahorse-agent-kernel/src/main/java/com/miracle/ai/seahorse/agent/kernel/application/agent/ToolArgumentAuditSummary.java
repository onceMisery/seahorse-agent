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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Package-private audit argument-summary collaborator for {@link LocalToolGatewayPort}.
 *
 * <p>Owns every pure argument-preview and per-tool argument-shape summary used to
 * build tool audit records and approval requests. Result-shape analysis lives in
 * {@link ToolResultAuditSummary}; together they keep the tool gateway under the
 * complexity budget.</p>
 */
final class ToolArgumentAuditSummary {

    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final int MAX_PREVIEW_ARGUMENT_KEY_LENGTH = 64;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> SANDBOX_FILE_FORMATS = List.of(
            "csv",
            "tsv",
            "json",
            "txt",
            "html",
            "markdown",
            "md",
            "docx",
            "odt",
            "ods",
            "odp",
            "xlsx",
            "pptx",
            "pdf",
            "png");
    private static final List<String> SANDBOX_FILE_CONTENT_ENCODINGS = List.of("plain", "base64");
    private static final List<String> SANDBOX_BROWSER_ARGUMENT_KEYS = List.of(
            "html",
            "url",
            "allowedHosts",
            "cookies",
            "sessionState",
            "sessionStateArtifactId",
            "browserProfileId",
            "captureSessionState",
            "action",
            "screenshot",
            "har",
            "video",
            "viewportWidth",
            "viewportHeight");

    private final ToolResultAuditSummary resultSummary;

    ToolArgumentAuditSummary() {
        this.resultSummary = new ToolResultAuditSummary();
    }

    String summarizeResult(ToolInvocationResult result, String auditError) {
        return resultSummary.summarizeResult(result, auditError);
    }

    String approvalSummary(ToolInvocationRequest request, PolicyDecision decision) {
        return truncate("Tool " + safeToolIdPreview(request.toolId())
                + " requires approval: " + safeReasonCodePreview(decision.reasonCode()));
    }

    String safeToolIdPreview(String toolId) {
        String value = Objects.requireNonNullElse(toolId, "").trim();
        return isSafePreviewArgumentKey(value) ? value : "unsafe-tool-id";
    }

    String safeReasonCodePreview(String reasonCode) {
        String value = Objects.requireNonNullElse(reasonCode, "").trim();
        return isSafePreviewArgumentKey(value) ? value : "unsafe-reason-code";
    }

    String argumentsPreviewJson(ToolInvocationRequest request) {
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "argumentKeys", safeArgumentKeys(request.arguments()),
                    "argumentCount", request.arguments().size(),
                    "argumentValueCount", mapValueCount(request.arguments()),
                    "argumentValueTotalLength", mapValueTotalLength(request.arguments()),
                    "argumentValueMaxLength", mapValueMaxLength(request.arguments()),
                    "resourceRefKeys", safeResourceRefKeys(request.resourceRefs()),
                    "resourceRefCount", request.resourceRefs().size(),
                    "resourceRefHash", sha256(canonicalResourceRefs(request.resourceRefs())))));
        } catch (JsonProcessingException ex) {
            return truncate("keys=" + safeArgumentKeys(request.arguments())
                    + ", size=" + request.arguments().size()
                    + ", argumentValueCount=" + mapValueCount(request.arguments())
                    + ", argumentValueTotalLength=" + mapValueTotalLength(request.arguments())
                    + ", argumentValueMaxLength=" + mapValueMaxLength(request.arguments())
                    + ", resourceRefCount=" + request.resourceRefs().size());
        }
    }

    String summarizeArguments(ToolInvocationRequest request) {
        if ("sandbox_browser".equals(request.toolId())) {
            return summarizeSandboxBrowserArguments(request);
        }
        if ("sandbox_python".equals(request.toolId())) {
            return summarizeSandboxPythonArguments(request);
        }
        if ("sandbox_file_convert".equals(request.toolId())) {
            return summarizeSandboxFileConvertArguments(request);
        }
        if ("invoke_remote_a2a_agent".equals(request.toolId())) {
            return summarizeRemoteA2aArguments(request);
        }
        if (request.toolId() != null && request.toolId().startsWith("openapi_")) {
            return summarizeOpenApiArguments(request);
        }
        return summarizeGenericArguments(request);
    }

    private String summarizeGenericArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=" + request.toolId()
                    + ", argumentKeys=" + safeArgumentKeys(arguments)
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeOpenApiArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> path = mapValue(arguments.get("path"));
        Map<String, Object> query = mapValue(arguments.get("query"));
        Map<String, Object> parameters = mapValue(arguments.get("parameters"));
        Map<String, Object> headers = mergeMaps(arguments.get("header"), arguments.get("headers"));
        Object body = arguments.containsKey("requestBody") ? arguments.get("requestBody") : arguments.get("body");
        Map<String, Object> bodyMap = mapValue(body);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("provider", "OPENAPI");
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        summary.put("pathKeys", safeArgumentKeys(path));
        summary.put("pathCount", path.size());
        summary.put("pathValueCount", mapValueCount(path));
        summary.put("pathValueTotalLength", mapValueTotalLength(path));
        summary.put("pathValueMaxLength", mapValueMaxLength(path));
        summary.put("queryKeys", safeArgumentKeys(query));
        summary.put("queryCount", query.size());
        summary.put("queryValueCount", mapValueCount(query));
        summary.put("queryValueTotalLength", mapValueTotalLength(query));
        summary.put("queryValueMaxLength", mapValueMaxLength(query));
        summary.put("parameterKeys", safeArgumentKeys(parameters));
        summary.put("parameterCount", parameters.size());
        summary.put("parameterValueCount", mapValueCount(parameters));
        summary.put("parameterValueTotalLength", mapValueTotalLength(parameters));
        summary.put("parameterValueMaxLength", mapValueMaxLength(parameters));
        summary.put("headerKeys", safeArgumentKeys(headers));
        summary.put("headerCount", headers.size());
        summary.put("headerValueCount", mapValueCount(headers));
        summary.put("headerValueTotalLength", mapValueTotalLength(headers));
        summary.put("headerValueMaxLength", mapValueMaxLength(headers));
        summary.put("requestBodyPresent", body != null);
        summary.put("requestBodyType", valueType(body));
        if (body instanceof String text) {
            summary.put("requestBodyLength", text.length());
        } else if (!bodyMap.isEmpty()) {
            summary.put("requestBodyKeys", safeArgumentKeys(bodyMap));
            summary.put("requestBodyFieldCount", bodyMap.size());
            summary.put("requestBodyValueCount", mapValueCount(bodyMap));
            summary.put("requestBodyValueTotalLength", mapValueTotalLength(bodyMap));
            summary.put("requestBodyValueMaxLength", mapValueMaxLength(bodyMap));
        }
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=" + request.toolId()
                    + ", provider=OPENAPI"
                    + ", argumentKeys=" + safeArgumentKeys(arguments)
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments)
                    + ", pathKeys=" + safeArgumentKeys(path)
                    + ", queryKeys=" + safeArgumentKeys(query)
                    + ", parameterKeys=" + safeArgumentKeys(parameters)
                    + ", headerKeys=" + safeArgumentKeys(headers)
                    + ", requestBodyPresent=" + (body != null)
                    + ", requestBodyType=" + valueType(body));
        }
    }

    private String summarizeSandboxPythonArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        List<String> requestedHosts = argumentStringList(arguments.get("requestedHosts"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "CODE_INTERPRETER");
        summary.put("codeLength", argumentString(arguments, "code").length());
        summary.put("networkRequested", booleanArgument(arguments, "networkRequested"));
        summary.put("requestedHostsPresent", !requestedHosts.isEmpty());
        summary.put("requestedHostCount", requestedHosts.size());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_python, runtimeType=CODE_INTERPRETER"
                    + ", codeLength=" + argumentString(arguments, "code").length()
                    + ", networkRequested=" + booleanArgument(arguments, "networkRequested")
                    + ", requestedHostsPresent=" + !requestedHosts.isEmpty()
                    + ", requestedHostCount=" + requestedHosts.size()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeSandboxFileConvertArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String sourceFormat = argumentString(arguments, "sourceFormat");
        String targetFormat = argumentString(arguments, "targetFormat");
        String contentEncoding = argumentString(arguments, "contentEncoding", "plain");
        String safeSourceFormat = safeKnownValue(sourceFormat, SANDBOX_FILE_FORMATS);
        String safeTargetFormat = safeKnownValue(targetFormat, SANDBOX_FILE_FORMATS);
        String safeContentEncoding = safeKnownValue(contentEncoding, SANDBOX_FILE_CONTENT_ENCODINGS);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "FILE_CONVERSION");
        summary.put("sourceFormat", safeSourceFormat);
        summary.put("sourceFormatPresent", hasText(sourceFormat));
        summary.put("sourceFormatLength", sourceFormat.length());
        summary.put("targetFormat", safeTargetFormat);
        summary.put("targetFormatPresent", hasText(targetFormat));
        summary.put("targetFormatLength", targetFormat.length());
        summary.put("contentEncoding", safeContentEncoding);
        summary.put("contentEncodingPresent", hasText(contentEncoding));
        summary.put("contentEncodingLength", contentEncoding.length());
        summary.put("contentLength", argumentString(arguments, "content").length());
        summary.put("binaryInput", "base64".equals(safeContentEncoding));
        summary.put("networkRequested", false);
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_file_convert, runtimeType=FILE_CONVERSION"
                    + ", sourceFormat=" + safeSourceFormat
                    + ", sourceFormatPresent=" + hasText(sourceFormat)
                    + ", sourceFormatLength=" + sourceFormat.length()
                    + ", targetFormat=" + safeTargetFormat
                    + ", targetFormatPresent=" + hasText(targetFormat)
                    + ", targetFormatLength=" + targetFormat.length()
                    + ", contentEncoding=" + safeContentEncoding
                    + ", contentEncodingPresent=" + hasText(contentEncoding)
                    + ", contentEncodingLength=" + contentEncoding.length()
                    + ", contentLength=" + argumentString(arguments, "content").length()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeRemoteA2aArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> metadata = mapValue(arguments.get("metadata"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        String agentName = argumentString(arguments, "agentName");
        String metadataVersion = argumentString(metadata, "version");
        summary.put("agentNamePresent", hasText(agentName));
        summary.put("agentNameLength", agentName.length());
        summary.put("promptLength", argumentString(arguments, "prompt").length());
        summary.put("metadataKeys", safeArgumentKeys(metadata));
        summary.put("metadataCount", metadata.size());
        summary.put("metadataValueCount", mapValueCount(metadata));
        summary.put("metadataValueTotalLength", mapValueTotalLength(metadata));
        summary.put("metadataValueMaxLength", mapValueMaxLength(metadata));
        summary.put("versionPresent", hasText(metadataVersion));
        summary.put("versionLength", metadataVersion.length());
        summary.put("argumentKeys", safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=invoke_remote_a2a_agent"
                    + ", agentNamePresent=" + hasText(agentName)
                    + ", agentNameLength=" + agentName.length()
                    + ", promptLength=" + argumentString(arguments, "prompt").length()
                    + ", metadataKeys=" + safeArgumentKeys(metadata)
                    + ", metadataCount=" + metadata.size()
                    + ", versionPresent=" + hasText(metadataVersion)
                    + ", versionLength=" + metadataVersion.length()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private String summarizeSandboxBrowserArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String url = argumentString(arguments, "url");
        String html = argumentString(arguments, "html");
        boolean urlMode = hasText(url);
        List<String> allowedHosts = argumentStringList(arguments.get("allowedHosts"));
        int cookieCount = listSize(arguments.get("cookies"));
        Map<String, Object> sessionState = mapValue(arguments.get("sessionState"));
        int sessionCookieCount = listSize(sessionState.get("cookies"));
        int sessionOriginCount = listSize(sessionState.get("origins"));
        int sessionLocalStorageItemCount = sessionStateLocalStorageItemCount(sessionState.get("origins"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("mode", urlMode ? "url" : "inline");
        summary.put("action", safeSandboxBrowserAction(arguments));
        summary.put("networkRequested", urlMode);
        summary.put("urlPresent", urlMode);
        summary.put("urlLength", url.length());
        summary.put("urlQueryPresent", hasUrlQuery(url));
        summary.put("urlQueryLength", urlQueryLength(url));
        summary.put("htmlPresent", hasText(html));
        summary.put("htmlLength", html.length());
        summary.put("allowedHostCount", allowedHosts.size());
        summary.put("allowedHostsPresent", !allowedHosts.isEmpty());
        summary.put("cookieCount", cookieCount);
        summary.put("sessionStateReplayRequested", !sessionState.isEmpty());
        summary.put("sessionStateArtifactReplayRequested", hasText(argumentString(arguments, "sessionStateArtifactId")));
        summary.put("browserProfileReplayRequested", hasText(argumentString(arguments, "browserProfileId")));
        summary.put("sessionStateCookieCount", sessionCookieCount);
        summary.put("sessionStateOriginCount", sessionOriginCount);
        summary.put("sessionStateLocalStorageItemCount", sessionLocalStorageItemCount);
        summary.put("captureSessionState", booleanArgument(arguments, "captureSessionState"));
        summary.put("screenshot", booleanArgument(arguments, "screenshot", true));
        summary.put("har", booleanArgument(arguments, "har"));
        summary.put("video", booleanArgument(arguments, "video"));
        summary.put("viewportWidthPresent", arguments.containsKey("viewportWidth"));
        summary.put("viewportWidth", positiveIntArgument(arguments, "viewportWidth"));
        summary.put("viewportHeightPresent", arguments.containsKey("viewportHeight"));
        summary.put("viewportHeight", positiveIntArgument(arguments, "viewportHeight"));
        summary.put("argumentKeys", safeSandboxBrowserArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", mapValueCount(arguments));
        summary.put("argumentValueTotalLength", mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", mapValueMaxLength(arguments));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_browser, mode=" + (urlMode ? "url" : "inline")
                    + ", allowedHostCount=" + allowedHosts.size()
                    + ", cookieCount=" + cookieCount
                    + ", sessionStateReplayRequested=" + !sessionState.isEmpty()
                    + ", sessionStateCookieCount=" + sessionCookieCount
                    + ", sessionStateOriginCount=" + sessionOriginCount
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + mapValueMaxLength(arguments));
        }
    }

    private List<String> safeSandboxBrowserArgumentKeys(Map<String, Object> arguments) {
        return SANDBOX_BROWSER_ARGUMENT_KEYS.stream()
                .filter(arguments::containsKey)
                .toList();
    }

    private boolean hasUrlQuery(String value) {
        return urlQueryLength(value) > 0;
    }

    private int urlQueryLength(String value) {
        if (!hasText(value)) {
            return 0;
        }
        try {
            String rawQuery = new URI(value).getRawQuery();
            return rawQuery == null ? 0 : rawQuery.length();
        } catch (URISyntaxException ex) {
            int queryStart = value.indexOf('?');
            if (queryStart < 0 || queryStart == value.length() - 1) {
                return 0;
            }
            int fragmentStart = value.indexOf('#', queryStart + 1);
            return (fragmentStart < 0 ? value.length() : fragmentStart) - queryStart - 1;
        }
    }

    private List<String> safeArgumentKeys(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }
        return arguments.keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::isSafePreviewArgumentKey)
                .sorted()
                .toList();
    }

    private List<String> safeResourceRefKeys(Map<String, String> resourceRefs) {
        if (resourceRefs == null || resourceRefs.isEmpty()) {
            return List.of();
        }
        return resourceRefs.keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(this::isSafePreviewArgumentKey)
                .sorted()
                .toList();
    }

    private boolean isSafePreviewArgumentKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_PREVIEW_ARGUMENT_KEY_LENGTH) {
            return false;
        }
        String lower = key.toLowerCase();
        if (lower.contains("secret") || lower.contains("token") || lower.contains("password")) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            boolean safe = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-'
                    || ch == '.';
            if (!safe) {
                return false;
            }
        }
        return true;
    }

    private String safeSandboxBrowserAction(Map<String, Object> arguments) {
        String action = argumentString(arguments, "action", "snapshot");
        if ("snapshot".equals(action) || "extract_text".equals(action) || "extract-text".equals(action)) {
            return action;
        }
        return "unsupported";
    }

    private String safeKnownValue(String value, List<String> allowedValues) {
        if (!hasText(value)) {
            return "absent";
        }
        String normalized = value.trim().toLowerCase();
        if (allowedValues.contains(normalized)) {
            return normalized;
        }
        return "unsupported";
    }

    private String canonicalResourceRefs(Map<String, String> resourceRefs) throws JsonProcessingException {
        Map<String, String> canonical = new LinkedHashMap<>();
        Objects.requireNonNullElse(resourceRefs, Map.<String, String>of()).entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> canonical.put(entry.getKey(), entry.getValue()));
        return OBJECT_MAPPER.writeValueAsString(canonical);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String argumentString(Map<String, Object> arguments, String name) {
        return argumentString(arguments, name, "");
    }

    private String argumentString(Map<String, Object> arguments, String name, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString().trim();
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name) {
        return booleanArgument(arguments, name, false);
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private int positiveIntArgument(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int listSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    private int sessionStateLocalStorageItemCount(Object value) {
        if (!(value instanceof Collection<?> origins)) {
            return 0;
        }
        int count = 0;
        for (Object origin : origins) {
            if (origin instanceof Map<?, ?> originMap) {
                count += listSize(originMap.get("localStorage"));
            }
        }
        return count;
    }

    private int mapValueCount(Map<String, Object> values) {
        return values == null ? 0 : values.size();
    }

    private int mapValueTotalLength(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(value -> value.toString().length())
                .sum();
    }

    private int mapValueMaxLength(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(value -> value.toString().length())
                .max()
                .orElse(0);
    }

    private List<String> argumentStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.toString().trim())
                    .filter(ToolArgumentAuditSummary::hasText)
                    .toList();
        }
        if (value instanceof String text && hasText(text)) {
            List<String> items = new ArrayList<>();
            for (String item : text.split(",")) {
                if (hasText(item)) {
                    items.add(item.trim());
                }
            }
            return items;
        }
        return List.of();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(key.toString(), item);
            }
        });
        return result;
    }

    private Map<String, Object> mergeMaps(Object first, Object second) {
        Map<String, Object> result = new LinkedHashMap<>(mapValue(first));
        result.putAll(mapValue(second));
        return result;
    }

    private String valueType(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof Collection<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
