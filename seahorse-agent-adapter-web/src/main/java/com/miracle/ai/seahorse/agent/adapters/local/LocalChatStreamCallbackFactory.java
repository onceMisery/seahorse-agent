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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamMessageDelta;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamMetaPayload;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * Local stream chat callback factory.
 */
public class LocalChatStreamCallbackFactory implements ChatStreamCallbackFactoryPort {

    private static final int DEFAULT_MESSAGE_CHUNK_SIZE = 5;
    private static final String TYPE_THINK = "think";
    private static final String TYPE_RESPONSE = "response";
    private static final String DONE_PAYLOAD = "[DONE]";

    private final StreamTaskPort streamTaskPort;
    private final ConversationMemoryPort memoryPort;

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort) {
        this(streamTaskPort, ConversationMemoryPort.noop());
    }

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort, ConversationMemoryPort memoryPort) {
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
    }

    @Override
    public StreamCallback create(SseEmitter emitter, String conversationId, String taskId) {
        return create(emitter, conversationId, taskId, "");
    }

    @Override
    public StreamCallback create(SseEmitter emitter, String conversationId, String taskId, String userId) {
        return new LocalChatStreamCallback(emitter, conversationId, taskId, userId, streamTaskPort, memoryPort);
    }

    private static final class LocalChatStreamCallback implements StreamCallback {

        private final StreamEventSender sender;
        private final String conversationId;
        private final String taskId;
        private final String userId;
        private final StreamTaskPort streamTaskPort;
        private final ConversationMemoryPort memoryPort;
        private final StringBuilder answer = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();

        private LocalChatStreamCallback(SseEmitter emitter,
                                        String conversationId,
                                        String taskId,
                                        String userId,
                                        StreamTaskPort streamTaskPort,
                                        ConversationMemoryPort memoryPort) {
            this.sender = new SpringSseEventSender(Objects.requireNonNull(emitter, "emitter must not be null"));
            this.conversationId = conversationId;
            this.taskId = taskId;
            this.userId = Objects.requireNonNullElse(userId, "");
            this.streamTaskPort = streamTaskPort;
            this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
            initialize();
        }

        @Override
        public void onContent(String content) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(content)) {
                return;
            }
            answer.append(content);
            sendChunked(TYPE_RESPONSE, content);
        }

        @Override
        public void onThinking(String content) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(content)) {
                return;
            }
            thinking.append(content);
            sendChunked(TYPE_THINK, content);
        }

        @Override
        public void onRunStarted(String runId) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(runId)) {
                return;
            }
            sender.sendEvent(StreamEventType.META.value(), new StreamMetaPayload(conversationId, taskId, runId));
        }

        @Override
        public void onComplete() {
            if (streamTaskPort.isCancelled(taskId)) {
                return;
            }
            appendAssistantMessage();
            sender.sendEvent(StreamEventType.FINISH.value(), new StreamCompletionPayload(null, null));
            sender.sendEvent(StreamEventType.DONE.value(), DONE_PAYLOAD);
            streamTaskPort.unregister(taskId);
            sender.complete();
        }

        @Override
        public void onError(Throwable error) {
            if (streamTaskPort.isCancelled(taskId)) {
                return;
            }
            streamTaskPort.unregister(taskId);
            sender.fail(error);
        }

        private void initialize() {
            sender.sendEvent(StreamEventType.META.value(), new StreamMetaPayload(conversationId, taskId));
            streamTaskPort.register(taskId, sender, () -> new StreamCompletionPayload(null, null));
        }

        private void appendAssistantMessage() {
            if (isBlank(conversationId) || isBlank(userId) || answer.isEmpty()) {
                return;
            }
            memoryPort.append(conversationId, userId,
                    ChatMessage.assistant(answer.toString(), thinking.toString(), null));
        }

        private void sendChunked(String type, String content) {
            int length = content.length();
            int index = 0;
            int count = 0;
            StringBuilder buffer = new StringBuilder();
            while (index < length) {
                int codePoint = content.codePointAt(index);
                buffer.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
                count++;
                if (count >= DEFAULT_MESSAGE_CHUNK_SIZE) {
                    sendMessage(type, buffer);
                    count = 0;
                }
            }
            if (!buffer.isEmpty()) {
                sendMessage(type, buffer);
            }
        }

        private void sendMessage(String type, StringBuilder buffer) {
            sender.sendEvent(StreamEventType.MESSAGE.value(), new StreamMessageDelta(type, buffer.toString()));
            buffer.setLength(0);
        }

        private boolean isBlank(String content) {
            return content == null || content.isBlank();
        }
    }
}
