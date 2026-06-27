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

import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSourceType;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdkHttpWebSearchPortAdapter implements WebSearchPort {

    private static final String DEFAULT_ENDPOINT = "https://duckduckgo.com/html/";
    private static final String DEFAULT_USER_AGENT = "SeahorseAgent-WebSearch/1.0";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int HARD_MAX_RESULTS = 10;
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "(?is)<div[^>]+class=\"[^\"]*result[^\"]*\"[^>]*>(.*?)</div>\\s*</div>");
    private static final Pattern RESULT_LINK = Pattern.compile(
            "(?is)<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern RESULT_SNIPPET = Pattern.compile(
            "(?is)<a[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>"
                    + "|<div[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</div>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;
    private final String userAgent;

    public JdkHttpWebSearchPortAdapter(HttpClient httpClient,
                                       String endpoint,
                                       Duration timeout,
                                       String userAgent) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(10)
                : timeout;
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::defaultHttpClient);
        this.endpoint = normalizeEndpoint(endpoint);
        this.userAgent = userAgent == null || userAgent.isBlank()
                ? DEFAULT_USER_AGENT
                : userAgent.trim();
    }

    @Override
    public WebSearchResult search(WebSearchRequest request) {
        if (request == null) {
            return new WebSearchResult("", List.of());
        }
        int maxResults = maxResults(request.maxResults());
        URI uri = searchUri(request.query(), request.locale(), request.timeRange());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new WebSearchResult(request.query(), List.of());
            }
            return new WebSearchResult(request.query(), parseResults(response.body(), maxResults));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new WebSearchResult(request.query(), List.of());
        } catch (RuntimeException ex) {
            return new WebSearchResult(request.query(), List.of());
        }
    }

    private HttpClient defaultHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        HttpProxySupport.proxySelectorFromEnvironment().ifPresent(builder::proxy);
        return builder.build();
    }

    private URI normalizeEndpoint(String value) {
        String raw = value == null || value.isBlank() ? DEFAULT_ENDPOINT : value.trim();
        return URI.create(raw);
    }

    private URI searchUri(String query, String locale, String timeRange) {
        StringBuilder builder = new StringBuilder(endpoint.toString());
        builder.append(endpoint.toString().contains("?") ? "&" : "?");
        builder.append("q=").append(urlEncode(query));
        if (locale != null && !locale.isBlank()) {
            builder.append("&kl=").append(urlEncode(locale.trim().toLowerCase(Locale.ROOT)));
        }
        if (timeRange != null && !timeRange.isBlank()) {
            builder.append("&df=").append(urlEncode(timeRange.trim()));
        }
        return URI.create(builder.toString());
    }

    private List<WebSearchHit> parseResults(String html, int maxResults) {
        List<WebSearchHit> hits = new ArrayList<>();
        Matcher blockMatcher = RESULT_BLOCK.matcher(Objects.requireNonNullElse(html, ""));
        while (blockMatcher.find() && hits.size() < maxResults) {
            String block = blockMatcher.group(1);
            Matcher linkMatcher = RESULT_LINK.matcher(block);
            if (!linkMatcher.find()) {
                continue;
            }
            String url = normalizeResultUrl(linkMatcher.group(1));
            if (url.isBlank()) {
                continue;
            }
            String title = cleanHtml(linkMatcher.group(2));
            String snippet = snippet(block);
            hits.add(new WebSearchHit(title, url, snippet, WebSourceType.SEARCH_RESULT, null, null));
        }
        return List.copyOf(hits);
    }

    private String snippet(String block) {
        Matcher matcher = RESULT_SNIPPET.matcher(block);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        return cleanHtml(value);
    }

    private String normalizeResultUrl(String rawHref) {
        String href = decodeHtml(Objects.requireNonNullElse(rawHref, "").trim());
        if (href.isBlank()) {
            return "";
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("/l/") || href.startsWith("https://duckduckgo.com/l/")) {
            int index = href.indexOf("uddg=");
            if (index >= 0) {
                String encoded = href.substring(index + "uddg=".length());
                int separator = encoded.indexOf('&');
                if (separator >= 0) {
                    encoded = encoded.substring(0, separator);
                }
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        return "";
    }

    private String cleanHtml(String html) {
        String withoutTags = HTML_TAG.matcher(Objects.requireNonNullElse(html, "")).replaceAll(" ");
        String decoded = decodeHtml(withoutTags);
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }

    private String decodeHtml(String value) {
        Matcher matcher = ENTITY.matcher(Objects.requireNonNullElse(value, ""));
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(entityValue(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String entityValue(String entity) {
        return switch (entity) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> numericEntity(entity);
        };
    }

    private String numericEntity(String entity) {
        try {
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                return Character.toString(Integer.parseInt(entity.substring(2), 16));
            }
            if (entity.startsWith("#")) {
                return Character.toString(Integer.parseInt(entity.substring(1)));
            }
        } catch (IllegalArgumentException ex) {
            return "&" + entity + ";";
        }
        return "&" + entity + ";";
    }

    private int maxResults(int requested) {
        if (requested <= 0) {
            return DEFAULT_MAX_RESULTS;
        }
        return Math.min(requested, HARD_MAX_RESULTS);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8);
    }
}
