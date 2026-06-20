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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class TurnBuffer implements StreamCallback {

    private final StringBuilder content = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private final CountDownLatch done = new CountDownLatch(1);
    private Throwable error;
    private volatile boolean completed;

    @Override
    public void onContent(String chunk) {
        if (chunk != null) {
            content.append(chunk);
        }
    }

    @Override
    public void onThinking(String chunk) {
        if (chunk != null) {
            thinking.append(chunk);
        }
    }

    @Override
    public void onComplete() {
        completed = true;
        done.countDown();
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
        done.countDown();
    }

    String content() {
        return content.toString();
    }

    String thinking() {
        return thinking.toString();
    }

    Throwable error() {
        return error;
    }

    boolean completed() {
        return completed;
    }

    void awaitCompletion(AgentRunControl control) {
        while (true) {
            control.checkCancelled();
            try {
                if (done.await(100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AgentLoopCancelledException("Agent loop cancelled", ex);
            }
        }
    }
}
