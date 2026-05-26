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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import java.util.List;
import java.util.Objects;

/**
 * Cache key for OAuth access tokens.
 */
public record OAuthTokenCacheKey(
        String tenantId,
        String serverId,
        String clientId,
        String audience,
        String resource,
        List<String> scopes
) {

    public OAuthTokenCacheKey {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        serverId = Objects.requireNonNullElse(serverId, "");
        clientId = Objects.requireNonNullElse(clientId, "");
        audience = Objects.requireNonNullElse(audience, "");
        resource = Objects.requireNonNullElse(resource, "");
        scopes = OAuthScopes.normalize(scopes);
        requireText(tenantId, "tenantId");
        requireText(serverId, "serverId");
        requireText(clientId, "clientId");
    }

    public static OAuthTokenCacheKey from(OAuthTokenRequest request) {
        OAuthTokenRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return new OAuthTokenCacheKey(
                safeRequest.tenantId(),
                safeRequest.serverId(),
                safeRequest.clientId(),
                safeRequest.audience(),
                safeRequest.resource(),
                safeRequest.scopes());
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
