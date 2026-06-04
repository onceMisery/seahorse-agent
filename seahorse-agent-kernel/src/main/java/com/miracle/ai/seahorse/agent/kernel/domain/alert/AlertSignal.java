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

package com.miracle.ai.seahorse.agent.kernel.domain.alert;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable alert signal emitted by the system when an anomalous condition is detected.
 *
 * @param ruleId    identifier of the rule that triggered this alert (may be {@code null} for ad-hoc alerts)
 * @param level     severity level
 * @param title     short human-readable title
 * @param message   detailed description
 * @param timestamp when the alert was generated
 * @param details   optional key-value pairs providing additional context
 */
public record AlertSignal(
        String ruleId,
        AlertLevel level,
        String title,
        String message,
        Instant timestamp,
        Map<String, String> details
) {

    /**
     * Convenience factory for ad-hoc alerts without a backing rule.
     */
    public static AlertSignal of(AlertLevel level, String title, String message) {
        return new AlertSignal(null, level, title, message, Instant.now(), Map.of());
    }

    /**
     * Convenience factory with details map.
     */
    public static AlertSignal of(AlertLevel level, String title, String message, Map<String, String> details) {
        return new AlertSignal(null, level, title, message, Instant.now(), details);
    }
}
