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

package com.miracle.ai.seahorse.agent.kernel.application.agent.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class WebFetchSafetyPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_SUFFIX = ".localhost";
    private static final String AWS_METADATA_IP = "169.254.169.254";
    private static final String ALIYUN_METADATA_IP = "100.100.100.200";
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern LOOPBACK_IPV6_PATTERN = Pattern.compile("^0*:0*:0*:0*:0*:0*:0*:1$|^::1$");
    private static final Pattern LINK_LOCAL_IPV6_PATTERN = Pattern.compile("^fe[89ab][0-9a-f]?:.*");
    private static final Pattern UNIQUE_LOCAL_IPV6_PATTERN = Pattern.compile("^f[cd][0-9a-f]{2}:.*");

    public WebFetchSafetyDecision decide(String url) {
        if (url == null || url.isBlank()) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.URL_MISSING);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.URL_INVALID);
        }
        String scheme = normalize(uri.getScheme());
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.SCHEME_NOT_ALLOWED);
        }
        String host = normalize(uri.getHost());
        if (host == null) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.HOST_MISSING);
        }
        if (isLocalhost(host)) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.LOCALHOST_BLOCKED);
        }
        if (isMetadataEndpoint(host)) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.METADATA_ENDPOINT_BLOCKED);
        }
        if (isBlockedIpLiteral(host)) {
            return WebFetchSafetyDecision.reject(WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
        }
        return WebFetchSafetyDecision.allow();
    }

    private boolean isLocalhost(String host) {
        return LOCALHOST.equals(host) || host.endsWith(LOCALHOST_SUFFIX);
    }

    private boolean isMetadataEndpoint(String host) {
        return AWS_METADATA_IP.equals(host) || ALIYUN_METADATA_IP.equals(host);
    }

    private boolean isBlockedIpLiteral(String host) {
        String normalizedHost = stripIpv6Brackets(host);
        if (IPV4_PATTERN.matcher(normalizedHost).matches()) {
            return isPrivateIpv4(normalizedHost);
        }
        if (normalizedHost.contains(":")) {
            return LOOPBACK_IPV6_PATTERN.matcher(normalizedHost).matches()
                    || LINK_LOCAL_IPV6_PATTERN.matcher(normalizedHost).matches()
                    || UNIQUE_LOCAL_IPV6_PATTERN.matcher(normalizedHost).matches();
        }
        return false;
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        int first = octet(parts[0]);
        int second = octet(parts[1]);
        if (first < 0 || second < 0 || octet(parts[2]) < 0 || octet(parts[3]) < 0) {
            return true;
        }
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254)
                || first == 0;
    }

    private int octet(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && parsed <= 255 ? parsed : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
