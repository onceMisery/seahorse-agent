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
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpOAuthCredentialTests {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String TENANT_ID = "tenant-a";
    private static final String SERVER_ID = "weather";
    private static final String CLIENT_ID = "weather-client";
    private static final String CLIENT_SECRET_REF = "secret:mcp/weather-client";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String AUDIENCE = "https://weather.example";
    private static final String RESOURCE = "weather-api";

    @Test
    void shouldExposeClientCredentialsConfigurationWithoutMagicStrings() {
        McpHttpAdapterProperties.Server server = new McpHttpAdapterProperties.Server();

        server.setAuthType(CredentialAuthType.CLIENT_CREDENTIALS);
        server.setTenantId(TENANT_ID);
        server.setClientId(CLIENT_ID);
        server.setClientSecretRef(CLIENT_SECRET_REF);
        server.setScopes(List.of("weather.write", "weather.read"));
        server.setAudience(AUDIENCE);
        server.setResource(RESOURCE);
        server.setAuthorizationServerMetadataUrl("https://auth.example/.well-known/oauth-authorization-server");
        server.setProtectedResourceMetadataUrl("https://weather.example/.well-known/oauth-protected-resource");

        Assertions.assertEquals(CredentialAuthType.CLIENT_CREDENTIALS, server.getAuthType());
        Assertions.assertEquals(TENANT_ID, server.getTenantId());
        Assertions.assertEquals(CLIENT_ID, server.getClientId());
        Assertions.assertEquals(CLIENT_SECRET_REF, server.getClientSecretRef());
        Assertions.assertEquals(List.of("weather.write", "weather.read"), server.getScopes());
        Assertions.assertEquals(AUDIENCE, server.getAudience());
        Assertions.assertEquals(RESOURCE, server.getResource());
        Assertions.assertEquals(
                "https://auth.example/.well-known/oauth-authorization-server",
                server.getAuthorizationServerMetadataUrl());
        Assertions.assertEquals(
                "https://weather.example/.well-known/oauth-protected-resource",
                server.getProtectedResourceMetadataUrl());
    }

    @Test
    void shouldMapClientCredentialsServerToCredentialRequestAndRegisterRemoteTool() {
        AtomicReference<CredentialRequest> capturedRequest = new AtomicReference<>();

        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(OkHttpClient.class, () -> mcpDiscoveryClient(null))
                .withBean(CredentialProviderPort.class, () -> request -> {
                    capturedRequest.set(request);
                    return CredentialMaterial.clientCredentialsBearer(
                            request.clientSecretRef(), SecretValue.of(ACCESS_TOKEN));
                })
                .withConfiguration(AutoConfigurations.of(McpHttpAutoConfiguration.class))
                .withPropertyValues(
                        "seahorse-agent.adapters.mcp.enabled=true",
                        "seahorse-agent.adapters.mcp.servers[0].name=" + SERVER_ID,
                        "seahorse-agent.adapters.mcp.servers[0].url=http://mcp.example",
                        "seahorse-agent.adapters.mcp.servers[0].auth-type=client_credentials",
                        "seahorse-agent.adapters.mcp.servers[0].tenant-id=" + TENANT_ID,
                        "seahorse-agent.adapters.mcp.servers[0].client-id=" + CLIENT_ID,
                        "seahorse-agent.adapters.mcp.servers[0].client-secret-ref=" + CLIENT_SECRET_REF,
                        "seahorse-agent.adapters.mcp.servers[0].scopes[0]=weather.write",
                        "seahorse-agent.adapters.mcp.servers[0].scopes[1]=weather.read",
                        "seahorse-agent.adapters.mcp.servers[0].audience=" + AUDIENCE,
                        "seahorse-agent.adapters.mcp.servers[0].resource=" + RESOURCE);

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NativeMcpToolRegistry.class);
            NativeMcpToolRegistry registry = context.getBean(NativeMcpToolRegistry.class);
            assertThat(registry.findTool("weather_query")).isPresent();

            CredentialRequest request = capturedRequest.get();
            assertThat(request).isNotNull();
            assertThat(request.authType()).isEqualTo(CredentialAuthType.CLIENT_CREDENTIALS);
            assertThat(request.tenantId()).isEqualTo(TENANT_ID);
            assertThat(request.serverId()).isEqualTo(SERVER_ID);
            assertThat(request.clientId()).isEqualTo(CLIENT_ID);
            assertThat(request.clientSecretRef()).isEqualTo(CLIENT_SECRET_REF);
            assertThat(request.scopes()).containsExactly("weather.read", "weather.write");
            assertThat(request.audience()).isEqualTo(AUDIENCE);
            assertThat(request.resource()).isEqualTo(RESOURCE);
        });
    }

    @Test
    void shouldFailClosedWhenClientCredentialsConfigurationIsIncomplete() {
        AtomicReference<CredentialRequest> capturedRequest = new AtomicReference<>();

        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(OkHttpClient.class, () -> mcpDiscoveryClient(null))
                .withBean(CredentialProviderPort.class, () -> request -> {
                    capturedRequest.set(request);
                    return CredentialMaterial.clientCredentialsBearer(
                            request.clientSecretRef(), SecretValue.of(ACCESS_TOKEN));
                })
                .withConfiguration(AutoConfigurations.of(McpHttpAutoConfiguration.class))
                .withPropertyValues(
                        "seahorse-agent.adapters.mcp.enabled=true",
                        "seahorse-agent.adapters.mcp.servers[0].name=" + SERVER_ID,
                        "seahorse-agent.adapters.mcp.servers[0].url=http://mcp.example",
                        "seahorse-agent.adapters.mcp.servers[0].auth-type=client_credentials",
                        "seahorse-agent.adapters.mcp.servers[0].tenant-id=" + TENANT_ID,
                        "seahorse-agent.adapters.mcp.servers[0].client-secret-ref=" + CLIENT_SECRET_REF);

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NativeMcpToolRegistry.class);
            NativeMcpToolRegistry registry = context.getBean(NativeMcpToolRegistry.class);
            assertThat(registry.findTool("weather_query")).isEmpty();
            assertThat(capturedRequest).hasValue(null);
        });
    }

    @Test
    void shouldInjectClientCredentialsBearerMaterialIntoMcpRequests() {
        AtomicReference<String> authorization = new AtomicReference<>();
        OkHttpClient httpClient = mcpDiscoveryClient(authorization);
        CredentialMaterial credentialMaterial = CredentialMaterial.clientCredentialsBearer(
                CLIENT_SECRET_REF, SecretValue.of(ACCESS_TOKEN));
        StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                httpClient, new ObjectMapper(), SERVER_ID, "http://mcp.example", credentialMaterial);

        Assertions.assertTrue(client.initialize());

        Assertions.assertEquals("Bearer " + ACCESS_TOKEN, authorization.get());
    }

    private OkHttpClient mcpDiscoveryClient(AtomicReference<String> authorization) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (authorization != null) {
                        authorization.set(chain.request().header("Authorization"));
                    }
                    String responseJson = switch (readMethod(chain.request().body())) {
                        case "initialize" -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
                        case "tools/list" -> "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":["
                                + "{\"name\":\"weather_query\",\"description\":\"Query weather\","
                                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}";
                        default -> "{\"jsonrpc\":\"2.0\",\"result\":{}}";
                    };
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseJson, JSON))
                            .build();
                })
                .build();
    }

    private String readMethod(okhttp3.RequestBody body) {
        if (body == null) {
            return "";
        }
        try {
            okio.Buffer buffer = new okio.Buffer();
            body.writeTo(buffer);
            return new ObjectMapper().readTree(buffer.readUtf8()).path("method").asText();
        } catch (Exception ex) {
            return "";
        }
    }
}
