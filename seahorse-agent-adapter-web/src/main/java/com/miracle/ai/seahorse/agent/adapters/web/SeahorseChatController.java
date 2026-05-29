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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生问答 Web 入站适配器。
 */
@RestController
public class SeahorseChatController {

    private static final EnumSet<TaskTemplateId> CONTROLLED_WEB_AGENT_TEMPLATES = EnumSet.of(
            TaskTemplateId.DEEP_RESEARCH,
            TaskTemplateId.WEB_SUMMARY,
            TaskTemplateId.COMPARE_ANALYSIS);
    private static final EnumSet<TaskTemplateId> HIGH_COST_TASK_TEMPLATES = EnumSet.of(
            TaskTemplateId.DEEP_RESEARCH,
            TaskTemplateId.COMPARE_ANALYSIS);
    private static final int TEMPLATE_PERMITS_PER_DAY = 50;
    private static final Duration TEMPLATE_WINDOW = Duration.ofDays(1);

    private final ObjectProvider<ChatInboundPort> chatInboundPortProvider;
    private final ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider;
    private final ObjectProvider<ResearchInboundPort> researchInboundPortProvider;
    private final ObjectProvider<ResearchSseBridge> researchSseBridgeProvider;
    private final ChatStreamCallbackFactoryPort callbackFactory;
    private final StreamTaskPort streamTaskPort;
    private final RateLimiterPort rateLimiterPort;
    private final AgentRunEventBufferPort eventBufferPort;
    private final AdvancedFeatureGate advancedFeatureGate;
    private final long sseTimeoutMs;
    private final int chatRateLimitPermits;
    private final Duration chatRateLimitWindow;

    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  long sseTimeoutMs) {
        this(chatInboundPortProvider, callbackFactory, streamTaskPort, sseTimeoutMs,
                AdvancedFeatureGate.consumerWebDefaults());
    }

    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  long sseTimeoutMs,
                                  AdvancedFeatureGate advancedFeatureGate) {
        this(chatInboundPortProvider, callbackFactory, streamTaskPort, null,
                null, null,
                RateLimiterPort.noop(), AgentRunEventBufferPort.noop(),
                advancedFeatureGate, sseTimeoutMs, 60, Duration.ofMinutes(1));
    }

    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  long sseTimeoutMs,
                                  ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider) {
        this(chatInboundPortProvider,
                callbackFactory,
                streamTaskPort,
                snapshotPortProvider,
                null,
                null,
                RateLimiterPort.noop(),
                AgentRunEventBufferPort.noop(),
                AdvancedFeatureGate.consumerWebDefaults(),
                sseTimeoutMs,
                60,
                Duration.ofMinutes(1));
    }

    @Autowired
    public SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                  ChatStreamCallbackFactoryPort callbackFactory,
                                  StreamTaskPort streamTaskPort,
                                  ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider,
                                  ObjectProvider<ResearchInboundPort> researchInboundPortProvider,
                                  ObjectProvider<ResearchSseBridge> researchSseBridgeProvider,
                                  ObjectProvider<RateLimiterPort> rateLimiterPortProvider,
                                  ObjectProvider<AgentRunEventBufferPort> eventBufferPortProvider,
                                  ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider,
                                  @Value("${seahorse-agent.web.sse-timeout-ms:300000}")
                                  long sseTimeoutMs,
                                  @Value("${seahorse-agent.web.chat-rate-limit.permits:60}") int chatRateLimitPermits,
                                  @Value("${seahorse-agent.web.chat-rate-limit.window-ms:60000}")
                                  long chatRateLimitWindowMs) {
        this(chatInboundPortProvider,
                callbackFactory,
                streamTaskPort,
                snapshotPortProvider,
                researchInboundPortProvider,
                researchSseBridgeProvider,
                Objects.requireNonNullElse(rateLimiterPortProvider.getIfAvailable(), RateLimiterPort.noop()),
                Objects.requireNonNullElse(eventBufferPortProvider.getIfAvailable(), AgentRunEventBufferPort.noop()),
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults),
                sseTimeoutMs,
                chatRateLimitPermits,
                Duration.ofMillis(Math.max(1L, chatRateLimitWindowMs)));
    }

    private SeahorseChatController(ObjectProvider<ChatInboundPort> chatInboundPortProvider,
                                   ChatStreamCallbackFactoryPort callbackFactory,
                                   StreamTaskPort streamTaskPort,
                                   ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider,
                                   ObjectProvider<ResearchInboundPort> researchInboundPortProvider,
                                   ObjectProvider<ResearchSseBridge> researchSseBridgeProvider,
                                   RateLimiterPort rateLimiterPort,
                                   AgentRunEventBufferPort eventBufferPort,
                                   AdvancedFeatureGate advancedFeatureGate,
                                   long sseTimeoutMs,
                                   int chatRateLimitPermits,
                                   Duration chatRateLimitWindow) {
        this.chatInboundPortProvider = chatInboundPortProvider;
        this.snapshotPortProvider = snapshotPortProvider;
        this.researchInboundPortProvider = researchInboundPortProvider;
        this.researchSseBridgeProvider = researchSseBridgeProvider;
        this.callbackFactory = Objects.requireNonNull(callbackFactory, "callbackFactory must not be null");
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.rateLimiterPort = Objects.requireNonNullElse(rateLimiterPort, RateLimiterPort.noop());
        this.eventBufferPort = Objects.requireNonNullElse(eventBufferPort, AgentRunEventBufferPort.noop());
        this.advancedFeatureGate = Objects.requireNonNullElseGet(
                advancedFeatureGate,
                AdvancedFeatureGate::consumerWebDefaults);
        this.sseTimeoutMs = sseTimeoutMs;
        this.chatRateLimitPermits = Math.max(1, chatRateLimitPermits);
        this.chatRateLimitWindow = Objects.requireNonNullElse(chatRateLimitWindow, Duration.ofMinutes(1));
    }

    @GetMapping(value = "/rag/v3/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter chat(@RequestParam(required = false) String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false) String userId,
                           @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                           String headerUserId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking,
                           @RequestParam(required = false) String chatMode,
                           @RequestParam(required = false) String agentId,
                           @RequestParam(required = false) String versionId,
                           @RequestParam(required = false) String taskTemplateId,
                           @RequestParam(required = false) List<String> attachmentIds,
                           @RequestParam(required = false) String resumeRunId,
                           @RequestParam(required = false) Long lastEventSeq) {
        String actualConversationId = resolveId(conversationId);
        String actualUserId = WebUserIdResolver.resolve(userId, headerUserId);
        if (hasText(resumeRunId)) {
            return resumeStream(resumeRunId, lastEventSeq, actualUserId);
        }
        checkChatRateLimit(actualUserId);
        checkTaskTemplateRateLimit(taskTemplateId);
        ChatMode resolvedChatMode = resolveChatMode(chatMode, taskTemplateId);
        requireChatModeAllowed(chatMode, resolvedChatMode, taskTemplateId, agentId, versionId);
        String taskId = nextShortId();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        if (isDeepResearchTemplate(taskTemplateId)) {
            return dispatchResearch(emitter, question, actualConversationId, actualUserId, taskId, taskTemplateId);
        }
        StreamCallback callback = callbackFactory.create(emitter, actualConversationId, taskId, actualUserId);
        StreamChatCommand command = new StreamChatCommand(
                question,
                actualConversationId,
                taskId,
                actualUserId,
                Boolean.TRUE.equals(deepThinking),
                resolvedChatMode,
                agentId,
                versionId,
                taskTemplateId,
                attachmentIds);
        try {
            ChatInboundPort chatInboundPort = chatInboundPortProvider.getIfAvailable();
            if (chatInboundPort == null) {
                throw new IllegalStateException("ChatInboundPort is not configured");
            }
            chatInboundPort.streamChat(command, callback);
        } catch (RuntimeException ex) {
            emitSseError(emitter, ex);
        }
        return emitter;
    }

    private boolean isDeepResearchTemplate(String taskTemplateId) {
        if (!hasText(taskTemplateId)) {
            return false;
        }
        try {
            return TaskTemplateId.fromValue(taskTemplateId.trim()) == TaskTemplateId.DEEP_RESEARCH;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private SseEmitter dispatchResearch(SseEmitter emitter,
                                        String question,
                                        String conversationId,
                                        String userId,
                                        String taskId,
                                        String taskTemplateId) {
        if (!hasText(question)) {
            emitSseError(emitter, new IllegalArgumentException("question must not be blank"));
            return emitter;
        }
        ResearchInboundPort researchInboundPort = researchInboundPortProvider == null
                ? null : researchInboundPortProvider.getIfAvailable();
        ResearchSseBridge bridge = researchSseBridgeProvider == null
                ? null : researchSseBridgeProvider.getIfAvailable();
        if (researchInboundPort == null || bridge == null) {
            emitSseError(emitter, new IllegalStateException("Research pipeline is not configured"));
            return emitter;
        }
        try {
            String runId = nextShortId();
            researchInboundPort.startResearch(new ResearchStartCommand(
                    runId, question, conversationId, userId, null, taskTemplateId));
            bridge.attach(emitter, runId, conversationId, taskId);
        } catch (RuntimeException ex) {
            emitSseError(emitter, ex);
        }
        return emitter;
    }

    private SseEmitter resumeStream(String resumeRunId, Long lastEventSeq, String actualUserId) {
        checkChatRateLimit(actualUserId);
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        try {
            AgentRunSnapshotInboundPort snapshotPort = snapshotPort();
            var snapshot = snapshotPort.getSnapshot(resumeRunId);
            if (lastEventSeq != null) {
                List<StreamEventEnvelope> missed = eventBufferPort.getAfter(resumeRunId, lastEventSeq);
                if (!missed.isEmpty()) {
                    long cursor = lastEventSeq;
                    for (StreamEventEnvelope envelope : missed) {
                        emitter.send(SseEmitter.event()
                                .name("stream_event")
                                .data(envelope));
                        cursor = Math.max(cursor, envelope.eventSeq());
                        if (envelope.eventType() == StreamEventType.FINISH) {
                            emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
                            emitter.complete();
                            return emitter;
                        }
                    }
                    if (attachResumeStream(emitter, resumeRunId, cursor)) {
                        return emitter;
                    }
                    emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
                    emitter.complete();
                    return emitter;
                }
            }
            emitter.send(SseEmitter.event()
                    .name(StreamEventType.RUN_SNAPSHOT.value())
                    .data(snapshot));
            if (snapshot.canResume() && attachResumeStream(emitter, resumeRunId, snapshot.lastEventSeq())) {
                return emitter;
            }
            emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
            emitter.complete();
        } catch (RuntimeException | IOException ex) {
            emitSseError(emitter, ex instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException(ex.getMessage(), ex));
        }
        return emitter;
    }

    private boolean attachResumeStream(SseEmitter emitter, String runId, long afterSeq) {
        ResearchSseBridge bridge = researchSseBridgeProvider == null
                ? null : researchSseBridgeProvider.getIfAvailable();
        if (bridge == null) {
            return false;
        }
        bridge.attach(emitter, runId, null, null, afterSeq);
        return true;
    }

    private AgentRunSnapshotInboundPort snapshotPort() {
        AgentRunSnapshotInboundPort port = snapshotPortProvider == null ? null : snapshotPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("AgentRunSnapshotInboundPort is not configured");
        }
        return port;
    }

    @PostMapping("/rag/v3/stop")
    public Map<String, Object> stop(@RequestParam String taskId) {
        ChatInboundPort chatInboundPort = chatInboundPortProvider.getIfAvailable();
        if (chatInboundPort != null) {
            chatInboundPort.stopTask(taskId);
        }
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

    private ChatMode resolveChatMode(String requestedChatMode, String taskTemplateId) {
        if (hasText(requestedChatMode)) {
            return parseChatMode(requestedChatMode);
        }
        return isControlledWebAgentTemplate(taskTemplateId) ? ChatMode.AGENT : ChatMode.RAG;
    }

    private void requireChatModeAllowed(String requestedChatMode,
                                        ChatMode resolvedChatMode,
                                        String taskTemplateId,
                                        String agentId,
                                        String versionId) {
        if (resolvedChatMode != ChatMode.AGENT) {
            return;
        }
        boolean controlledWebTask = isControlledWebAgentTemplate(taskTemplateId)
                && !hasText(agentId)
                && !hasText(versionId);
        if (!controlledWebTask) {
            advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        }
    }

    private boolean isControlledWebAgentTemplate(String taskTemplateId) {
        if (!hasText(taskTemplateId)) {
            return false;
        }
        try {
            return CONTROLLED_WEB_AGENT_TEMPLATES.contains(TaskTemplateId.fromValue(taskTemplateId.trim()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void checkChatRateLimit(String userId) {
        RateLimitDecision decision = rateLimiterPort.tryAcquire(
                "user", userId, chatRateLimitPermits, chatRateLimitWindow);
        if (!decision.allowed()) {
            throw new IllegalStateException("chat rate limit exceeded");
        }
    }

    private void checkTaskTemplateRateLimit(String taskTemplateId) {
        if (!isHighCostTaskTemplate(taskTemplateId)) {
            return;
        }
        String normalized = TaskTemplateId.fromValue(taskTemplateId.trim()).value();
        RateLimitDecision decision = rateLimiterPort.tryAcquire(
                "template", normalized, TEMPLATE_PERMITS_PER_DAY, TEMPLATE_WINDOW);
        if (!decision.allowed()) {
            throw new IllegalStateException("task template rate limit exceeded");
        }
    }

    private boolean isHighCostTaskTemplate(String taskTemplateId) {
        if (!hasText(taskTemplateId)) {
            return false;
        }
        try {
            return HIGH_COST_TASK_TEMPLATES.contains(TaskTemplateId.fromValue(taskTemplateId.trim()));
        } catch (IllegalArgumentException ex) {
            return false;
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
