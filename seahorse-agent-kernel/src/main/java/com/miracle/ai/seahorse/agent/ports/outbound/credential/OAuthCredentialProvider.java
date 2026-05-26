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

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Credential provider that resolves static bearer and OAuth client credentials.
 */
public class OAuthCredentialProvider implements CredentialProviderPort {

    private static final String MSG_SECRET_NOT_FOUND = "secretRef not found";
    private static final String MSG_UNSUPPORTED_AUTH_TYPE = "unsupported credential auth type";

    private final SecretStorePort secretStorePort;
    private final OAuthTokenPort oauthTokenPort;
    private final OAuthTokenCachePort tokenCachePort;
    private final Clock clock;

    public OAuthCredentialProvider(SecretStorePort secretStorePort,
                                   OAuthTokenPort oauthTokenPort,
                                   OAuthTokenCachePort tokenCachePort,
                                   Clock clock) {
        this.secretStorePort = Objects.requireNonNull(secretStorePort, "secretStorePort must not be null");
        this.oauthTokenPort = Objects.requireNonNull(oauthTokenPort, "oauthTokenPort must not be null");
        this.tokenCachePort = Objects.requireNonNull(tokenCachePort, "tokenCachePort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
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
            case CLIENT_CREDENTIALS -> resolveClientCredentials(safeRequest);
            case USER_DELEGATED -> throw new IllegalArgumentException(MSG_UNSUPPORTED_AUTH_TYPE
                    + ": " + safeRequest.authType());
        };
    }

    private CredentialMaterial resolveClientCredentials(CredentialRequest request) {
        SecretValue clientSecret = secretStorePort.getSecret(request.clientSecretRef())
                .orElseThrow(() -> new IllegalArgumentException(MSG_SECRET_NOT_FOUND));
        OAuthTokenRequest tokenRequest = OAuthTokenRequest.clientCredentials(
                request.tenantId(),
                request.serverId(),
                request.clientId(),
                request.clientSecretRef(),
                clientSecret,
                request.scopes(),
                request.audience(),
                request.resource());
        OAuthTokenCacheKey cacheKey = OAuthTokenCacheKey.from(tokenRequest);
        OAuthToken token = tokenCachePort.get(cacheKey)
                .orElseGet(() -> requestAndCacheToken(cacheKey, tokenRequest));
        return CredentialMaterial.clientCredentialsBearer(request.clientSecretRef(), token.accessToken());
    }

    private OAuthToken requestAndCacheToken(OAuthTokenCacheKey cacheKey, OAuthTokenRequest tokenRequest) {
        OAuthToken token = oauthTokenPort.getToken(tokenRequest);
        Duration ttl = OAuthTokenTtlPolicy.cacheTtl(token, clock);
        tokenCachePort.put(cacheKey, token, ttl);
        return token;
    }
}
