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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConditionalOnSeahorseAgentProperty.Container.class)
@Conditional(SeahorseAgentPropertyCondition.class)
public @interface ConditionalOnSeahorseAgentProperty {

    String prefix() default "";

    String name();

    String havingValue() default "";

    boolean matchIfMissing() default false;

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Conditional(SeahorseAgentPropertyCondition.class)
    @interface Container {

        ConditionalOnSeahorseAgentProperty[] value();
    }
}

final class SeahorseAgentPropertyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        List<ConditionalOnSeahorseAgentProperty> properties = new ArrayList<>(metadata.getAnnotations()
                .stream(ConditionalOnSeahorseAgentProperty.class)
                .map(MergedAnnotation::synthesize)
                .toList());
        metadata.getAnnotations()
                .stream(ConditionalOnSeahorseAgentProperty.Container.class)
                .map(MergedAnnotation::synthesize)
                .map(ConditionalOnSeahorseAgentProperty.Container::value)
                .flatMap(Arrays::stream)
                .forEach(properties::add);
        if (properties.isEmpty()) {
            return true;
        }
        Environment environment = context.getEnvironment();
        return properties.stream().allMatch(property -> matches(environment, property));
    }

    private static boolean matches(Environment environment, ConditionalOnSeahorseAgentProperty property) {
        String propertyName = propertyName(property.prefix(), property.name());
        String havingValue = property.havingValue();
        String value = environment.getProperty(propertyName);
        if (value == null) {
            value = environment.getProperty(legacyPropertyName(propertyName));
        }
        if (value == null) {
            return property.matchIfMissing();
        }
        if (havingValue == null || havingValue.isBlank()) {
            return !"false".equalsIgnoreCase(value);
        }
        return havingValue.equalsIgnoreCase(value);
    }

    private static String propertyName(String prefix, String name) {
        String trimmedPrefix = prefix == null ? "" : prefix.trim();
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedPrefix.isEmpty()) {
            return trimmedName;
        }
        return trimmedPrefix + "." + trimmedName;
    }

    private static String legacyPropertyName(String propertyName) {
        if (propertyName != null && propertyName.startsWith("seahorse-agent.")) {
            return "seahorse.agent." + propertyName.substring("seahorse-agent.".length());
        }
        return propertyName;
    }
}
