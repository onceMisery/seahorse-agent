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

import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSearchAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("seahorse-agent.chat.agent-mode-enabled=true")
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAutoConfiguration.class));

    @Test
    void shouldRegisterToolSearchByDefaultWhenAgentRuntimeIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InMemoryToolRegistry.class);
            assertThat(context).hasSingleBean(ToolSearchToolPortAdapter.class);
        });
    }

    @Test
    void shouldNotRegisterToolSearchWhenDeferredSearchIsDisabled() {
        contextRunner.withPropertyValues("seahorse-agent.chat.agent.tools.deferred-search.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InMemoryToolRegistry.class);
                    assertThat(context).doesNotHaveBean(ToolSearchToolPortAdapter.class);
                });
    }
}
