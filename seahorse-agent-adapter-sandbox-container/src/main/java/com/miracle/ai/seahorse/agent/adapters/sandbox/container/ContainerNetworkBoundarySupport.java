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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 容器网络边界校验与主机名规范化协作者（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：容器网络边界校验与主机名规范化协作者。
 */
final class ContainerNetworkBoundarySupport {

    void validateContainerNetworkBoundary(SandboxRuntimeType runtimeType, boolean networkRequested) {
        if (!networkRequested || runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return;
        }
        throw new IllegalArgumentException(
                "container runtime network egress is only supported for browser automation");
    }
    void validateBrowserNetworkBoundary(BrowserAutomationRequest request,
                                                boolean networkRequested,
                                                List<String> requestedHosts) {
        if (request.allowedHosts().isEmpty()) {
            return;
        }
        if (!networkRequested) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts requires networkRequested=true");
        }
        Set<String> authorizedHosts = normalizedBrowserRequestedHosts(requestedHosts);
        if (!authorizedHosts.containsAll(request.allowedHosts())) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts must be included in requestedHosts");
        }
    }
    Set<String> normalizedBrowserRequestedHosts(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        values.forEach(value -> addNormalizedBrowserHost(hosts, value));
        return Set.copyOf(hosts);
    }
    String browserUrlHost(String url) {
        try {
            String host = new URI(url).getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "url host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }
    String browserUrlOrigin(String url, String label) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !ContainerSandboxTextSupport.hasText(host)) {
                throw new IllegalArgumentException("browser automation " + label + " must be HTTP/HTTPS");
            }
            if (label.startsWith("sessionState origin")
                    && (ContainerSandboxTextSupport.hasText(uri.getUserInfo())
                    || ContainerSandboxTextSupport.hasText(uri.getRawPath())
                    || ContainerSandboxTextSupport.hasText(uri.getRawQuery())
                    || ContainerSandboxTextSupport.hasText(uri.getRawFragment()))) {
                throw new IllegalArgumentException("browser automation " + label + " must be an origin only");
            }
            validatePublicBrowserHost(host, label + " host");
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + host + ":" + port;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation " + label + " is not valid", ex);
        }
    }
    void addNormalizedBrowserHost(Set<String> hosts, String value) {
        addNormalizedBrowserHost(hosts, value, "allowedHosts");
    }
    void addNormalizedBrowserHost(Set<String> hosts, String value, String label) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return;
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.contains("/") || host.contains(":") || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation " + label + " must contain host names only");
        }
        validatePublicBrowserHost(host, label);
        hosts.add(host);
    }
    void validatePublicBrowserHost(String host, String label) {
        if (!ContainerSandboxTextSupport.hasText(host)
                || "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.contains(":")
                || !host.contains(".")
                || !hasValidDnsLabels(host)
                || isIpv4Literal(host)
                || host.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("browser automation " + label
                    + " must be a valid dotted DNS host, not localhost or an IP literal");
        }
    }
    boolean hasValidDnsLabels(String host) {
        String[] labels = host.split("\\.", -1);
        for (String dnsLabel : labels) {
            if (dnsLabel.isEmpty()
                    || dnsLabel.length() > 63
                    || dnsLabel.startsWith("-")
                    || dnsLabel.endsWith("-")) {
                return false;
            }
        }
        return true;
    }
    boolean isIpv4Literal(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = host.split("\\.");
        for (String part : parts) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }
    String browserCookieDomainHost(String domain) {
        if (!ContainerSandboxTextSupport.hasText(domain) || domain.startsWith(".") || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation sessionState cookie domain is invalid");
        }
        validatePublicBrowserHost(domain, "sessionState cookie domain");
        return domain;
    }
    String browserSessionStateOriginHost(String origin) {
        if (!ContainerSandboxTextSupport.hasText(origin)) {
            throw new IllegalArgumentException("browser automation sessionState origin is required");
        }
        try {
            URI uri = new URI(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !ContainerSandboxTextSupport.hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation sessionState origin must be HTTP/HTTPS");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "sessionState origin host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation sessionState origin is not valid", ex);
        }
    }
}
