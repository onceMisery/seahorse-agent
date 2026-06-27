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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositorySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class JdkHttpAdaptersTests {

    @Test
    void shouldReadRawRepositoryFilesWhenGitHubMetadataIsUnavailable() {
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                "https://api.github.com/repos/onceMisery/seahorse-agent", FakeResponse.text(403, "{}"),
                "https://api.github.com/repos/onceMisery/seahorse-agent/git/trees/main?recursive=1",
                FakeResponse.text(403, "{}"),
                "https://raw.githubusercontent.com/onceMisery/seahorse-agent/main/README.md",
                FakeResponse.text(200, "# Seahorse Agent\n\nA local agent workspace.")));
        JdkHttpGitHubRepositoryPortAdapter adapter = new JdkHttpGitHubRepositoryPortAdapter(
                httpClient, new ObjectMapper(), Duration.ofSeconds(1), "test", Clock.systemUTC());

        GitHubRepositorySnapshot snapshot = adapter.read(new GitHubRepositoryRequest(
                "https://github.com/onceMisery/seahorse-agent", null, 1, 500));

        assertThat(snapshot.defaultBranch()).isEqualTo("main");
        assertThat(snapshot.htmlUrl()).isEqualTo("https://github.com/onceMisery/seahorse-agent");
        assertThat(snapshot.files()).hasSize(1);
        assertThat(snapshot.files().get(0).path()).isEqualTo("README.md");
        assertThat(snapshot.files().get(0).contentText()).contains("Seahorse Agent");
    }

    @Test
    void shouldReturnTruncatedWebFetchContentWhenResponseExceedsByteLimit() {
        String html = "<html><body>" + "x".repeat(200) + "</body></html>";
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                "http://example.com/large", FakeResponse.bytes(200, html.getBytes(StandardCharsets.UTF_8),
                        "text/html; charset=utf-8")));
        JdkHttpWebFetchPortAdapter adapter = new JdkHttpWebFetchPortAdapter(
                httpClient, new WebFetchSafetyPolicy(), Duration.ofSeconds(1), 32, "test");

        WebFetchResult result = adapter.fetch(new WebFetchRequest("http://example.com/large", 100));

        assertThat(result.status()).isEqualTo(WebFetchStatus.FETCHED);
        assertThat(result.truncated()).isTrue();
        assertThat(result.contentText()).isNotBlank();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void shouldParseDuckDuckGoHtmlSearchResults() {
        String html = """
                <html><body>
                  <div class="result">
                    <div class="result__body">
                      <h2><a class="result__a" href="/l/?uddg=https%3A%2F%2Fexample.com%2Fa&amp;rut=abc">Alpha &amp; Beta</a></h2>
                      <a class="result__snippet">Snippet <b>one</b></a>
                    </div>
                  </div>
                </body></html>
                """;
        FakeHttpClient httpClient = new FakeHttpClient(Map.of(
                "https://search.example/html/?q=seahorse+agent&kl=zh-cn", FakeResponse.text(200, html)));
        JdkHttpWebSearchPortAdapter adapter = new JdkHttpWebSearchPortAdapter(
                httpClient, "https://search.example/html/", Duration.ofSeconds(1), "test");

        WebSearchResult result = adapter.search(new WebSearchRequest("seahorse agent", "zh-CN", null, 3));

        assertThat(result.query()).isEqualTo("seahorse agent");
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).title()).isEqualTo("Alpha & Beta");
        assertThat(result.hits().get(0).url()).isEqualTo("https://example.com/a");
        assertThat(result.hits().get(0).snippet()).isEqualTo("Snippet one");
    }

    private static final class FakeHttpClient extends HttpClient {

        private final Map<String, FakeResponse> responses;

        private FakeHttpClient(Map<String, FakeResponse> responses) {
            this.responses = responses;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            FakeResponse response = responses.get(request.uri().toString());
            if (response == null) {
                throw new IOException("No fake response for " + request.uri());
            }
            Object body = response.stringBody()
                    ? new String(response.body(), StandardCharsets.UTF_8)
                    : response.body();
            return new SimpleHttpResponse<>(request, response.status(), response.headers(), (T) body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by these tests");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by these tests");
        }
    }

    private record FakeResponse(int status, byte[] body, HttpHeaders headers, boolean stringBody) {

        private static FakeResponse text(int status, String body) {
            return new FakeResponse(status, body.getBytes(StandardCharsets.UTF_8), HttpHeaders.of(
                    Map.of("content-type", java.util.List.of("application/json")), (name, value) -> true), true);
        }

        private static FakeResponse bytes(int status, byte[] body, String contentType) {
            return new FakeResponse(status, body, HttpHeaders.of(
                    Map.of("content-type", java.util.List.of(contentType)), (name, value) -> true), false);
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request,
                                         int statusCode,
                                         HttpHeaders headers,
                                         T body) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
