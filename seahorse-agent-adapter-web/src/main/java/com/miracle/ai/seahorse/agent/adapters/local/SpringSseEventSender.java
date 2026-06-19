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

import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local Spring SseEmitter implementation for Seahorse stream events.
 */
public class SpringSseEventSender implements StreamEventSender {

    private static final Logger log = LoggerFactory.getLogger(SpringSseEventSender.class);

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SpringSseEventSender(SseEmitter emitter) {
        this.emitter = Objects.requireNonNull(emitter, "SSE emitter must not be null");
        this.emitter.onCompletion(() -> closed.set(true));
        this.emitter.onTimeout(() -> closed.set(true));
        this.emitter.onError(error -> closed.set(true));
    }

    @Override
    public void sendEvent(String eventName, Object payload) {
        if (closed.get()) {
            return;
        }
        try {
            sendPayload(eventName, payload);
        } catch (Exception ex) {
            if (isClientDisconnect(ex)) {
                closeAfterClientDisconnect(ex);
                return;
            }
            fail(ex);
        }
    }

    @Override
    public void complete() {
        if (closed.compareAndSet(false, true)) {
            completeQuietly();
        }
    }

    @Override
    public void fail(Throwable error) {
        if (isClientDisconnect(error)) {
            closeAfterClientDisconnect(error);
            return;
        }
        if (closed.compareAndSet(false, true)) {
            sendError(error);
            completeQuietly();
        }
        log.warn("SSE send failed", error);
    }

    private void closeAfterClientDisconnect(Throwable error) {
        if (closed.compareAndSet(false, true)) {
            completeQuietly();
        }
        log.debug("SSE client disconnected before stream completed", error);
    }

    private void sendPayload(String eventName, Object payload) throws java.io.IOException {
        if (eventName == null) {
            emitter.send(payload);
            return;
        }
        emitter.send(SseEmitter.event().name(eventName).data(payload));
    }

    private void sendError(Throwable error) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", Objects.requireNonNullElse(error.getMessage(),
                            error.getClass().getSimpleName()))));
            emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
        } catch (Exception sendException) {
            log.debug("Failed to send SSE error payload", sendException);
        }
    }

    private void completeQuietly() {
        try {
            emitter.complete();
        } catch (Exception completeException) {
            log.debug("Failed to complete SSE emitter", completeException);
        }
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || normalized.contains("client disconnected")
                        || normalized.contains("client abort")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
