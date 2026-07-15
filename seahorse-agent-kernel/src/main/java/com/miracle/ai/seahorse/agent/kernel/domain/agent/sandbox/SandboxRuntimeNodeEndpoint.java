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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record SandboxRuntimeNodeEndpoint(String nodeId, URI transportUri, Instant expiresAt) {

    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public SandboxRuntimeNodeEndpoint {
        nodeId = normalizeNodeId(nodeId);
        transportUri = normalizeTransportUri(transportUri);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    private static String normalizeNodeId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!NODE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("nodeId must use the sandbox runtime node id syntax");
        }
        return normalized;
    }

    private static URI normalizeTransportUri(URI value) {
        URI uri = Objects.requireNonNull(value, "transportUri must not be null").normalize();
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("transportUri must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }
        String text = uri.toString();
        if (text.length() > 512) {
            throw new IllegalArgumentException("transportUri must not exceed 512 characters");
        }
        return text.endsWith("/") ? URI.create(text.substring(0, text.length() - 1)) : uri;
    }
}
