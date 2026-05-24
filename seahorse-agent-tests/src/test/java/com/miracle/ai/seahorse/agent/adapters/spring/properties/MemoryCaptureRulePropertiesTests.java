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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryCaptureRules;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 7 续：验证 {@link MemoryCaptureRuleProperties} 默认值与
 * {@link MemoryCaptureRules#defaults()} 严格对齐，并支持外部 key 覆盖。
 */
class MemoryCaptureRulePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHolderConfiguration.class);

    @Test
    void defaultsMatchKernelMemoryCaptureRulesDefaults() {
        contextRunner.run(context -> {
            MemoryCaptureRuleProperties properties = context.getBean(MemoryCaptureRuleProperties.class);
            MemoryCaptureRules rules = properties.toRules();
            MemoryCaptureRules expected = MemoryCaptureRules.defaults();

            assertThat(rules.minCandidateLength()).isEqualTo(expected.minCandidateLength());
            assertThat(rules.maxCandidateLength()).isEqualTo(expected.maxCandidateLength());
            assertThat(rules.profileStatementPrefixes())
                    .containsExactlyElementsOf(expected.profileStatementPrefixes());
            assertThat(rules.preferenceStatementPrefixes())
                    .containsExactlyElementsOf(expected.preferenceStatementPrefixes());
            assertThat(rules.questionEndingCharacters())
                    .containsExactlyElementsOf(expected.questionEndingCharacters());
            assertThat(rules.questionContainsKeywords())
                    .containsExactlyElementsOf(expected.questionContainsKeywords());
        });
    }

    @Test
    void customKeysOverrideDefaultsViaToRules() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.capture.min-candidate-length=3",
                        "seahorse-agent.memory.capture.max-candidate-length=200",
                        "seahorse-agent.memory.capture.profile-statement-prefixes[0]=I am",
                        "seahorse-agent.memory.capture.preference-statement-prefixes[0]=I prefer",
                        "seahorse-agent.memory.capture.question-ending-characters[0]=?",
                        "seahorse-agent.memory.capture.question-contains-keywords[0]=what is")
                .run(context -> {
                    MemoryCaptureRules rules = context.getBean(MemoryCaptureRuleProperties.class).toRules();
                    assertThat(rules.minCandidateLength()).isEqualTo(3);
                    assertThat(rules.maxCandidateLength()).isEqualTo(200);
                    assertThat(rules.profileStatementPrefixes()).containsExactly("I am");
                    assertThat(rules.preferenceStatementPrefixes()).containsExactly("I prefer");
                    assertThat(rules.questionEndingCharacters()).containsExactly("?");
                    assertThat(rules.questionContainsKeywords()).containsExactly("what is");
                });
    }

    @Test
    void invalidMinLengthRejectedByRulesConstructor() {
        MemoryCaptureRuleProperties properties = new MemoryCaptureRuleProperties();
        properties.setMinCandidateLength(0);
        try {
            properties.toRules();
            assertThat(false).as("expected IllegalArgumentException for non-positive min length").isTrue();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemoryCaptureRuleProperties.class)
    static class PropertiesHolderConfiguration {
    }
}
