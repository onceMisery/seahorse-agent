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

package com.miracle.ai.seahorse.agent.adapters.source.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuDocumentFetcherAdapterTests {

    private HttpServer server;
    private FeishuDocumentFetcherAdapter adapter;
    private AtomicInteger tokenRequests;
    private AtomicReference<String> authorizationHeader;

    @BeforeEach
    void setUp() throws Exception {
        tokenRequests = new AtomicInteger();
        authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", this::handleToken);
        server.createContext("/open-apis/drive/v1/files/file-1/download", this::handleDownload);
        server.start();

        FeishuDocumentSourceProperties properties = new FeishuDocumentSourceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRetryBackoffMillis(0);
        adapter = new FeishuDocumentFetcherAdapter(new OkHttpClient(), new ObjectMapper(), properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFetchFeishuFileWithTenantTokenFromAppCredentials() {
        DocumentFetchResult result = adapter.fetch(new DocumentFetchRequest(
                "feishu",
                "file-1",
                "",
                Map.of("app_id", "app-1", "app_secret", "secret-1")));

        assertThat(tokenRequests).hasValue(1);
        assertThat(authorizationHeader).hasValue("Bearer tenant-token");
        assertThat(result.content()).isEqualTo("hello feishu".getBytes(StandardCharsets.UTF_8));
        assertThat(result.mimeType()).isEqualTo("text/plain");
        assertThat(result.fileName()).isEqualTo("source.txt");
    }

    @Test
    void shouldUseExplicitTenantAccessTokenWithoutTokenRequest() {
        DocumentFetchResult result = adapter.fetch(new DocumentFetchRequest(
                "lark-drive",
                "file-1",
                "doc.txt",
                Map.of("tenant_access_token", "direct-token")));

        assertThat(tokenRequests).hasValue(0);
        assertThat(authorizationHeader).hasValue("Bearer direct-token");
        assertThat(result.fileName()).isEqualTo("doc.txt");
    }

    @Test
    void shouldRejectMissingCredentials() {
        assertThatThrownBy(() -> adapter.fetch(new DocumentFetchRequest("feishu", "file-1", "", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_access_token");
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        byte[] body = "{\"code\":0,\"tenant_access_token\":\"tenant-token\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "hello feishu".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "text/plain");
        exchange.getResponseHeaders().add("content-disposition", "attachment; filename=\"source.txt\"");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
