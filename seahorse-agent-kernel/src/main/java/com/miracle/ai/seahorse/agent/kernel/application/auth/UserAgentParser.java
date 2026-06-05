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

package com.miracle.ai.seahorse.agent.kernel.application.auth;

/**
 * Simple User-Agent parser without external dependencies.
 * Parses common browser and OS patterns.
 */
public final class UserAgentParser {

    private UserAgentParser() {
        // Utility class
    }

    /**
     * Parse a User-Agent string into a human-readable format.
     *
     * @param userAgentString the User-Agent header value
     * @return formatted string like "Chrome 120 on Windows 10" or "Unknown"
     */
    public static String parse(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return "Unknown";
        }

        String browser = parseBrowser(userAgentString);
        String os = parseOS(userAgentString);
        String device = parseDevice(userAgentString);

        StringBuilder result = new StringBuilder();
        result.append(browser);

        if (!"Unknown".equals(os)) {
            result.append(" on ").append(os);
        }

        if (!"Desktop".equals(device) && !"Unknown".equals(device)) {
            result.append(" (").append(device).append(")");
        }

        return result.toString();
    }

    private static String parseBrowser(String ua) {
        // Order matters: check more specific patterns first
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            String version = extractVersion(ua, "Edg/", "Edge/");
            return "Edge" + (version != null ? " " + version : "");
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            String version = extractVersion(ua, "OPR/", "Version/");
            return "Opera" + (version != null ? " " + version : "");
        }
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) {
            String version = extractVersion(ua, "Chrome/");
            return "Chrome" + (version != null ? " " + version : "");
        }
        if (ua.contains("Firefox/")) {
            String version = extractVersion(ua, "Firefox/");
            return "Firefox" + (version != null ? " " + version : "");
        }
        if (ua.contains("Safari/") && !ua.contains("Chrome")) {
            String version = extractVersion(ua, "Version/");
            return "Safari" + (version != null ? " " + version : "");
        }
        if (ua.contains("MSIE") || ua.contains("Trident/")) {
            String version = extractVersion(ua, "MSIE", "rv:");
            return "IE" + (version != null ? " " + version : "");
        }
        return "Unknown Browser";
    }

    private static String parseOS(String ua) {
        if (ua.contains("Windows NT 10.0")) {
            return "Windows 10";
        }
        if (ua.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (ua.contains("Windows NT 6.2")) {
            return "Windows 8";
        }
        if (ua.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X")) {
            String version = extractVersion(ua, "Mac OS X ");
            if (version != null) {
                return "macOS " + version.replace('_', '.');
            }
            return "macOS";
        }
        if (ua.contains("iPhone OS") || ua.contains("iOS")) {
            String version = extractVersion(ua, "iPhone OS ");
            return "iOS" + (version != null ? " " + version.replace('_', '.') : "");
        }
        if (ua.contains("Android")) {
            String version = extractVersion(ua, "Android ");
            return "Android" + (version != null ? " " + version : "");
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        if (ua.contains("Ubuntu")) {
            return "Ubuntu";
        }
        return "Unknown OS";
    }

    private static String parseDevice(String ua) {
        if (ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone")) {
            return "Mobile";
        }
        if (ua.contains("iPad") || ua.contains("Tablet")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private static String extractVersion(String ua, String... prefixes) {
        for (String prefix : prefixes) {
            int start = ua.indexOf(prefix);
            if (start != -1) {
                start += prefix.length();
                int end = start;
                while (end < ua.length() && (Character.isDigit(ua.charAt(end)) || ua.charAt(end) == '.' || ua.charAt(end) == '_')) {
                    end++;
                }
                if (end > start) {
                    return ua.substring(start, end);
                }
            }
        }
        return null;
    }
}
