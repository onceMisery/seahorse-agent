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
 * Minimal credential provider that resolves static bearer material from a secret store.
 */
public class SecretStoreCredentialProvider implements CredentialProviderPort {

    private static final String MSG_SECRET_NOT_FOUND = "secretRef not found";
    private static final String MSG_UNSUPPORTED_AUTH_TYPE = "unsupported credential auth type";

    private final SecretStorePort secretStorePort;

    public SecretStoreCredentialProvider(SecretStorePort secretStorePort) {
        this.secretStorePort = Objects.requireNonNull(secretStorePort, "secretStorePort must not be null");
    }

    @Override
    public CredentialMaterial resolve(CredentialRequest request) {
        CredentialRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return switch (safeRequest.authType()) {
            case NONE -> CredentialMaterial.none();
            case STATIC_BEARER -> CredentialMaterial.staticBearer(
                    safeRequest.secretRef(),
                    secretStorePort.getSecret(safeRequest.secretRef())
                            .orElseThrow(() -> new IllegalArgumentException(MSG_SECRET_NOT_FOUND)));
            default -> throw new IllegalArgumentException(MSG_UNSUPPORTED_AUTH_TYPE + ": " + safeRequest.authType());
        };
    }
}
