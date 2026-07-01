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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Near-term guardrail for local stdio MCP processes before a separate runner exists.
 */
final class StdioMcpRunnerPolicy {

    private static final List<String> DEFAULT_ENVIRONMENT_ALLOWLIST = List.of(
            "PATH",
            "Path",
            "PATHEXT",
            "SystemRoot",
            "SYSTEMROOT",
            "WINDIR",
            "HOME",
            "TMPDIR",
            "TEMP",
            "TMP",
            "LANG",
            "LC_ALL");
    private static final StdioMcpRunnerPolicy DISABLED = new StdioMcpRunnerPolicy(
            false,
            true,
            DEFAULT_ENVIRONMENT_ALLOWLIST,
            List.of());

    private final boolean enabled;
    private final boolean inheritEnvironment;
    private final Set<String> environmentAllowlist;
    private final Set<String> normalizedEnvironmentAllowlist;
    private final List<Path> workingDirAllowlist;

    private StdioMcpRunnerPolicy(boolean enabled,
                                 boolean inheritEnvironment,
                                 List<String> environmentAllowlist,
                                 List<String> workingDirAllowlist) {
        this.enabled = enabled;
        this.inheritEnvironment = inheritEnvironment;
        this.environmentAllowlist = normalizeEnvironmentKeys(environmentAllowlist);
        this.normalizedEnvironmentAllowlist = this.environmentAllowlist.stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.workingDirAllowlist = normalizeWorkingDirs(workingDirAllowlist);
    }

    static StdioMcpRunnerPolicy from(McpHttpAdapterProperties.StdioRunnerIsolation properties) {
        if (properties == null || !properties.isEnabled()) {
            return DISABLED;
        }
        return new StdioMcpRunnerPolicy(
                true,
                properties.isInheritEnvironment(),
                properties.getEnvironmentAllowlist(),
                properties.getWorkingDirAllowlist());
    }

    static StdioMcpRunnerPolicy defaultPolicy() {
        return from(new McpHttpAdapterProperties.StdioRunnerIsolation());
    }

    static List<String> defaultEnvironmentAllowlist() {
        return DEFAULT_ENVIRONMENT_ALLOWLIST;
    }

    Optional<String> validateWorkingDir(String workingDir) {
        if (!enabled || !hasText(workingDir)) {
            return Optional.empty();
        }
        Path requested = normalizePath(workingDir);
        if (!Files.isDirectory(requested)) {
            return Optional.of("stdio workingDir is not a directory: " + workingDir.trim());
        }
        if (workingDirAllowlist.isEmpty()
                || workingDirAllowlist.stream().noneMatch(requested::startsWith)) {
            return Optional.of("stdio workingDir not allowlisted: " + requested);
        }
        return Optional.empty();
    }

    void applyEnvironment(ProcessBuilder processBuilder, Map<String, String> configuredEnv) {
        Objects.requireNonNull(processBuilder, "processBuilder must not be null");
        Map<String, String> target = processBuilder.environment();
        if (!enabled || inheritEnvironment) {
            putConfiguredEnvironment(target, configuredEnv);
            return;
        }
        Map<String, String> inherited = new LinkedHashMap<>(target);
        target.clear();
        inherited.forEach((key, value) -> {
            if (isEnvironmentAllowed(key) && value != null) {
                target.put(key, value);
            }
        });
        putConfiguredEnvironment(target, configuredEnv);
    }

    File workingDirectory(String workingDir) {
        if (!hasText(workingDir)) {
            return null;
        }
        return normalizePath(workingDir).toFile();
    }

    Map<String, String> isolatedEnvironment(Map<String, String> inheritedEnv, Map<String, String> configuredEnv) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!enabled || inheritEnvironment) {
            result.putAll(copyEnvironment(inheritedEnv));
        } else {
            copyEnvironment(inheritedEnv).forEach((key, value) -> {
                if (isEnvironmentAllowed(key)) {
                    result.put(key, value);
                }
            });
        }
        putConfiguredEnvironment(result, configuredEnv);
        return Map.copyOf(result);
    }

    private boolean isEnvironmentAllowed(String key) {
        if (!hasText(key)) {
            return false;
        }
        String safeKey = key.trim();
        return environmentAllowlist.contains(safeKey)
                || normalizedEnvironmentAllowlist.contains(safeKey.toLowerCase(Locale.ROOT));
    }

    private void putConfiguredEnvironment(Map<String, String> target, Map<String, String> configuredEnv) {
        copyEnvironment(configuredEnv).forEach(target::put);
    }

    private Map<String, String> copyEnvironment(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (hasText(key) && value != null) {
                result.put(key.trim(), value);
            }
        });
        return result;
    }

    private Set<String> normalizeEnvironmentKeys(List<String> keys) {
        List<String> source = keys == null ? DEFAULT_ENVIRONMENT_ALLOWLIST : keys;
        Set<String> result = new LinkedHashSet<>();
        source.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private List<Path> normalizeWorkingDirs(List<String> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        roots.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(this::normalizePath)
                .forEach(result::add);
        return List.copyOf(result);
    }

    private Path normalizePath(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
