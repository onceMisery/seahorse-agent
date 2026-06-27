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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryFile;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositorySnapshot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdkHttpGitHubRepositoryPortAdapter implements GitHubRepositoryPort {

    private static final Pattern GITHUB_REPOSITORY_URL = Pattern.compile(
            "^https?://github\\.com/([^/]+)/([^/#?]+)(?:[/#?].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_USER_AGENT = "SeahorseAgent-GitHubRepositoryReader/1.0";
    private static final List<String> PRIORITY_EXACT_PATHS = List.of(
            "README.md",
            "readme.md",
            "README",
            "docs/README.md",
            "docs/index.md",
            "redis.conf",
            "src/server.c",
            "src/networking.c",
            "src/ae.c",
            "src/db.c",
            "src/t_string.c",
            "src/replication.c",
            "src/sentinel.c");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".md", ".txt", ".rst", ".adoc", ".c", ".h", ".cc", ".cpp", ".java", ".kt", ".go", ".rs", ".py",
            ".js", ".ts", ".tsx", ".jsx", ".json", ".yaml", ".yml", ".toml", ".xml", ".properties", ".conf",
            ".ini", ".sh", ".sql", ".gradle", ".pom");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final String userAgent;
    private final Clock clock;

    public JdkHttpGitHubRepositoryPortAdapter(HttpClient httpClient,
                                              ObjectMapper objectMapper,
                                              Duration timeout,
                                              String userAgent,
                                              Clock clock) {
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(15)
                : timeout;
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::defaultHttpClient);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent.trim();
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public GitHubRepositorySnapshot read(GitHubRepositoryRequest request) {
        RepositoryRef ref = parseRepositoryUrl(request.repositoryUrl());
        JsonNode metadata = repositoryMetadata(ref);
        String defaultBranch = firstText(metadata, "default_branch");
        if (request.branch() != null && !request.branch().isBlank()) {
            defaultBranch = request.branch();
        }
        if (defaultBranch == null || defaultBranch.isBlank()) {
            defaultBranch = "main";
        }
        String description = firstText(metadata, "description");
        String htmlUrl = firstText(metadata, "html_url");
        if (htmlUrl == null || htmlUrl.isBlank()) {
            htmlUrl = "https://github.com/" + ref.owner() + "/" + ref.repo();
        }

        List<TreeItem> tree = tree(ref, defaultBranch);
        List<String> selectedPaths = selectPaths(tree, request.maxFiles());
        List<GitHubRepositoryFile> files = new ArrayList<>();
        for (String selectedPath : selectedPaths) {
            readRawFile(ref, defaultBranch, selectedPath, request.maxCharsPerFile())
                    .ifPresent(files::add);
        }
        return new GitHubRepositorySnapshot(
                ref.owner(),
                ref.repo(),
                defaultBranch,
                htmlUrl,
                Objects.requireNonNullElse(description, ""),
                files,
                tree.size() > selectedPaths.size(),
                clock.instant());
    }

    private List<TreeItem> tree(RepositoryRef ref, String branch) {
        try {
            JsonNode root = getJson("https://api.github.com/repos/" + path(ref.owner()) + "/" + path(ref.repo())
                    + "/git/trees/" + path(branch) + "?recursive=1");
            JsonNode tree = root.path("tree");
            if (!tree.isArray()) {
                return List.of();
            }
            List<TreeItem> items = new ArrayList<>();
            for (JsonNode item : tree) {
                if ("blob".equals(item.path("type").asText(""))) {
                    String path = item.path("path").asText("");
                    int size = item.path("size").asInt(0);
                    if (isUsefulTextPath(path, size)) {
                        items.add(new TreeItem(path, size));
                    }
                }
            }
            return List.copyOf(items);
        } catch (RuntimeException ex) {
            return fallbackTree();
        }
    }

    private List<String> selectPaths(List<TreeItem> tree, int maxFiles) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        Set<String> available = new LinkedHashSet<>(tree.stream().map(TreeItem::path).toList());
        for (String priorityPath : PRIORITY_EXACT_PATHS) {
            if (available.contains(priorityPath)) {
                selected.add(priorityPath);
            }
        }
        tree.stream()
                .sorted(Comparator.comparingInt(this::score).thenComparing(TreeItem::path))
                .map(TreeItem::path)
                .forEach(selected::add);
        return selected.stream().limit(Math.max(1, maxFiles)).toList();
    }

    private int score(TreeItem item) {
        String path = item.path().toLowerCase(Locale.ROOT);
        if (path.equals("readme.md") || path.equals("readme")) {
            return 0;
        }
        if (path.startsWith("docs/") || path.startsWith("doc/")) {
            return 10;
        }
        if (path.endsWith(".md") || path.endsWith(".rst") || path.endsWith(".adoc")) {
            return 20;
        }
        if (path.contains("server") || path.contains("network") || path.contains("main")) {
            return 30;
        }
        if (path.endsWith(".conf") || path.endsWith(".yaml") || path.endsWith(".yml") || path.endsWith(".json")) {
            return 40;
        }
        return 100;
    }

    private java.util.Optional<GitHubRepositoryFile> readRawFile(RepositoryRef ref,
                                                                 String branch,
                                                                 String filePath,
                                                                 int maxChars) {
        String rawUrl = "https://raw.githubusercontent.com/" + path(ref.owner()) + "/" + path(ref.repo())
                + "/" + path(branch) + "/" + encodePathSegments(filePath);
        try {
            String content = getText(rawUrl);
            boolean truncated = content.length() > maxChars;
            if (truncated) {
                content = content.substring(0, maxChars);
            }
            return java.util.Optional.of(new GitHubRepositoryFile(filePath, content, rawUrl, truncated));
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private JsonNode getJson(String url) {
        try {
            return objectMapper.readTree(getText(url));
        } catch (IOException ex) {
            throw new IllegalStateException("GitHub JSON parse failed: " + url, ex);
        }
    }

    private JsonNode repositoryMetadata(RepositoryRef ref) {
        try {
            return getJson("https://api.github.com/repos/" + path(ref.owner()) + "/" + path(ref.repo()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String getText(String url) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/vnd.github+json,text/plain,*/*")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("GitHub request failed: " + status + ", url=" + url);
            }
            return Objects.requireNonNullElse(response.body(), "");
        } catch (IOException ex) {
            throw new IllegalStateException("GitHub request failed: " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub request interrupted: " + url, ex);
        }
    }

    private RepositoryRef parseRepositoryUrl(String repositoryUrl) {
        Matcher matcher = GITHUB_REPOSITORY_URL.matcher(Objects.requireNonNullElse(repositoryUrl, "").trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Only https://github.com/{owner}/{repo} URLs are supported");
        }
        String repo = matcher.group(2).replaceFirst("\\.git$", "");
        return new RepositoryRef(matcher.group(1), repo);
    }

    private boolean isUsefulTextPath(String path, int size) {
        if (path == null || path.isBlank() || size <= 0 || size > 512 * 1024) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.contains("/.git/") || normalized.contains("/node_modules/") || normalized.contains("/dist/")) {
            return false;
        }
        if (normalized.equals("readme") || normalized.equals("makefile") || normalized.equals("dockerfile")) {
            return true;
        }
        return TEXT_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private List<TreeItem> fallbackTree() {
        return PRIORITY_EXACT_PATHS.stream()
                .map(path -> new TreeItem(path, 1))
                .toList();
    }

    private HttpClient defaultHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        HttpProxySupport.proxySelectorFromEnvironment().ifPresent(builder::proxy);
        return builder.build();
    }

    private String firstText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private String path(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String encodePathSegments(String value) {
        return java.util.Arrays.stream(Objects.requireNonNullElse(value, "").split("/"))
                .map(this::path)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private record RepositoryRef(String owner, String repo) { }

    private record TreeItem(String path, int size) { }
}
