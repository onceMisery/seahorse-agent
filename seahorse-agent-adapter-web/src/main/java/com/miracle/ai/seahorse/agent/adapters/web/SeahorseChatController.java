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

package com.miracle.ai.seahorse.agent.adapters.web;

import cn.hutool.core.util.IdUtil;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Seahorse 原生问答 Web 入站适配器。
 */
@RestController
public class SeahorseChatController {

    private static final String DEFAULT_USER_ID = "default";

    private final ObjectProvider<ChatInboundPort> chatInboundPortProvider;
    private final ChatStreamCallbackFactoryPort callbackFactory;
    private final StreamTaskPort streamTaskPort;
    private final RateLimiterPort rateLimiterPort;
    private final long sseTimeoutMs;
    private final int chatRateLimitPermits;
    private final Duration chatRateLimitWindow;

    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  long sseTimeoutMs) {
        this(chatInboundPortProvider, callbackFactory, streamTaskPort, RateLimiterPort.noop(), sseTimeoutMs, 60,
                Duration.ofMinutes(1).toMillis());
    }

    @Autowired
    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  RateLimiterPort rateLimiterPort,
                                  @Value("${seahorse-agent.web.sse-timeout-ms:300000}")
                                  long sseTimeoutMs,
                                  @Value("${seahorse-agent.web.chat-rate-limit.permits:60}") int chatRateLimitPermits,
                                  @Value("${seahorse-agent.web.chat-rate-limit.window-ms:60000}")
                                  long chatRateLimitWindowMs) {
        this.chatInboundPortProvider = chatInboundPortProvider;
        this.callbackFactory = Objects.requireNonNull(callbackFactory, "callbackFactory must not be null");
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.rateLimiterPort = Objects.requireNonNullElse(rateLimiterPort, RateLimiterPort.noop());
        this.sseTimeoutMs = sseTimeoutMs;
        this.chatRateLimitPermits = Math.max(1, chatRateLimitPermits);
        this.chatRateLimitWindow = Duration.ofMillis(Math.max(1L, chatRateLimitWindowMs));
    }

    @GetMapping(value = "/rag/v3/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false, defaultValue = DEFAULT_USER_ID) String userId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking,
                           @RequestParam(required = false) String chatMode) {
        String actualConversationId = resolveId(conversationId);
        String actualUserId = resolveUserId(userId);
        checkChatRateLimit(actualUserId);
        String taskId = nextShortId();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        StreamCallback callback = callbackFactory.create(emitter, actualConversationId, taskId, actualUserId);
        StreamChatCommand command = new StreamChatCommand(
                question,
                actualConversationId,
                taskId,
                actualUserId,
                Boolean.TRUE.equals(deepThinking),
                parseChatMode(chatMode));
                Boolean.TRUE.equals(deepThinking);
        try {
            chatInboundPortProvider.getIfAvailable().streamChat(command, callback);
        } catch (RuntimeException ex) {
            emitSseError(emitter, ex);
        }
        return emitter;
    }

    @PostMapping("/rag/v3/stop")
    public Map<String, Object> stop(@RequestParam String taskId) {
        chatInboundPortProvider.getIfAvailable().stopTask(taskId);
        streamTaskPort.unregister(taskId);
        return Map.of("code", "0");
    }

    private String resolveId(String value) {
        if (value == null || value.isBlank()) {
            return nextShortId();
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String nextShortId() {
        return IdUtil.nanoId(20);
    }

    private ChatMode parseChatMode(String value) {
        if (value == null || value.isBlank()) {
            return ChatMode.RAG;
        }
        try {
            return ChatMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChatMode.RAG;
        }
    }

    private String resolveUserId(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private void checkChatRateLimit(String userId) {
        RateLimitDecision decision = rateLimiterPort.tryAcquire(
                "chat", userId, chatRateLimitPermits, chatRateLimitWindow);
        if (!decision.allowed()) {
            throw new IllegalStateException("chat rate limit exceeded");
        }
    }

    private void emitSseError(SseEmitter emitter, RuntimeException ex) {
        try {
            emitter.send(SseEmitter.event()
                    .name(StreamEventType.REJECT.value())
                    .data(Map.of("type", "response", "delta", "")));
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()))));
            emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
            emitter.complete();
        } catch (IOException sendError) {
            emitter.complete();
        }
    }
}
