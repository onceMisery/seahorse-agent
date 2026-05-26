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

class OAuthTokenRequestTests {

    private static final String TENANT_ID = "tenant-a";
    private static final String SERVER_ID = "weather";
    private static final String CLIENT_ID = "weather-client";
    private static final String CLIENT_SECRET_REF = "secret:mcp/weather-client";
    private static final String RAW_CLIENT_SECRET = "client-secret-value";
    private static final String AUDIENCE = "api://weather";
    private static final String RESOURCE = "https://mcp.weather.example";

    @Test
    void shouldNormalizeClientCredentialsRequestScopesAndRedactClientSecret() {
        OAuthTokenRequest request = OAuthTokenRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                SecretValue.of(RAW_CLIENT_SECRET),
                List.of("weather.write", "weather.read", "weather.read", " "),
                AUDIENCE,
                RESOURCE);

        Assertions.assertEquals(OAuthGrantType.CLIENT_CREDENTIALS, request.grantType());
        Assertions.assertEquals(List.of("weather.read", "weather.write"), request.scopes());
        Assertions.assertEquals(CLIENT_SECRET_REF, request.clientSecretRef());
        Assertions.assertEquals(RAW_CLIENT_SECRET, request.clientSecret().reveal());
        Assertions.assertFalse(request.toString().contains(RAW_CLIENT_SECRET));
    }

    @Test
    void shouldRejectBlankClientCredentialsRequiredFields() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> OAuthTokenRequest.clientCredentials(
                "",
                SERVER_ID,
                CLIENT_ID,
                CLIENT_SECRET_REF,
                SecretValue.of(RAW_CLIENT_SECRET),
                List.of("weather.read"),
                AUDIENCE,
                RESOURCE));
        Assertions.assertThrows(IllegalArgumentException.class, () -> OAuthTokenRequest.clientCredentials(
                TENANT_ID,
                SERVER_ID,
                "",
                CLIENT_SECRET_REF,
                SecretValue.of(RAW_CLIENT_SECRET),
                List.of("weather.read"),
                AUDIENCE,
                RESOURCE));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CredentialRequest.userDelegated(
                TENANT_ID,
                SERVER_ID,
                CLIENT_ID,
                List.of("weather.read"),
                AUDIENCE,
                RESOURCE));
    }
}
