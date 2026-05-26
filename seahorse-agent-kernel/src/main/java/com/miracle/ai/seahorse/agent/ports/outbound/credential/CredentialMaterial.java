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

import java.util.Objects;

/**
 * Resolved credential material. Raw secret access is explicit through {@link SecretValue#reveal()}.
 */
public record CredentialMaterial(
        CredentialAuthType authType,
        String secretRef,
        SecretValue secretValue
) {

    private static final CredentialMaterial NONE = new CredentialMaterial(
            CredentialAuthType.NONE, "", SecretValue.empty());

    public CredentialMaterial {
        authType = Objects.requireNonNullElse(authType, CredentialAuthType.NONE);
        secretRef = Objects.requireNonNullElse(secretRef, "");
        secretValue = Objects.requireNonNullElseGet(secretValue, SecretValue::empty);
        if (authType.isSecretBacked() && secretRef.isBlank()) {
            throw new IllegalArgumentException("secretRef must not be blank for " + authType);
        }
        if (authType.isSecretBacked() && !secretValue.hasText()) {
            throw new IllegalArgumentException("secret value must not be blank for " + authType);
        }
    }

    public static CredentialMaterial none() {
        return NONE;
    }

    public static CredentialMaterial staticBearer(String secretRef, SecretValue secretValue) {
        return new CredentialMaterial(CredentialAuthType.STATIC_BEARER, secretRef, secretValue);
    }

    public static CredentialMaterial clientCredentialsBearer(String clientSecretRef, SecretValue accessToken) {
        return new CredentialMaterial(CredentialAuthType.CLIENT_CREDENTIALS, clientSecretRef, accessToken);
    }
}
