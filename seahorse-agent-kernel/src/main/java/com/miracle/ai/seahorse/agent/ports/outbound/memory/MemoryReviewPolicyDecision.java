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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.util.Objects;

public record MemoryReviewPolicyDecision(Action action, String reason) {

    public enum Action {
        DROP,
        REVIEW,
        AUTO_COMMIT
    }

    public MemoryReviewPolicyDecision {
        action = Objects.requireNonNullElse(action, Action.AUTO_COMMIT);
        reason = Objects.requireNonNullElse(reason, "").trim();
    }

    public static MemoryReviewPolicyDecision drop(String reason) {
        return new MemoryReviewPolicyDecision(Action.DROP, reason);
    }

    public static MemoryReviewPolicyDecision review(String reason) {
        return new MemoryReviewPolicyDecision(Action.REVIEW, reason);
    }

    public static MemoryReviewPolicyDecision autoCommit() {
        return new MemoryReviewPolicyDecision(Action.AUTO_COMMIT, "");
    }
}
