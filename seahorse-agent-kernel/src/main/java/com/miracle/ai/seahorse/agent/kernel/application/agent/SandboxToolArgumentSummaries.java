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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private sandbox argument-shape summaries for {@link LocalToolGatewayPort}.
 *
 * <p>Owns the browser / code-interpreter / file-conversion runtime argument
 * previews. Shared redaction and size metrics come from
 * {@link ToolArgumentAuditSummary}; this class holds only sandbox-runtime
 * knowledge.</p>
 */
final class SandboxToolArgumentSummaries {

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

    private SandboxToolArgumentSummaries() {
    }

    static String summarizeSandboxBrowserArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String url = ToolArgumentAuditSummary.argumentString(arguments, "url");
        String html = ToolArgumentAuditSummary.argumentString(arguments, "html");
        boolean urlMode = ToolArgumentAuditSummary.hasText(url);
        List<String> allowedHosts = argumentStringList(arguments.get("allowedHosts"));
        int cookieCount = listSize(arguments.get("cookies"));
        Map<String, Object> sessionState = ToolArgumentAuditSummary.mapValue(arguments.get("sessionState"));
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
        summary.put("htmlPresent", ToolArgumentAuditSummary.hasText(html));
        summary.put("htmlLength", html.length());
        summary.put("allowedHostCount", allowedHosts.size());
        summary.put("allowedHostsPresent", !allowedHosts.isEmpty());
        summary.put("cookieCount", cookieCount);
        summary.put("sessionStateReplayRequested", !sessionState.isEmpty());
        summary.put("sessionStateArtifactReplayRequested",
                ToolArgumentAuditSummary.hasText(ToolArgumentAuditSummary.argumentString(arguments, "sessionStateArtifactId")));
        summary.put("browserProfileReplayRequested",
                ToolArgumentAuditSummary.hasText(ToolArgumentAuditSummary.argumentString(arguments, "browserProfileId")));
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
        summary.put("argumentValueCount", ToolArgumentAuditSummary.mapValueCount(arguments));
        summary.put("argumentValueTotalLength", ToolArgumentAuditSummary.mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        try {
            return ToolArgumentAuditSummary.truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return ToolArgumentAuditSummary.truncate("toolId=sandbox_browser, mode=" + (urlMode ? "url" : "inline")
                    + ", allowedHostCount=" + allowedHosts.size()
                    + ", cookieCount=" + cookieCount
                    + ", sessionStateReplayRequested=" + !sessionState.isEmpty()
                    + ", sessionStateCookieCount=" + sessionCookieCount
                    + ", sessionStateOriginCount=" + sessionOriginCount
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + ToolArgumentAuditSummary.mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + ToolArgumentAuditSummary.mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        }
    }

    static String summarizeSandboxPythonArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        List<String> requestedHosts = argumentStringList(arguments.get("requestedHosts"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "CODE_INTERPRETER");
        summary.put("codeLength", ToolArgumentAuditSummary.argumentString(arguments, "code").length());
        summary.put("networkRequested", booleanArgument(arguments, "networkRequested"));
        summary.put("requestedHostsPresent", !requestedHosts.isEmpty());
        summary.put("requestedHostCount", requestedHosts.size());
        summary.put("argumentKeys", ToolArgumentAuditSummary.safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", ToolArgumentAuditSummary.mapValueCount(arguments));
        summary.put("argumentValueTotalLength", ToolArgumentAuditSummary.mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        try {
            return ToolArgumentAuditSummary.truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return ToolArgumentAuditSummary.truncate("toolId=sandbox_python, runtimeType=CODE_INTERPRETER"
                    + ", codeLength=" + ToolArgumentAuditSummary.argumentString(arguments, "code").length()
                    + ", networkRequested=" + booleanArgument(arguments, "networkRequested")
                    + ", requestedHostsPresent=" + !requestedHosts.isEmpty()
                    + ", requestedHostCount=" + requestedHosts.size()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + ToolArgumentAuditSummary.mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + ToolArgumentAuditSummary.mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        }
    }

    static String summarizeSandboxFileConvertArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        String sourceFormat = ToolArgumentAuditSummary.argumentString(arguments, "sourceFormat");
        String targetFormat = ToolArgumentAuditSummary.argumentString(arguments, "targetFormat");
        String contentEncoding = ToolArgumentAuditSummary.argumentString(arguments, "contentEncoding", "plain");
        String safeSourceFormat = safeKnownValue(sourceFormat, SANDBOX_FILE_FORMATS);
        String safeTargetFormat = safeKnownValue(targetFormat, SANDBOX_FILE_FORMATS);
        String safeContentEncoding = safeKnownValue(contentEncoding, SANDBOX_FILE_CONTENT_ENCODINGS);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("runtimeType", "FILE_CONVERSION");
        summary.put("sourceFormat", safeSourceFormat);
        summary.put("sourceFormatPresent", ToolArgumentAuditSummary.hasText(sourceFormat));
        summary.put("sourceFormatLength", sourceFormat.length());
        summary.put("targetFormat", safeTargetFormat);
        summary.put("targetFormatPresent", ToolArgumentAuditSummary.hasText(targetFormat));
        summary.put("targetFormatLength", targetFormat.length());
        summary.put("contentEncoding", safeContentEncoding);
        summary.put("contentEncodingPresent", ToolArgumentAuditSummary.hasText(contentEncoding));
        summary.put("contentEncodingLength", contentEncoding.length());
        summary.put("contentLength", ToolArgumentAuditSummary.argumentString(arguments, "content").length());
        summary.put("binaryInput", "base64".equals(safeContentEncoding));
        summary.put("networkRequested", false);
        summary.put("argumentKeys", ToolArgumentAuditSummary.safeArgumentKeys(arguments));
        summary.put("argumentCount", arguments.size());
        summary.put("argumentValueCount", ToolArgumentAuditSummary.mapValueCount(arguments));
        summary.put("argumentValueTotalLength", ToolArgumentAuditSummary.mapValueTotalLength(arguments));
        summary.put("argumentValueMaxLength", ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        try {
            return ToolArgumentAuditSummary.truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return ToolArgumentAuditSummary.truncate("toolId=sandbox_file_convert, runtimeType=FILE_CONVERSION"
                    + ", sourceFormat=" + safeSourceFormat
                    + ", sourceFormatPresent=" + ToolArgumentAuditSummary.hasText(sourceFormat)
                    + ", sourceFormatLength=" + sourceFormat.length()
                    + ", targetFormat=" + safeTargetFormat
                    + ", targetFormatPresent=" + ToolArgumentAuditSummary.hasText(targetFormat)
                    + ", targetFormatLength=" + targetFormat.length()
                    + ", contentEncoding=" + safeContentEncoding
                    + ", contentEncodingPresent=" + ToolArgumentAuditSummary.hasText(contentEncoding)
                    + ", contentEncodingLength=" + contentEncoding.length()
                    + ", contentLength=" + ToolArgumentAuditSummary.argumentString(arguments, "content").length()
                    + ", argumentCount=" + arguments.size()
                    + ", argumentValueCount=" + ToolArgumentAuditSummary.mapValueCount(arguments)
                    + ", argumentValueTotalLength=" + ToolArgumentAuditSummary.mapValueTotalLength(arguments)
                    + ", argumentValueMaxLength=" + ToolArgumentAuditSummary.mapValueMaxLength(arguments));
        }
    }

    private static List<String> safeSandboxBrowserArgumentKeys(Map<String, Object> arguments) {
        return SANDBOX_BROWSER_ARGUMENT_KEYS.stream()
                .filter(arguments::containsKey)
                .toList();
    }

    private static boolean hasUrlQuery(String value) {
        return urlQueryLength(value) > 0;
    }

    private static int urlQueryLength(String value) {
        if (!ToolArgumentAuditSummary.hasText(value)) {
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

    private static String safeSandboxBrowserAction(Map<String, Object> arguments) {
        String action = ToolArgumentAuditSummary.argumentString(arguments, "action", "snapshot");
        if ("snapshot".equals(action) || "extract_text".equals(action) || "extract-text".equals(action)) {
            return action;
        }
        return "unsupported";
    }

    private static String safeKnownValue(String value, List<String> allowedValues) {
        if (!ToolArgumentAuditSummary.hasText(value)) {
            return "absent";
        }
        String normalized = value.trim().toLowerCase();
        if (allowedValues.contains(normalized)) {
            return normalized;
        }
        return "unsupported";
    }

    private static boolean booleanArgument(Map<String, Object> arguments, String name) {
        return booleanArgument(arguments, name, false);
    }

    private static boolean booleanArgument(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private static int positiveIntArgument(Map<String, Object> arguments, String name) {
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

    private static int listSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    private static int sessionStateLocalStorageItemCount(Object value) {
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

    private static List<String> argumentStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(item -> item.toString().trim())
                    .filter(ToolArgumentAuditSummary::hasText)
                    .toList();
        }
        if (value instanceof String text && ToolArgumentAuditSummary.hasText(text)) {
            List<String> items = new ArrayList<>();
            for (String item : text.split(",")) {
                if (ToolArgumentAuditSummary.hasText(item)) {
                    items.add(item.trim());
                }
            }
            return items;
        }
        return List.of();
    }
}
