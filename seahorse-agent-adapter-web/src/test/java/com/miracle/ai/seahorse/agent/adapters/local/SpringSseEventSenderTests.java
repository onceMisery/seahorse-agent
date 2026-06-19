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

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SpringSseEventSenderTests {

    @Test
    void failShouldEmitErrorThenDoneBeforeCompleting() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        sender.fail(new IllegalStateException("stream failed"));

        assertThat(emitter.events).hasSize(2);
        assertThat(emitter.events.get(0)).contains("error").contains("stream failed");
        assertThat(emitter.events.get(1)).contains("done").contains("[DONE]");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void sendEventShouldSwallowCompleteFailureAfterEmitterError() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        RuntimeException completeFailure = new IllegalStateException(
                "AsyncContext is already closed after onError");
        doThrow(new IOException("client disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(completeFailure).when(emitter).complete();
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        assertThatCode(() -> sender.sendEvent("message", "payload")).doesNotThrowAnyException();
    }

    @Test
    void sendEventShouldTreatClientDisconnectAsNormalCompletion() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AsyncRequestNotUsableException(
                "ServletOutputStream failed to flush: java.io.IOException: Broken pipe"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        assertThatCode(() -> sender.sendEvent("message", "payload")).doesNotThrowAnyException();

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void failShouldNotEmitErrorPayloadForClientDisconnect() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        sender.fail(new AsyncRequestNotUsableException(
                "ServletOutputStream failed to flush: java.io.IOException: Broken pipe"));

        assertThat(emitter.events).isEmpty();
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void completeShouldSwallowFailureWhenEmitterAlreadyClosedByContainer() {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("AsyncContext is already closed")).when(emitter).complete();
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        assertThatCode(sender::complete).doesNotThrowAnyException();
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private final List<String> events = new java.util.ArrayList<>();
        private boolean completed;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            events.add(builder.build().stream()
                    .map(item -> String.valueOf(item.getData()))
                    .collect(Collectors.joining("\n")));
        }

        @Override
        public void complete() {
            completed = true;
        }
    }
}
