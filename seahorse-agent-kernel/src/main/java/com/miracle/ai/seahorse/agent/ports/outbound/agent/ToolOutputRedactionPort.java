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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;

import java.util.Objects;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ToolOutputRedactionPort {

    String REDACTED_VALUE = "[REDACTED]";
    Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9][A-Za-z0-9_-]*");

    ToolInvocationResult redact(ToolInvocationRequest request, ToolInvocationResult result);

    static ToolOutputRedactionPort noop() {
        return (request, result) -> result;
    }

    static ToolOutputRedactionPort basicSecretPatterns() {
        return (request, result) -> {
            if (result == null || !result.success() || result.content() == null) {
                return result;
            }
            String redacted = OPENAI_KEY_PATTERN.matcher(result.content()).replaceAll(REDACTED_VALUE);
            if (Objects.equals(redacted, result.content())) {
                return result;
            }
            return ToolInvocationResult.ok(redacted);
        };
    }
}
