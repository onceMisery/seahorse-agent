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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyDecision;
import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class JdkHttpWebFetchPortAdapter implements WebFetchPort {

    private static final int DEFAULT_MAX_BYTES = 512 * 1024;
    private static final int DEFAULT_MAX_CHARS = 8_000;
    private static final String DEFAULT_USER_AGENT = "SeahorseAgent-WebResearch/1.0";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String TEXT_ACCEPT_HEADER = "text/html,text/plain,application/xhtml+xml;q=0.9,*/*;q=0.1";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String REASON_DNS_PRIVATE_NETWORK_BLOCKED = "DNS_PRIVATE_NETWORK_BLOCKED";
    private static final String REASON_WEB_FETCH_REJECTED = "WEB_FETCH_REJECTED";
    private static final String REASON_HTTP_STATUS_PREFIX = "HTTP_STATUS_";
    private static final String REASON_UNSUPPORTED_MIME_TYPE = "UNSUPPORTED_MIME_TYPE";
    private static final String REASON_WEB_FETCH_FAILED = "WEB_FETCH_FAILED";
    private static final List<String> TEXT_MIME_PREFIXES = List.of("text/", "application/xhtml+xml");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final HttpClient httpClient;
    private final WebFetchSafetyPolicy safetyPolicy;
    private final Duration timeout;
    private final int maxBytes;
    private final String userAgent;

    public JdkHttpWebFetchPortAdapter(HttpClient httpClient,
                                      WebFetchSafetyPolicy safetyPolicy,
                                      Duration timeout,
                                      int maxBytes,
                                      String userAgent) {
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::defaultHttpClient);
        this.safetyPolicy = Objects.requireNonNullElseGet(safetyPolicy, WebFetchSafetyPolicy::new);
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(10)
                : timeout;
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        this.userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent.trim();
    }

    @Override
    public WebFetchResult fetch(WebFetchRequest request) {
        if (request == null) {
            return WebFetchResult.failed("", REASON_WEB_FETCH_FAILED);
        }
        WebFetchSafetyDecision decision = safetyPolicy.decide(request.url());
        if (!decision.allowed()) {
            return WebFetchResult.rejected(request.url(), decision.reason().name());
        }
        URI uri = URI.create(request.url());
        if (resolvesToPrivateNetwork(uri)) {
            return WebFetchResult.rejected(request.url(), REASON_DNS_PRIVATE_NETWORK_BLOCKED);
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header(HEADER_ACCEPT, TEXT_ACCEPT_HEADER)
                    .header(HEADER_USER_AGENT, userAgent)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                return WebFetchResult.failed(request.url(), REASON_HTTP_STATUS_PREFIX + statusCode);
            }
            byte[] body = response.body() == null ? new byte[0] : response.body();
            boolean byteTruncated = body.length > maxBytes;
            if (byteTruncated) {
                body = java.util.Arrays.copyOf(body, maxBytes);
            }
            String contentType = response.headers()
                    .firstValue(HEADER_CONTENT_TYPE)
                    .orElse("");
            if (!isTextContent(contentType)) {
                return WebFetchResult.failed(request.url(), REASON_UNSUPPORTED_MIME_TYPE);
            }
            String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            String normalized = normalizeText(contentType, text);
            int maxChars = request.maxChars() <= 0 ? DEFAULT_MAX_CHARS : request.maxChars();
            boolean truncated = byteTruncated || normalized.length() > maxChars;
            String content = truncated ? normalized.substring(0, Math.min(maxChars, normalized.length())) : normalized;
            return WebFetchResult.fetched(request.url(), titleFromText(content), content, contentType, truncated);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return WebFetchResult.failed(request.url(), REASON_WEB_FETCH_FAILED);
        } catch (RuntimeException ex) {
            String reason = Objects.requireNonNullElse(ex.getMessage(), REASON_WEB_FETCH_FAILED);
            return WebFetchResult.failed(request.url(), reason.isBlank() ? REASON_WEB_FETCH_FAILED : reason);
        }
    }

    private HttpClient defaultHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout == null ? Duration.ofSeconds(10) : timeout)
                .followRedirects(HttpClient.Redirect.NEVER);
        HttpProxySupport.proxySelectorFromEnvironment().ifPresent(builder::proxy);
        return builder.build();
    }

    private boolean resolvesToPrivateNetwork(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private boolean isTextContent(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return TEXT_MIME_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private String normalizeText(String contentType, String text) {
        String content = Objects.requireNonNullElse(text, "");
        String normalizedContentType = Objects.requireNonNullElse(contentType, "").toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("html")) {
            content = SCRIPT_BLOCK.matcher(content).replaceAll(" ");
            content = STYLE_BLOCK.matcher(content).replaceAll(" ");
            content = HTML_TAG.matcher(content).replaceAll(" ");
        }
        return WHITESPACE.matcher(content).replaceAll(" ").trim();
    }

    private String titleFromText(String content) {
        String text = Objects.requireNonNullElse(content, "").trim();
        if (text.isBlank()) {
            return "";
        }
        int end = Math.min(120, text.length());
        return text.substring(0, end);
    }
}
