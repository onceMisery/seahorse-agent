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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

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
}
