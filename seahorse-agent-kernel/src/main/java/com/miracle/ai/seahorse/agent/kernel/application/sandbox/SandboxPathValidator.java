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

package com.miracle.ai.seahorse.agent.kernel.application.sandbox;

import com.miracle.ai.seahorse.agent.kernel.exception.ForbiddenException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Validates file paths accessed by sandbox-executed code against a forbidden path list.
 * Prevents sandbox escape through sensitive system directory access.
 */
public class SandboxPathValidator {

    private static final List<String> FORBIDDEN_PATHS = List.of(
            "/etc",
            "/root",
            "~/.ssh",
            "~/.gnupg",
            "/proc",
            "/sys",
            "/boot",
            "/dev",
            "c:\\windows\\system32"
    );

    /**
     * Validates that the given path does not resolve to a forbidden system directory.
     *
     * @param path the file path to validate
     * @throws ForbiddenException if the path matches a forbidden pattern
     */
    public void validate(String path) {
        if (path == null || path.isBlank()) {
            throw new ForbiddenException("path must not be blank", "file", path);
        }

        String normalizedPath = normalizePath(path.trim());

        for (String forbidden : FORBIDDEN_PATHS) {
            String normalizedForbidden = normalizePath(forbidden);
            if (normalizedPath.equals(normalizedForbidden)
                    || normalizedPath.startsWith(normalizedForbidden + "/")
                    || normalizedPath.startsWith(normalizedForbidden + "\\")) {
                throw new ForbiddenException(
                        "Access to path '" + path + "' is forbidden by sandbox policy",
                        "file",
                        path);
            }
        }

        // Attempt canonical path resolution for symlink escape detection
        try {
            String canonical = new File(path).getCanonicalPath();
            String normalizedCanonical = normalizePath(canonical);
            for (String forbidden : FORBIDDEN_PATHS) {
                String normalizedForbidden = normalizePath(forbidden);
                if (normalizedCanonical.equals(normalizedForbidden)
                        || normalizedCanonical.startsWith(normalizedForbidden + "/")
                        || normalizedCanonical.startsWith(normalizedForbidden + "\\")) {
                    throw new ForbiddenException(
                            "Resolved path '" + canonical + "' is forbidden by sandbox policy",
                            "file",
                            path);
                }
            }
        } catch (IOException ignored) {
            // If canonical resolution fails, rely on the normalized check above
        }
    }

    private static String normalizePath(String path) {
        // Expand ~ to user home for matching
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home", "");
            if (!home.isEmpty()) {
                path = home + path.substring(1);
            }
        }
        // Normalize separators to forward slash and lowercase for Windows compatibility
        return path.replace('\\', '/').toLowerCase();
    }
}
