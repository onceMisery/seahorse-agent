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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleStreamingChatToolsTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ToolDescriptor WEATHER = new ToolDescriptor(
            "weather", "Weather", "查询天气", "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");

    @Test
    void serializesToolsOnlyWhenPresent() throws Exception {
        CapturingInterceptor interceptor = new CapturingInterceptor(done(), done());
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("天气")))
                .tools(List.of(WEATHER))
                .build(), new RecordingCallback(), calls -> { });
        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("你好")))
                .build(), new RecordingCallback(), calls -> { });

        JsonNode withTools = MAPPER.readTree(interceptor.requestBodies.get(0));
        assertThat(withTools.path("tools")).hasSize(1);
        assertThat(withTools.at("/tools/0/type").asText()).isEqualTo("function");
        assertThat(withTools.at("/tools/0/function/name").asText()).isEqualTo("weather");
        assertThat(withTools.at("/tools/0/function/parameters/type").asText()).isEqualTo("object");
        assertThat(withTools.path("tool_choice").asText()).isEqualTo("auto");

        JsonNode withoutTools = MAPPER.readTree(interceptor.requestBodies.get(1));
        assertThat(withoutTools.has("tools")).isFalse();
        assertThat(withoutTools.has("tool_choice")).isFalse();
    }

    @Test
    void streamChatWithToolsUsesRequestModelId() throws Exception {
        CapturingInterceptor interceptor = new CapturingInterceptor(done());
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .modelId("agent-chat-model")
                .samplingOptions(ChatSamplingOptions.builder()
                        .topK(30)
                        .thinking(true)
                        .build())
                .build(), new RecordingCallback(), calls -> { });

        JsonNode payload = MAPPER.readTree(interceptor.requestBodies.get(0));
        assertThat(payload.path("model").asText()).isEqualTo("agent-chat-model");
        assertThat(payload.path("top_k").asInt()).isEqualTo(30);
        assertThat(payload.path("thinking").asBoolean()).isTrue();
    }

    @Test
    void requiredToolChoiceWithoutToolsIsRejectedBeforeHttpRequest() {
        CapturingInterceptor interceptor = new CapturingInterceptor(done());
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);

        assertThatThrownBy(() -> adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("必须用工具")))
                .toolChoice("required")
                .build(), new RecordingCallback(), calls -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool_choice=required");
        assertThat(interceptor.requestBodies).isEmpty();
    }

    @Test
    void aggregatesToolCallDeltasAndDoesNotEmitRawJsonAsContent() {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"weather","arguments":"{\\"city\\""}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":\\"SH\\"}"}}]}}]}

                data: [DONE]

                """);
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);
        RecordingCallback callback = new RecordingCallback();
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("天气")))
                .tools(List.of(WEATHER))
                .build(), callback, seen::set);

        assertThat(callback.contents).isEmpty();
        assertThat(seen.get()).hasSize(1);
        AgentToolCall call = seen.get().get(0);
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.toolId()).isEqualTo("weather");
        assertThat(call.arguments()).containsEntry("city", "SH");
    }

    @Test
    void pureContentStreamStillCallsCollectorWithEmptyList() {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                data: {"choices":[{"delta":{"content":"hello"}}]}

                data: [DONE]

                """);
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);
        RecordingCallback callback = new RecordingCallback();
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .build(), callback, seen::set);

        assertThat(callback.contents).containsExactly("hello");
        assertThat(seen.get()).isEmpty();
        assertThat(callback.completeCount).isEqualTo(1);
    }

    @Test
    void invalidArgumentsJsonIsExposedAsRawArgument() {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"weather","arguments":"{bad"}}]}}]}

                data: [DONE]

                """);
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("天气")))
                .tools(List.of(WEATHER))
                .build(), new RecordingCallback(), seen::set);

        assertThat(seen.get()).hasSize(1);
        assertThat(seen.get().get(0).arguments()).containsEntry("_raw", "{bad");
    }

    @Test
    void malformedSseJsonReportsErrorAndDoesNotCallCollector() {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                data: {bad-json

                data: [DONE]

                """);
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);
        ErrorRecordingCallback callback = new ErrorRecordingCallback();
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("天气")))
                .tools(List.of(WEATHER))
                .build(), callback, seen::set);

        assertThat(callback.error).isNotNull();
        assertThat(callback.completeCount).isZero();
        assertThat(seen.get()).isNull();
    }

    @Test
    void streamEofBeforeDoneReportsErrorAndDoesNotCallCollector() {
        CapturingInterceptor interceptor = new CapturingInterceptor("""
                data: {"choices":[{"delta":{"content":"partial"}}]}

                """);
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);
        ErrorRecordingCallback callback = new ErrorRecordingCallback();
        AtomicReference<List<AgentToolCall>> seen = new AtomicReference<>();

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("天气")))
                .tools(List.of(WEATHER))
                .build(), callback, seen::set);

        assertThat(callback.error).isNotNull();
        assertThat(callback.completeCount).isZero();
        assertThat(seen.get()).isNull();
    }


    @Test
    void serializesAssistantToolCallsAndToolMessages() throws Exception {
        AgentToolCall call = AgentToolCall.of("call-1", "weather", Map.of("city", "SH"));
        CapturingInterceptor interceptor = new CapturingInterceptor(done());
        OpenAiCompatibleModelAdapter adapter = adapter(interceptor);

        adapter.streamChatWithTools(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.user("天气"),
                        ChatMessage.assistantToolCalls("先查天气", List.of(call)),
                        ChatMessage.tool("call-1", "{\"temp\":21}")))
                .tools(List.of(WEATHER))
                .build(), new RecordingCallback(), calls -> { });

        JsonNode messages = MAPPER.readTree(interceptor.requestBodies.get(0)).path("messages");
        assertThat(messages.at("/1/role").asText()).isEqualTo("assistant");
        assertThat(messages.at("/1/tool_calls/0/id").asText()).isEqualTo("call-1");
        assertThat(messages.at("/1/tool_calls/0/function/name").asText()).isEqualTo("weather");
        assertThat(messages.at("/1/tool_calls/0/function/arguments").asText()).contains("\"city\":\"SH\"");
        assertThat(messages.at("/2/role").asText()).isEqualTo("tool");
        assertThat(messages.at("/2/tool_call_id").asText()).isEqualTo("call-1");
    }

    private static OpenAiCompatibleModelAdapter adapter(CapturingInterceptor interceptor) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build();
        return new OpenAiCompatibleModelAdapter(
                client,
                MAPPER,
                new OpenAiCompatibleModelProperties("http://127.0.0.1/v1", "", "gpt-test", "", "", List.of()),
                Runnable::run);
    }

    private static String done() {
        return """
                data: [DONE]

                """;
    }

    private static final class CapturingInterceptor implements Interceptor {
        private final Queue<String> responses;
        private final List<String> requestBodies = new ArrayList<>();

        private CapturingInterceptor(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Buffer buffer = new Buffer();
            if (chain.request().body() != null) {
                chain.request().body().writeTo(buffer);
            }
            requestBodies.add(buffer.readUtf8());
            String response = responses.isEmpty() ? done() : responses.remove();
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(response, MediaType.get("text/event-stream; charset=utf-8")))
                    .build();
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final List<String> contents = new ArrayList<>();
        private int completeCount;

        @Override
        public void onContent(String content) {
            contents.add(content);
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

    private static final class ErrorRecordingCallback implements StreamCallback {
        private Throwable error;
        private int completeCount;

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
            completeCount++;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}
