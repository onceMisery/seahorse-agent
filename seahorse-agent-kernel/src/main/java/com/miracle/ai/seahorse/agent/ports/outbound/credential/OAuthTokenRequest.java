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
 * OAuth token acquisition request.
 */
public record OAuthTokenRequest(
        OAuthGrantType grantType,
        String tenantId,
        String serverId,
        String clientId,
        String clientSecretRef,
        SecretValue clientSecret,
        List<String> scopes,
        String audience,
        String resource
) {

    public OAuthTokenRequest {
        grantType = Objects.requireNonNull(grantType, "grantType must not be null");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        serverId = Objects.requireNonNullElse(serverId, "");
        clientId = Objects.requireNonNullElse(clientId, "");
        clientSecretRef = Objects.requireNonNullElse(clientSecretRef, "");
        clientSecret = Objects.requireNonNullElseGet(clientSecret, SecretValue::empty);
        scopes = OAuthScopes.normalize(scopes);
        audience = Objects.requireNonNullElse(audience, "");
        resource = Objects.requireNonNullElse(resource, "");
        if (OAuthGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            requireText(tenantId, "tenantId");
            requireText(serverId, "serverId");
            requireText(clientId, "clientId");
            requireText(clientSecretRef, "clientSecretRef");
            if (!clientSecret.hasText()) {
                throw new IllegalArgumentException("clientSecret must not be blank");
            }
        }
    }

    public static OAuthTokenRequest clientCredentials(String tenantId,
                                                      String serverId,
                                                      String clientId,
                                                      String clientSecretRef,
                                                      SecretValue clientSecret,
                                                      List<String> scopes,
                                                      String audience,
                                                      String resource) {
        return new OAuthTokenRequest(
                OAuthGrantType.CLIENT_CREDENTIALS,
                tenantId,
                serverId,
                clientId,
                clientSecretRef,
                clientSecret,
                scopes,
                audience,
                resource);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
