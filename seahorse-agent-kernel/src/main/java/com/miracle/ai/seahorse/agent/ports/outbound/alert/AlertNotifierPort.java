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

package com.miracle.ai.seahorse.agent.ports.outbound.alert;

import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertSignal;

/**
 * Outbound port for delivering alert notifications to external channels
 * (e.g. DingTalk, Slack, email, PagerDuty).
 *
 * <p>Implementations must never throw; delivery failures should be logged internally.
 */
public interface AlertNotifierPort {

    /**
     * Deliver the given alert signal to the external channel.
     * Implementations must swallow and log all exceptions internally.
     *
     * @param alert the alert signal to deliver
     */
    void notify(AlertSignal alert);

    /**
     * Whether this notifier is capable of handling alerts at the given severity level.
     *
     * @param level the alert level to check
     * @return {@code true} if this notifier supports the level
     */
    boolean supports(AlertLevel level);

    /**
     * A no-op notifier that silently discards all alerts.
     */
    static AlertNotifierPort noOp() {
        return new AlertNotifierPort() {
            @Override
            public void notify(AlertSignal alert) {
                // intentionally empty
            }

            @Override
            public boolean supports(AlertLevel level) {
                return false;
            }
        };
    }
}
