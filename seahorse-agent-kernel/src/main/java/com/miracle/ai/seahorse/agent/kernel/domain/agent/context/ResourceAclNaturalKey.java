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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import java.util.Objects;

public record ResourceAclNaturalKey(String tenantId,
                                    ResourceAclRuleScope scope,
                                    String resourceType,
                                    String resourceId,
                                    AccessSubjectType subjectType,
                                    String subjectId,
                                    ResourceAction action) {

    public ResourceAclNaturalKey {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        resourceType = requireText(resourceType, "resourceType must not be blank");
        resourceId = requireText(resourceId, "resourceId must not be blank");
        subjectType = Objects.requireNonNull(subjectType, "subjectType must not be null");
        subjectId = requireText(subjectId, "subjectId must not be blank");
        action = Objects.requireNonNull(action, "action must not be null");
    }

    public boolean matches(ResourceAclRule rule) {
        ResourceAclRule safeRule = Objects.requireNonNull(rule, "rule must not be null");
        return tenantId.equals(safeRule.tenantId())
                && scope == safeRule.scope()
                && resourceType.equals(safeRule.resourceType())
                && resourceId.equals(safeRule.resourceId())
                && subjectType == safeRule.subjectType()
                && subjectId.equals(safeRule.subjectId())
                && action == safeRule.action();
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
