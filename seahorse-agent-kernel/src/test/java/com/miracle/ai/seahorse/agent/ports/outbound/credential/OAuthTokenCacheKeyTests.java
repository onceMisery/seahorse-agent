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

import java.util.List;

class OAuthTokenCacheKeyTests {

    private static final String TENANT_ID = "tenant-a";
    private static final String SERVER_ID = "weather";
    private static final String CLIENT_ID = "weather-client";
    private static final String CLIENT_SECRET_REF = "secret:mcp/weather-client";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String AUDIENCE = "api://weather";
    private static final String RESOURCE = "https://mcp.weather.example";

    @Test
    void shouldUseNormalizedScopesInCacheKey() {
        OAuthTokenRequest first = request(List.of("weather.write", "weather.read"));
        OAuthTokenRequest second = request(List.of("weather.read", "weather.write", "weather.read"));

        Assertions.assertEquals(OAuthTokenCacheKey.from(first), OAuthTokenCacheKey.from(second));
        Assertions.assertEquals(List.of("weather.read", "weather.write"), OAuthTokenCacheKey.from(first).scopes());
    }

    @Test
    void shouldSeparateAudienceAndResourceInCacheKey() {
        OAuthTokenCacheKey first = OAuthTokenCacheKey.from(request(List.of("weather.read")));
        OAuthTokenCacheKey differentAudience = OAuthTokenCacheKey.from(OAuthTokenRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                SecretValue.of(CLIENT_SECRET),
                List.of("weather.read"),
                "api://other-weather",
                RESOURCE));
        OAuthTokenCacheKey differentResource = OAuthTokenCacheKey.from(OAuthTokenRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                SecretValue.of(CLIENT_SECRET),
                List.of("weather.read"),
                AUDIENCE,
                "https://other-resource.example"));

        Assertions.assertNotEquals(first, differentAudience);
        Assertions.assertNotEquals(first, differentResource);
    }

    private OAuthTokenRequest request(List<String> scopes) {
        return OAuthTokenRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                SecretValue.of(CLIENT_SECRET),
                scopes,
                AUDIENCE,
                RESOURCE);
    }
}
