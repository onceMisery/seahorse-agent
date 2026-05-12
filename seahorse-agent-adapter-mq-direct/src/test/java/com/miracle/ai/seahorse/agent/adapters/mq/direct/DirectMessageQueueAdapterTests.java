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

package com.miracle.ai.seahorse.agent.adapters.mq.direct;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageQueueAdapterTests {

    @Test
    void shouldDispatchSubscribedMessagesInProcess() throws Exception {
        DirectMessageQueueAdapter adapter = new DirectMessageQueueAdapter();
        List<SamplePayload> received = new ArrayList<>();
        AutoCloseable subscription = adapter.subscribe("topic-a", "sub-a", SamplePayload.class, received::add);

        adapter.publishReliable("topic-a", "key-a", "biz", new SamplePayload("doc-1"));

        assertThat(received).containsExactly(new SamplePayload("doc-1"));
        subscription.close();
    }

    private record SamplePayload(String id) {
    }
}
