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

/**
 * Declarative rule describing a class of alert conditions.
 *
 * @param id          unique rule identifier (e.g. {@code "service-down"})
 * @param name        human-readable rule name
 * @param level       severity assigned to alerts matching this rule
 * @param description explanation of what this rule detects
 * @param enabled     whether the rule is active
 */
public record AlertRule(
        String id,
        String name,
        AlertLevel level,
        String description,
        boolean enabled
) {

    /**
     * Factory that creates an enabled rule.
     */
    public static AlertRule enabled(String id, String name, AlertLevel level, String description) {
        return new AlertRule(id, name, level, description, true);
    }
}
