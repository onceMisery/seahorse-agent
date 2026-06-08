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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SpringSseEventSenderTests {

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
    void completeShouldSwallowFailureWhenEmitterAlreadyClosedByContainer() {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("AsyncContext is already closed")).when(emitter).complete();
        SpringSseEventSender sender = new SpringSseEventSender(emitter);

        assertThatCode(sender::complete).doesNotThrowAnyException();
    }
}
