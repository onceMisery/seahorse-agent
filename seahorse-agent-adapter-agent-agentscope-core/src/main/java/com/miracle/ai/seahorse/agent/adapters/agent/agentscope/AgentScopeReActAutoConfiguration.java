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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.Arrays;

/**
 * Compatibility aggregate for the split AgentScope auto-configurations.
 */
@AutoConfiguration
@Import({
        AgentScopeObservationAutoConfiguration.class,
        AgentScopeCoreAutoConfiguration.class,
        AgentScopeOptionalAutoConfigurationImportSelector.class
})
public class AgentScopeReActAutoConfiguration {
}

final class AgentScopeOptionalAutoConfigurationImportSelector implements DeferredImportSelector {

    private static final String[] OPTIONAL_AUTO_CONFIGURATIONS = {
            "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeNacosAutoConfiguration",
            "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeA2aAutoConfiguration",
            "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeConfigCenterAutoConfiguration",
            "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeStudioAutoConfiguration"
    };

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        ClassLoader classLoader = AgentScopeOptionalAutoConfigurationImportSelector.class.getClassLoader();
        return Arrays.stream(OPTIONAL_AUTO_CONFIGURATIONS)
                .filter(className -> ClassUtils.isPresent(className, classLoader))
                .toArray(String[]::new);
    }
}
