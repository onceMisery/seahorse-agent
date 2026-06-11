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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.adapters.web.alert.DingTalkAlertNotifierAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.alert.KernelAlertEvaluationService;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertRule;
import com.miracle.ai.seahorse.agent.ports.outbound.alert.AlertNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot auto-configuration for the Seahorse Agent alert subsystem.
 *
 * <p>Activated by setting {@code seahorse.agent.observability.alert.enabled=true}.
 * Configures DingTalk webhook notifier and the kernel alert evaluation service
 * with a sensible set of default alert rules.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse.agent.observability.alert", name = "enabled", havingValue = "true")
public class SeahorseAgentAlertAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseAgentAlertAutoConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "seahorse.agent.observability.alert.dingtalk")
    public DingTalkAlertProperties seahorseAlertDingTalkProperties() {
        return new DingTalkAlertProperties();
    }

    @Bean
    @ConditionalOnMissingBean(AlertNotifierPort.class)
    public DingTalkAlertNotifierAdapter seahorseDingTalkAlertNotifier(DingTalkAlertProperties properties) {
        String webhook = properties.getWebhook();
        if (webhook == null || webhook.isBlank()) {
            LOG.info("DingTalk alert webhook not configured; alert notifications will be disabled.");
            return null;
        }
        LOG.info("DingTalk alert notifier configured with webhook: {}...{}",
                webhook.substring(0, Math.min(webhook.length(), 30)),
                webhook.length() > 30 ? "***" : "");
        return new DingTalkAlertNotifierAdapter(webhook, properties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean(KernelAlertEvaluationService.class)
    public KernelAlertEvaluationService seahorseAlertEvaluationService(
            ObjectProvider<AlertNotifierPort> notifierPorts) {
        List<AlertNotifierPort> notifiers = new ArrayList<>();
        notifierPorts.orderedStream().forEach(notifiers::add);
        if (notifiers.isEmpty()) {
            notifiers.add(AlertNotifierPort.noOp());
        }

        List<AlertRule> defaultRules = buildDefaultRules();
        LOG.info("Initializing KernelAlertEvaluationService with {} notifier(s) and {} default rule(s)",
                notifiers.size(), defaultRules.size());
        return new KernelAlertEvaluationService(notifiers, defaultRules);
    }

    private static List<AlertRule> buildDefaultRules() {
        return List.of(
                AlertRule.enabled(
                        "service-down",
                        "Service Down",
                        AlertLevel.CRITICAL,
                        "A critical service instance is unreachable or has stopped responding."
                ),
                AlertRule.enabled(
                        "api-error-rate-high",
                        "API Error Rate High",
                        AlertLevel.CRITICAL,
                        "API error rate has exceeded the 5% threshold."
                ),
                AlertRule.enabled(
                        "db-pool-exhausted",
                        "Database Pool Exhausted",
                        AlertLevel.WARNING,
                        "The database connection pool has reached maximum capacity."
                ),
                AlertRule.enabled(
                        "payment-callback-failed",
                        "Payment Callback Failed",
                        AlertLevel.CRITICAL,
                        "A payment gateway callback invocation has failed."
                ),
                AlertRule.enabled(
                        "quota-exhausted",
                        "Quota Exhausted",
                        AlertLevel.WARNING,
                        "A tenant or user has exhausted their allocated resource quota."
                ),
                AlertRule.enabled(
                        "jvm-memory-high",
                        "JVM Memory High",
                        AlertLevel.WARNING,
                        "JVM heap memory usage has exceeded 90% of the maximum."
                )
        );
    }

    /**
     * Configuration properties for DingTalk webhook alerting.
     */
    public static class DingTalkAlertProperties {

        private String webhook;
        private String secret;

        public String getWebhook() {
            return webhook;
        }

        public void setWebhook(String webhook) {
            this.webhook = webhook;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
