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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 本地流任务适配器契约测试。
 */
class LocalStreamTaskPortTests {

    @Test
    void shouldCancelRegisteredLocalTaskOnce() {
        LocalStreamTaskPort taskPort = new LocalStreamTaskPort();
        StreamEventSender sender = mock(StreamEventSender.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        StreamCompletionPayload payload = new StreamCompletionPayload("message-1", "title-1");

        taskPort.register("task-1", sender, () -> payload);
        taskPort.bindHandle("task-1", handle);
        taskPort.cancel("task-1");
        taskPort.cancel("task-1");

        Assertions.assertTrue(taskPort.isCancelled("task-1"));
        verify(handle).cancel();
        verify(sender).sendEvent(StreamEventType.CANCEL.value(), payload);
        verify(sender).sendEvent(StreamEventType.DONE.value(), "[DONE]");
        verify(sender).complete();
    }

    @Test
    void shouldImmediatelyCancelHandleBoundAfterCancellation() {
        LocalStreamTaskPort taskPort = new LocalStreamTaskPort();
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        taskPort.cancel("task-1");
        taskPort.bindHandle("task-1", handle);

        verify(handle).cancel();
    }

    @Test
    void shouldTreatBlankTaskIdAsNotCancelled() {
        LocalStreamTaskPort taskPort = new LocalStreamTaskPort();

        Assertions.assertFalse(taskPort.isCancelled(null));
        Assertions.assertFalse(taskPort.isCancelled(""));
    }
}
