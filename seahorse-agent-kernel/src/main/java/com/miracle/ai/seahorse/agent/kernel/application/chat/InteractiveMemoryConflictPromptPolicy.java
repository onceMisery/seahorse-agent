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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractiveMemoryConflictPromptPolicy {

    private static final int DEFAULT_SCAN_LIMIT = 20;
    private static final int DEFAULT_MAX_PROMPTS_PER_TURN = 3;
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_REPEAT_PROMPTS = 2;

    private final boolean enabled;
    private final int scanLimit;
    private final int maxPromptsPerTurn;
    private final Duration cooldown;
    private final int maxRepeatPrompts;
    private final Clock clock;
    private final Map<String, PromptState> states = new ConcurrentHashMap<>();

    public InteractiveMemoryConflictPromptPolicy(boolean enabled,
                                                 int scanLimit,
                                                 int maxPromptsPerTurn,
                                                 Duration cooldown,
                                                 int maxRepeatPrompts,
                                                 Clock clock) {
        this.enabled = enabled;
        this.scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
        this.maxPromptsPerTurn = maxPromptsPerTurn <= 0 ? DEFAULT_MAX_PROMPTS_PER_TURN : maxPromptsPerTurn;
        this.cooldown = Objects.requireNonNullElse(cooldown, DEFAULT_COOLDOWN);
        this.maxRepeatPrompts = Math.max(0, maxRepeatPrompts);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    public static InteractiveMemoryConflictPromptPolicy defaults() {
        return new InteractiveMemoryConflictPromptPolicy(
                true,
                DEFAULT_SCAN_LIMIT,
                DEFAULT_MAX_PROMPTS_PER_TURN,
                DEFAULT_COOLDOWN,
                DEFAULT_MAX_REPEAT_PROMPTS,
                Clock.systemUTC());
    }

    public boolean enabled() {
        return enabled;
    }

    public int scanLimit() {
        return scanLimit;
    }

    public int maxPromptsPerTurn() {
        return maxPromptsPerTurn;
    }

    public boolean shouldPrompt(String userId, String conflictId) {
        if (!enabled || maxRepeatPrompts <= 0 || isBlank(userId) || isBlank(conflictId)) {
            return false;
        }
        PromptState state = states.get(key(userId, conflictId));
        if (state == null) {
            return true;
        }
        if (state.promptCount() >= maxRepeatPrompts) {
            return false;
        }
        if (!cooldown.isZero() && !cooldown.isNegative()
                && state.lastPromptAt().plus(cooldown).isAfter(clock.instant())) {
            return false;
        }
        return true;
    }

    public void markPrompted(String userId, String conflictId) {
        if (isBlank(userId) || isBlank(conflictId)) {
            return;
        }
        Instant now = clock.instant();
        states.compute(key(userId, conflictId), (ignored, existing) -> {
            if (existing == null) {
                return new PromptState(1, now);
            }
            return new PromptState(existing.promptCount() + 1, now);
        });
    }

    private String key(String userId, String conflictId) {
        return userId.trim() + ":" + conflictId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PromptState(int promptCount, Instant lastPromptAt) {
    }
}
