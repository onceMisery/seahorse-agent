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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Configuration(proxyBeanMethods = false)
public class SeahorseAgentPropertyAliasConfiguration {

    static final String CANONICAL_PREFIX = "seahorse-agent.";
    static final String LEGACY_PREFIX = "seahorse.agent.";
    private static final String PROPERTY_SOURCE_NAME = "seahorseAgentPropertyAliases";

    @Bean
    static BeanFactoryPostProcessor seahorseAgentPropertyAliasPostProcessor(
            ConfigurableEnvironment environment) {
        return beanFactory -> addAliases(environment);
    }

    static void addAliases(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        Set<String> suffixes = discoverSuffixes(environment);
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (String suffix : suffixes) {
            String canonicalName = CANONICAL_PREFIX + suffix;
            String legacyName = LEGACY_PREFIX + suffix;
            Object value = findProperty(environment, canonicalName);
            if (value == null) {
                value = findProperty(environment, legacyName);
            }
            if (value != null) {
                aliases.put(canonicalName, value);
                aliases.put(legacyName, value);
            }
        }
        if (!aliases.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, aliases));
        }
    }

    private static Set<String> discoverSuffixes(ConfigurableEnvironment environment) {
        Set<String> suffixes = new LinkedHashSet<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                if (propertyName.startsWith(CANONICAL_PREFIX)) {
                    suffixes.add(propertyName.substring(CANONICAL_PREFIX.length()));
                } else if (propertyName.startsWith(LEGACY_PREFIX)) {
                    suffixes.add(propertyName.substring(LEGACY_PREFIX.length()));
                }
            }
        }
        return suffixes;
    }

    private static Object findProperty(ConfigurableEnvironment environment, String propertyName) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            Object value = propertySource.getProperty(propertyName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
