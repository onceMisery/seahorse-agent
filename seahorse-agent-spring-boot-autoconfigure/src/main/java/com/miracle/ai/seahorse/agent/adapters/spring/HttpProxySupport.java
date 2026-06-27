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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class HttpProxySupport {

    private static final String PROPERTY_PROXY = "seahorse.agent.adapters.http.proxy";
    private static final String PROPERTY_NON_PROXY_HOSTS = "seahorse.agent.adapters.http.non-proxy-hosts";

    private HttpProxySupport() {
    }

    static Optional<ProxySelector> proxySelectorFromEnvironment() {
        return proxyUri()
                .map(HttpProxySupport::toProxyAddress)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(address -> new EnvironmentProxySelector(address, noProxyHosts()));
    }

    private static Optional<URI> proxyUri() {
        String value = firstNonBlank(
                System.getProperty(PROPERTY_PROXY),
                System.getenv("HTTPS_PROXY"),
                System.getenv("https_proxy"),
                System.getenv("HTTP_PROXY"),
                System.getenv("http_proxy"));
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.contains("://") ? value.trim() : "http://" + value.trim();
        try {
            return Optional.of(URI.create(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<InetSocketAddress> toProxyAddress(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || host.isBlank() || port <= 0) {
            return Optional.empty();
        }
        return Optional.of(InetSocketAddress.createUnresolved(host, port));
    }

    private static List<String> noProxyHosts() {
        String value = firstNonBlank(
                System.getProperty(PROPERTY_NON_PROXY_HOSTS),
                System.getenv("NO_PROXY"),
                System.getenv("no_proxy"),
                System.getProperty("http.nonProxyHosts"));
        if (value == null) {
            return List.of();
        }
        List<String> hosts = new ArrayList<>();
        for (String token : value.split("[,|]")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                hosts.add(normalized);
            }
        }
        return List.copyOf(hosts);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = Objects.requireNonNullElse(value, "").trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return null;
    }

    private static final class EnvironmentProxySelector extends ProxySelector {

        private final List<Proxy> proxies;
        private final List<String> noProxyHosts;

        private EnvironmentProxySelector(InetSocketAddress proxyAddress, List<String> noProxyHosts) {
            this.proxies = List.of(new Proxy(Proxy.Type.HTTP, proxyAddress));
            this.noProxyHosts = noProxyHosts;
        }

        @Override
        public List<Proxy> select(URI uri) {
            String host = uri == null ? "" : Objects.requireNonNullElse(uri.getHost(), "")
                    .toLowerCase(Locale.ROOT);
            if (host.isBlank() || shouldBypass(host)) {
                return List.of(Proxy.NO_PROXY);
            }
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Proxy failures are surfaced by the calling HTTP client.
        }

        private boolean shouldBypass(String host) {
            for (String pattern : noProxyHosts) {
                if ("*".equals(pattern)
                        || host.equals(pattern)
                        || host.endsWith(stripLeadingWildcard(pattern))
                        || hostPortless(pattern).equals(host)) {
                    return true;
                }
            }
            return false;
        }

        private String stripLeadingWildcard(String pattern) {
            if (pattern.startsWith("*.")) {
                return pattern.substring(1);
            }
            if (pattern.startsWith(".")) {
                return pattern;
            }
            return "." + pattern;
        }

        private String hostPortless(String pattern) {
            int portSeparator = pattern.lastIndexOf(':');
            return portSeparator > 0 ? pattern.substring(0, portSeparator) : pattern;
        }
    }
}
