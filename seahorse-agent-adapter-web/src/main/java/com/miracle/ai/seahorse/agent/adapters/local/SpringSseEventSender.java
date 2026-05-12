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

import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local Spring SseEmitter implementation for Seahorse stream events.
 */
@Slf4j
public class SpringSseEventSender implements StreamEventSender {

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
            fail(ex);
        }
    }

    @Override
    public void complete() {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    @Override
    public void fail(Throwable error) {
        if (closed.compareAndSet(false, true)) {
            emitter.completeWithError(error);
        }
        log.warn("SSE send failed", error);
    }

    private void sendPayload(String eventName, Object payload) throws java.io.IOException {
        if (eventName == null) {
            emitter.send(payload);
            return;
        }
        emitter.send(SseEmitter.event().name(eventName).data(payload));
    }
}
