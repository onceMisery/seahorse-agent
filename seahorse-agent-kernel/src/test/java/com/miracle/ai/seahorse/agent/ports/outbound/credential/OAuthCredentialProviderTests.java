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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class OAuthCredentialProviderTests {

    private static final String TENANT_ID = "tenant-a";
    private static final String SERVER_ID = "weather";
    private static final String CLIENT_ID = "weather-client";
    private static final String CLIENT_SECRET_REF = "secret:mcp/weather-client";
    private static final String RAW_CLIENT_SECRET = "client-secret-value";
    private static final String RAW_ACCESS_TOKEN = "oauth-access-token";
    private static final String AUDIENCE = "api://weather";
    private static final String RESOURCE = "https://mcp.weather.example";
    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldResolveClientCredentialsTokenAndCacheIt() {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicReference<OAuthTokenRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<Duration> capturedTtl = new AtomicReference<>();
        OAuthTokenCachePort cache = new InMemoryOAuthTokenCachePort(Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            public void put(OAuthTokenCacheKey key, OAuthToken token, Duration ttl) {
                capturedTtl.set(ttl);
                super.put(key, token, ttl);
            }
        };
        OAuthCredentialProvider provider = new OAuthCredentialProvider(
                secretStore(),
                request -> {
                    tokenRequests.incrementAndGet();
                    capturedRequest.set(request);
                    return OAuthToken.bearer(
                            SecretValue.of(RAW_ACCESS_TOKEN),
                            NOW.plus(Duration.ofMinutes(10)),
                            request.scopes(),
                            "");
                },
                cache,
                Clock.fixed(NOW, ZoneOffset.UTC));

        CredentialRequest credentialRequest = CredentialRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                List.of("weather.write", "weather.read"),
                AUDIENCE,
                RESOURCE);

        CredentialMaterial first = provider.resolve(credentialRequest);
        CredentialMaterial second = provider.resolve(credentialRequest);

        Assertions.assertEquals(CredentialAuthType.CLIENT_CREDENTIALS, first.authType());
        Assertions.assertEquals(RAW_ACCESS_TOKEN, first.secretValue().reveal());
        Assertions.assertEquals(RAW_ACCESS_TOKEN, second.secretValue().reveal());
        Assertions.assertEquals(1, tokenRequests.get());
        Assertions.assertEquals(RAW_CLIENT_SECRET, capturedRequest.get().clientSecret().reveal());
        Assertions.assertEquals(List.of("weather.read", "weather.write"), capturedRequest.get().scopes());
        Assertions.assertTrue(capturedTtl.get().compareTo(Duration.ZERO) > 0);
        Assertions.assertTrue(capturedTtl.get().compareTo(Duration.ofMinutes(10)) < 0);
    }

    @Test
    void shouldNotExposeClientSecretOrAccessTokenInStringRepresentations() {
        OAuthCredentialProvider provider = new OAuthCredentialProvider(
                secretStore(),
                request -> OAuthToken.bearer(
                        SecretValue.of(RAW_ACCESS_TOKEN),
                        NOW.plus(Duration.ofMinutes(10)),
                        request.scopes(),
                        ""),
                new InMemoryOAuthTokenCachePort(Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        CredentialMaterial material = provider.resolve(CredentialRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                List.of("weather.read"),
                AUDIENCE,
                RESOURCE));

        Assertions.assertFalse(material.toString().contains(RAW_ACCESS_TOKEN));
        Assertions.assertFalse(provider.toString().contains(RAW_CLIENT_SECRET));
    }

    @Test
    void shouldFailClosedWhenClientSecretRefIsMissing() {
        OAuthCredentialProvider provider = new OAuthCredentialProvider(
                secretRef -> Optional.empty(),
                request -> OAuthToken.bearer(
                        SecretValue.of(RAW_ACCESS_TOKEN),
                        NOW.plus(Duration.ofMinutes(10)),
                        request.scopes(),
                        ""),
                new InMemoryOAuthTokenCachePort(Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class, () -> provider.resolve(
                CredentialRequest.clientCredentials(
                        TENANT_ID,
                        SERVER_ID,
                        CLIENT_ID,
                        CLIENT_SECRET_REF,
                        List.of("weather.read"),
                        AUDIENCE,
                        RESOURCE)));

        Assertions.assertFalse(error.getMessage().contains(RAW_CLIENT_SECRET));
        Assertions.assertFalse(error.getMessage().contains(RAW_ACCESS_TOKEN));
    }

    private SecretStorePort secretStore() {
        return secretRef -> CLIENT_SECRET_REF.equals(secretRef)
                ? Optional.of(SecretValue.of(RAW_CLIENT_SECRET))
                : Optional.empty();
    }
}
