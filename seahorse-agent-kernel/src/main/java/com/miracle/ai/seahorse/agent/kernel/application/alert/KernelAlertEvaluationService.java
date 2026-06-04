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

package com.miracle.ai.seahorse.agent.kernel.application.alert;

import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertRule;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertSignal;
import com.miracle.ai.seahorse.agent.ports.outbound.alert.AlertNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application-level service that evaluates alert signals against registered rules
 * and dispatches matching alerts to all capable notifier ports.
 */
public class KernelAlertEvaluationService {

    private static final Logger LOG = LoggerFactory.getLogger(KernelAlertEvaluationService.class);

    private final List<AlertRule> rules = new CopyOnWriteArrayList<>();
    private final List<AlertNotifierPort> notifiers;

    public KernelAlertEvaluationService(List<AlertNotifierPort> notifiers) {
        this.notifiers = List.copyOf(Objects.requireNonNull(notifiers, "notifiers must not be null"));
    }

    public KernelAlertEvaluationService(List<AlertNotifierPort> notifiers, List<AlertRule> defaultRules) {
        this(notifiers);
        Objects.requireNonNull(defaultRules, "defaultRules must not be null").forEach(this::registerRule);
    }

    /**
     * Register a new alert rule. Duplicate rule IDs are allowed but only the first match wins
     * during evaluation.
     *
     * @param rule the rule to register
     */
    public void registerRule(AlertRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        rules.add(rule);
        LOG.debug("Registered alert rule: id={}, level={}", rule.id(), rule.level());
    }

    /**
     * Evaluate a batch of alert signals. Each signal is matched against enabled rules
     * and dispatched to all capable notifiers.
     *
     * @param signals the alert signals to evaluate
     */
    public void evaluate(List<AlertSignal> signals) {
        Objects.requireNonNull(signals, "signals must not be null");
        for (AlertSignal signal : signals) {
            fireAlert(signal);
        }
    }

    /**
     * Fire a single alert signal. If the signal's {@code ruleId} matches an enabled rule,
     * the alert is dispatched; otherwise it is dispatched unconditionally as an ad-hoc alert.
     *
     * @param signal the alert signal to fire
     */
    public void fireAlert(AlertSignal signal) {
        Objects.requireNonNull(signal, "signal must not be null");

        // If the signal carries a ruleId, verify that the rule is enabled
        if (signal.ruleId() != null) {
            boolean matched = rules.stream()
                    .anyMatch(r -> r.id().equals(signal.ruleId()) && r.enabled());
            if (!matched) {
                LOG.debug("Alert signal with ruleId={} did not match any enabled rule; skipping.", signal.ruleId());
                return;
            }
        }

        dispatch(signal);
    }

    private void dispatch(AlertSignal signal) {
        AlertLevel level = signal.level();
        boolean dispatched = false;
        for (AlertNotifierPort notifier : notifiers) {
            if (notifier.supports(level)) {
                try {
                    notifier.notify(signal);
                    dispatched = true;
                } catch (Exception e) {
                    LOG.warn("Notifier {} threw unexpectedly while dispatching alert [{}]: {}",
                            notifier.getClass().getSimpleName(), signal.title(), e.getMessage(), e);
                }
            }
        }
        if (!dispatched) {
            LOG.warn("No notifier could handle alert [level={}, title={}]", level, signal.title());
        }
    }

    /**
     * Returns an unmodifiable view of the currently registered rules.
     */
    public List<AlertRule> getRules() {
        return List.copyOf(rules);
    }
}
