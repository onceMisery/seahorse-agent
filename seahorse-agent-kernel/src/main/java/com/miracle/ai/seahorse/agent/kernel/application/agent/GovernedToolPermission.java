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

import java.util.Objects;

/**
 * Seahorse-owned tool governance result consumed by pluggable agent executors.
 */
public record GovernedToolPermission(
        Effect effect,
        String approvalId,
        String reasonCode,
        String reasonMessage) {

    public enum Effect {
        ALLOW,
        APPROVAL_REQUIRED,
        DENY
    }

    public GovernedToolPermission {
        effect = Objects.requireNonNullElse(effect, Effect.DENY);
        approvalId = trimToNull(approvalId);
        reasonCode = defaultText(reasonCode, effect.name());
        reasonMessage = defaultText(reasonMessage, reasonCode);
    }

    public static GovernedToolPermission allow(String reasonCode, String reasonMessage) {
        return new GovernedToolPermission(Effect.ALLOW, null, reasonCode, reasonMessage);
    }

    public static GovernedToolPermission approvalRequired(
            String approvalId,
            String reasonCode,
            String reasonMessage) {
        return new GovernedToolPermission(Effect.APPROVAL_REQUIRED, approvalId, reasonCode, reasonMessage);
    }

    public static GovernedToolPermission deny(String reasonCode, String reasonMessage) {
        return new GovernedToolPermission(Effect.DENY, null, reasonCode, reasonMessage);
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? Objects.requireNonNullElse(fallback, "") : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
