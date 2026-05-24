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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class StreamableHttpMcpClientCredentialTests {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SECRET_REF = "secret:mcp/weather";
    private static final String RAW_TOKEN = "sk-live-secret";

    @Test
    void shouldInjectStaticBearerTokenIntoMcpRequests() {
        AtomicReference<String> authorization = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    authorization.set(chain.request().header(AUTHORIZATION_HEADER));
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(
                                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}",
                                    JSON))
                            .build();
                })
                .build();
        CredentialMaterial credentialMaterial = CredentialMaterial.staticBearer(
                SECRET_REF, SecretValue.of(RAW_TOKEN));
        StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                httpClient, new ObjectMapper(), "weather", "http://mcp.example", credentialMaterial);

        McpToolExecutionResult result = client.call(new McpToolExecutionRequest("weather_query", Map.of()));

        Assertions.assertTrue(result.success());
        Assertions.assertEquals("Bearer " + RAW_TOKEN, authorization.get());
    }

    @Test
    void shouldExposeStaticBearerConfigurationWithoutMagicStrings() {
        McpHttpAdapterProperties.Server server = new McpHttpAdapterProperties.Server();

        Assertions.assertEquals(CredentialAuthType.NONE, server.getAuthType());

        server.setAuthType(CredentialAuthType.STATIC_BEARER);
        server.setClientSecretRef(SECRET_REF);

        Assertions.assertEquals(CredentialAuthType.STATIC_BEARER, server.getAuthType());
        Assertions.assertEquals(SECRET_REF, server.getClientSecretRef());
    }
}
