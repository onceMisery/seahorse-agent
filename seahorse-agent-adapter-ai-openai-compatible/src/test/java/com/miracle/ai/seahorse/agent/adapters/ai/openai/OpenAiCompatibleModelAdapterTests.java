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

package com.miracle.ai.seahorse.agent.adapters.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationResult;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleModelAdapterTests {

    @Test
    void shouldSubmitStreamConsumptionToConfiguredExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                new OkHttpClient(),
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("http://127.0.0.1:65535/v1", "", "gpt-test", "", "", List.of()),
                executor);

        adapter.streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build(), new NoopStreamCallback());

        assertThat(executor.tasks).hasSize(1);
    }

    @Test
    void shouldOmitThinkingWhenDisabledForOpenAiCompatiblePayload() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    capturedBody.set(buffer.readUtf8());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("data: [DONE]\n\n", null))
                            .build();
                })
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("http://127.0.0.1:65535/v1", "", "gpt-test", "", "", List.of()),
                Runnable::run);

        adapter.streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .samplingOptions(ChatSamplingOptions.builder()
                        .thinking(false)
                        .build())
                .build(), new NoopStreamCallback());

        assertThat(capturedBody.get()).doesNotContain("\"thinking\"");
    }

    @Test
    void shouldRequestTokenUsageForStreamingChat() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    capturedBody.set(buffer.readUtf8());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("data: [DONE]\n\n", null))
                            .build();
                })
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("http://127.0.0.1:65535/v1", "", "gpt-test", "", "", List.of()),
                Runnable::run);

        adapter.streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build(), new NoopStreamCallback());

        assertThat(capturedBody.get()).contains("\"stream_options\":{\"include_usage\":true}");
    }

    @Test
    void shouldEmitTokenUsageFromStreamingChatUsageChunk() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("""
                                data: {"choices":[{"delta":{"content":"hello"}}]}

                                data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17}}

                                data: [DONE]

                                """, null))
                        .build())
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("http://127.0.0.1:65535/v1", "", "gpt-test", "", "", List.of()),
                Runnable::run);
        UsageRecordingCallback callback = new UsageRecordingCallback();

        adapter.streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build(), callback);

        assertThat(callback.contents).containsExactly("hello");
        assertThat(callback.usage.get()).isEqualTo(new ChatTokenUsage(12, 5));
        assertThat(callback.completeCount).isEqualTo(1);
    }

    @Test
    void shouldGenerateImageThroughOpenAiCompatibleImageEndpoint() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    capturedPath.set(chain.request().url().encodedPath());
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    capturedBody.set(buffer.readUtf8());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("""
                                    {"data":[{"url":"https://cdn.example.com/redis.png"}]}
                                    """, null))
                            .build();
                })
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("https://apihub.agnes-ai.com", "",
                        "gpt-test", "", "", "agnes-image-2.0-flash", List.of()),
                Runnable::run);

        ImageGenerationResult result = adapter.generate(new ImageGenerationRequest(
                "Draw Redis architecture", null, "1024x1024", "technical diagram", "url"));

        assertThat(capturedPath.get()).isEqualTo("/v1/images/generations");
        assertThat(capturedBody.get()).contains("\"model\":\"agnes-image-2.0-flash\"");
        assertThat(capturedBody.get()).contains("\"prompt\":\"Draw Redis architecture\"");
        assertThat(capturedBody.get()).contains("\"size\":\"1024x1024\"");
        assertThat(capturedBody.get()).doesNotContain("response_format");
        assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/redis.png");
        assertThat(result.model()).isEqualTo("agnes-image-2.0-flash");
    }

    @Test
    void shouldOnlySendImageResponseFormatWhenBase64IsExplicitlyRequested() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    capturedBody.set(buffer.readUtf8());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("""
                                    {"data":[{"b64_json":"abc"}]}
                                    """, null))
                            .build();
                })
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("https://apihub.agnes-ai.com", "",
                        "gpt-test", "", "", "agnes-image-2.0-flash", List.of()),
                Runnable::run);

        adapter.generate(new ImageGenerationRequest(
                "Draw Redis architecture", null, "1024x1024", null, "b64_json"));

        assertThat(capturedBody.get()).contains("\"response_format\":\"b64_json\"");
    }

    @Test
    void shouldRetryImageGenerationWithoutResponseFormatWhenProviderRejectsIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> bodies = new ArrayList<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    bodies.add(buffer.readUtf8());
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(400)
                                .message("Bad Request")
                                .body(ResponseBody.create("""
                                        {"error":{"message":"UnsupportedParamsError: Setting `response_format` is not supported"}}
                                        """, null))
                                .build();
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("""
                                    {"data":[{"url":"https://cdn.example.com/fallback.png"}]}
                                    """, null))
                            .build();
                })
                .build();
        OpenAiCompatibleModelAdapter adapter = new OpenAiCompatibleModelAdapter(
                httpClient,
                new ObjectMapper(),
                new OpenAiCompatibleModelProperties("https://apihub.agnes-ai.com", "",
                        "gpt-test", "", "", "agnes-image-2.0-flash", List.of()),
                Runnable::run);

        ImageGenerationResult result = adapter.generate(new ImageGenerationRequest(
                "Draw Seahorse Agent", null, "1024x1024", null, "b64_json"));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(bodies.get(0)).contains("\"response_format\":\"b64_json\"");
        assertThat(bodies.get(1)).doesNotContain("response_format");
        assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/fallback.png");
        assertThat(result.b64Json()).isBlank();
    }

    @Test
    void shouldNormalizeBareProviderBaseUrlToOpenAiV1Path() {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                "https://apihub.agnes-ai.com", "", "gpt-test", "", "", "image-test", List.of());

        assertThat(properties.baseUrl()).isEqualTo("https://apihub.agnes-ai.com/v1");
    }

    private static final class RecordingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            // 只记录任务，不执行网络调用；该测试关注调度边界而非 SSE 协议解析。
            tasks.add(command);
        }
    }

    private static final class NoopStreamCallback implements StreamCallback {

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private static final class UsageRecordingCallback implements StreamCallback {
        private final List<String> contents = new ArrayList<>();
        private final AtomicReference<ChatTokenUsage> usage = new AtomicReference<>();
        private int completeCount;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onUsage(ChatTokenUsage usage) {
            this.usage.set(usage);
        }

        @Override
        public void onComplete() {
            completeCount++;
        }

        @Override
        public void onError(Throwable error) {
            throw new AssertionError(error);
        }
    }
}
