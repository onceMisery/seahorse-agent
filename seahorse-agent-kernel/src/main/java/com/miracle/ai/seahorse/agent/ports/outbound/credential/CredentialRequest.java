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
 * Credential resolution request.
 */
public record CredentialRequest(
        CredentialAuthType authType,
        String secretRef,
        String tenantId,
        String serverId,
        String clientId,
        List<String> scopes,
        String audience,
        String resource
) {

    public CredentialRequest {
        authType = Objects.requireNonNullElse(authType, CredentialAuthType.NONE);
        secretRef = Objects.requireNonNullElse(secretRef, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        serverId = Objects.requireNonNullElse(serverId, "");
        clientId = Objects.requireNonNullElse(clientId, "");
        scopes = OAuthScopes.normalize(scopes);
        audience = Objects.requireNonNullElse(audience, "");
        resource = Objects.requireNonNullElse(resource, "");
        if (authType.isSecretBacked() && secretRef.isBlank()) {
            throw new IllegalArgumentException("secretRef must not be blank for " + authType);
        }
        if (CredentialAuthType.CLIENT_CREDENTIALS.equals(authType)) {
            requireText(tenantId, "tenantId");
            requireText(serverId, "serverId");
            requireText(clientId, "clientId");
        }
    }

    public static CredentialRequest none() {
        return new CredentialRequest(CredentialAuthType.NONE, "", "", "", "", List.of(), "", "");
    }

    public static CredentialRequest staticBearer(String secretRef) {
        return new CredentialRequest(CredentialAuthType.STATIC_BEARER, secretRef, "", "", "", List.of(), "", "");
    }

    public static CredentialRequest clientCredentials(String tenantId,
                                                      String serverId,
                                                      String clientId,
                                                      String clientSecretRef,
                                                      List<String> scopes,
                                                      String audience,
                                                      String resource) {
        return new CredentialRequest(
                CredentialAuthType.CLIENT_CREDENTIALS,
                clientSecretRef,
                tenantId,
                serverId,
                clientId,
                scopes,
                audience,
                resource);
    }

    public static CredentialRequest userDelegated(String tenantId,
                                                  String serverId,
                                                  String clientId,
                                                  List<String> scopes,
                                                  String audience,
                                                  String resource) {
        throw new IllegalArgumentException("USER_DELEGATED credential resolution is not supported yet");
    }

    public String clientSecretRef() {
        return secretRef;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
